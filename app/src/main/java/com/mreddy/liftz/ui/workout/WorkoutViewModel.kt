package com.mreddy.liftz.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.DailyLogEntity
import com.mreddy.liftz.data.db.ExerciseWithPlan
import com.mreddy.liftz.data.db.GoalsEntity
import com.mreddy.liftz.data.db.IncrementsEntity
import com.mreddy.liftz.data.db.ProgressionSuggestionEntity
import com.mreddy.liftz.data.db.QueueState
import com.mreddy.liftz.data.db.SessionWithSets
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.DayCompletion
import com.mreddy.liftz.domain.TimeEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One row of the Spotify-style queue. */
data class QueueRow(
    val plan: ExerciseWithPlan,
    val state: QueueState,
    val setsDone: Int,
    val setsPlanned: Int,
    val estimatedSeconds: Int
) {
    val progress: Float get() = if (setsPlanned == 0) 0f else setsDone.toFloat() / setsPlanned
}

data class WorkoutUiState(
    val date: LocalDate = LocalDate.now(),
    val isWorkoutDay: Boolean = false,
    val rows: List<QueueRow> = emptyList(),
    val dailyLog: DailyLogEntity? = null,
    val goals: GoalsEntity = GoalsEntity(),
    val increments: IncrementsEntity = IncrementsEntity(),
    val completion: DayCompletion.Result? = null,
    val suggestions: List<ProgressionSuggestionEntity> = emptyList(),
    val estimatedRemainingSeconds: Int = 0
) {
    val setsDone: Int get() = rows.sumOf { it.setsDone }
    val setsPlanned: Int get() = rows.sumOf { it.setsPlanned }
    val workoutProgress: Float get() = if (setsPlanned == 0) 0f else setsDone.toFloat() / setsPlanned
    val remainingLabel: String get() = TimeEstimator.format(estimatedRemainingSeconds)
}

class WorkoutViewModel(
    private val repo: LiftzRepository,
    private val date: LocalDate
) : ViewModel() {

    /** Time estimates are a suspend query, so they are loaded once and folded into the state. */
    private val estimates = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        viewModelScope.launch {
            repo.ensureDailyLog(date)
            estimates.value = repo.estimatesForDate(date)
        }
    }

    val state: StateFlow<WorkoutUiState> = combine(
        repo.observeExercisesForDate(date),
        repo.observeSessionsForDate(date),
        repo.observeDailyLog(date),
        repo.observeGoals(),
        repo.observeIncrements(),
        repo.observePendingSuggestions(),
        estimates
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val exercises = values[0] as List<ExerciseWithPlan>
        @Suppress("UNCHECKED_CAST")
        val sessions = values[1] as List<SessionWithSets>
        val log = values[2] as DailyLogEntity?
        val goals = values[3] as GoalsEntity
        val increments = values[4] as IncrementsEntity
        @Suppress("UNCHECKED_CAST")
        val suggestions = values[5] as List<ProgressionSuggestionEntity>
        @Suppress("UNCHECKED_CAST")
        val est = values[6] as Map<String, Int>

        val sessionsById = sessions.associateBy { it.session.exerciseId }
        val rows = exercises.map { plan ->
            val session = sessionsById[plan.exercise.id]
            val setsPlanned = plan.orderedPlannedSets.size.takeIf { it > 0 } ?: plan.exercise.plannedSets
            val setsDone = session?.sets?.size ?: 0
            val state = when {
                session?.session?.completed == true -> QueueState.COMPLETED
                setsDone > 0 -> QueueState.IN_PROGRESS
                else -> QueueState.UPCOMING
            }
            QueueRow(
                plan = plan,
                state = state,
                setsDone = setsDone,
                setsPlanned = setsPlanned,
                estimatedSeconds = est[plan.exercise.id]
                    ?: (setsPlanned * (plan.exercise.restSecondsPerSet + 40))
            )
        }

        val inProgressIndex = rows.indexOfFirst { it.state == QueueState.IN_PROGRESS }
            .takeIf { it >= 0 }
            ?: rows.indexOfFirst { it.state == QueueState.UPCOMING }.takeIf { it >= 0 }

        val remaining = TimeEstimator.estimateRemainingSeconds(
            perExerciseEstimate = rows.map { it.estimatedSeconds },
            inProgressIndex = inProgressIndex,
            inProgressSetsDone = inProgressIndex?.let { rows[it].setsDone } ?: 0,
            inProgressSetsPlanned = inProgressIndex?.let { rows[it].setsPlanned } ?: 1
        )

        val isWorkoutDay = log?.isWorkoutDay ?: rows.isNotEmpty()
        val completion = DayCompletion.of(
            DayCompletion.Progress(
                waterMl = log?.waterMl ?: 0,
                proteinG = log?.proteinG ?: 0,
                carbsG = log?.carbsG ?: 0,
                fatG = log?.fatG ?: 0,
                calories = repo.caloriesFor(log, goals),
                isWorkoutDay = isWorkoutDay,
                workoutCompleted = log?.workoutCompleted ?: false,
                autoCalcCalories = goals.autoCalcCalories
            ),
            DayCompletion.Goals(
                goals.waterMl, goals.proteinG, goals.carbsG, goals.fatG, goals.calories
            )
        )

        WorkoutUiState(
            date = date,
            isWorkoutDay = isWorkoutDay,
            rows = rows,
            dailyLog = log,
            goals = goals,
            increments = increments,
            completion = completion,
            suggestions = suggestions.filter { s -> rows.any { it.plan.exercise.id == s.exerciseId } },
            estimatedRemainingSeconds = remaining
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState(date = date))

    fun adjustMacro(macro: LiftzRepository.Macro, delta: Int) = viewModelScope.launch {
        repo.adjustMacro(date, macro, delta)
    }

    fun setMacro(macro: LiftzRepository.Macro, value: Int) = viewModelScope.launch {
        repo.setMacro(date, macro, value)
    }

    fun acceptSuggestion(s: ProgressionSuggestionEntity) = viewModelScope.launch {
        repo.acceptSuggestion(s)
    }

    fun dismissSuggestion(s: ProgressionSuggestionEntity) = viewModelScope.launch {
        repo.dismissSuggestion(s)
    }
}

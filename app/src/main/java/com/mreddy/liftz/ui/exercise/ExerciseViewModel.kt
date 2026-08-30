package com.mreddy.liftz.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.LevelEntity
import com.mreddy.liftz.data.db.PlannedSetEntity
import com.mreddy.liftz.data.db.ProgressionSuggestionEntity
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.ProgressionEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One row in the set logging list. */
data class SetRowState(
    val plannedSet: PlannedSetEntity,
    /** Current value in the rep stepper. Pre-filled per the FIXED_REP / TO_FAILURE rules. */
    val reps: Int,
    val logged: Boolean,
    /** Only shown for TO_FAILURE sets: the number to beat. */
    val targetToBeat: Int?
)

data class ExerciseUiState(
    val loading: Boolean = true,
    val exerciseName: String = "",
    val formDescription: String = "",
    val notes: String = "",
    val levels: List<LevelEntity> = emptyList(),
    val currentLevelKey: String? = null,
    val currentWeightKg: Double? = null,
    val weightIncrementKg: Double? = null,
    val personalRecord: Int? = null,
    val lastSessionTopReps: Int? = null,
    val hypertrophyMin: Int = 8,
    val hypertrophyMax: Int = 12,
    val rollingWindow: Int = 6,
    val progressionNote: String = "",
    val rows: List<SetRowState> = emptyList(),
    /** 0f..1f, drives the pie-chart ring. */
    val ringProgress: Float = 0f,
    val allSetsDone: Boolean = false,
    val celebrate: Boolean = false,
    /** Cumulative rest for the whole exercise, seconds. */
    val restTotalSeconds: Int = 0,
    val restRemainingSeconds: Int = 0,
    val restRunning: Boolean = false,
    val pendingSuggestion: ProgressionSuggestionEntity? = null
) {
    val setsLogged: Int get() = rows.count { it.logged }
}

class ExerciseViewModel(
    private val repo: LiftzRepository,
    private val exerciseId: String,
    private val date: LocalDate
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseUiState())
    val state: StateFlow<ExerciseUiState> = _state.asStateFlow()

    private var sessionId: Long = 0
    private var restJob: Job? = null
    /** Seconds actually spent resting; written to the session on completion. */
    private var restElapsedSeconds: Int = 0

    init { load() }

    private fun load() = viewModelScope.launch {
        sessionId = repo.startSession(exerciseId, date)
        val ctx = repo.exerciseContext(exerciseId) ?: return@launch
        val e = ctx.plan.exercise
        val alreadyLogged = repo.exerciseContextLoggedReps(exerciseId, date)

        val rows = ctx.plan.orderedPlannedSets.mapIndexed { index, ps ->
            val logged = alreadyLogged[index]
            SetRowState(
                plannedSet = ps,
                reps = logged ?: ctx.defaultRepsPerSet.getOrElse(index) { e.hypertrophyMin },
                logged = logged != null,
                targetToBeat = if (ps.setType == SetType.TO_FAILURE)
                    ctx.defaultRepsPerSet.getOrNull(index) else null
            )
        }

        _state.update {
            it.copy(
                loading = false,
                exerciseName = e.name,
                formDescription = e.formDescription,
                notes = e.notes,
                levels = ctx.plan.orderedLevels,
                currentLevelKey = e.currentLevelKey,
                currentWeightKg = e.currentWeightKg,
                weightIncrementKg = e.weightIncrementKg,
                personalRecord = ctx.personalRecord,
                lastSessionTopReps = ctx.lastSessionTopReps,
                hypertrophyMin = e.hypertrophyMin,
                hypertrophyMax = e.hypertrophyMax,
                rollingWindow = e.rollingWindow,
                progressionNote = describe(ctx.outcome),
                rows = rows,
                ringProgress = if (rows.isEmpty()) 0f
                else rows.count { r -> r.logged }.toFloat() / rows.size,
                allSetsDone = rows.isNotEmpty() && rows.all { r -> r.logged },
                restTotalSeconds = ctx.plannedRestSeconds,
                restRemainingSeconds = ctx.plannedRestSeconds
            )
        }
        // Any set already logged means the rest clock should be running.
        if (rows.any { it.logged } && !_state.value.allSetsDone) startRestTimer()
    }

    private fun describe(outcome: ProgressionEngine.Outcome): String = when (outcome) {
        is ProgressionEngine.Outcome.Hold ->
            if (outcome.needed > 0)
                "${outcome.qualifyingStreak}/${outcome.needed} qualifying sessions. ${outcome.reason}"
            else outcome.reason
        is ProgressionEngine.Outcome.AdvanceLevel -> outcome.rationale
        is ProgressionEngine.Outcome.AddWeight -> outcome.rationale
        is ProgressionEngine.Outcome.TopOfLadder -> "Top of the ladder. Add weight or slow the tempo."
    }

    /* ---------------------------- rep stepper ---------------------------- */

    /** Rep increment is fixed at 1 by design and is not user configurable. */
    fun bumpReps(setIndex: Int, delta: Int) {
        _state.update { s ->
            s.copy(rows = s.rows.mapIndexed { i, row ->
                if (i == setIndex) row.copy(reps = (row.reps + delta).coerceAtLeast(0)) else row
            })
        }
    }

    fun setReps(setIndex: Int, value: Int) {
        _state.update { s ->
            s.copy(rows = s.rows.mapIndexed { i, row ->
                if (i == setIndex) row.copy(reps = value.coerceAtLeast(0)) else row
            })
        }
    }

    /* ---------------------------- logging ---------------------------- */

    fun completeSet(setIndex: Int) = viewModelScope.launch {
        val row = _state.value.rows.getOrNull(setIndex) ?: return@launch
        repo.logSet(
            sessionId = sessionId,
            setIndex = setIndex,
            reps = row.reps,
            weightKg = _state.value.currentWeightKg,
            setType = row.plannedSet.setType,
            // Record the rung this set was actually done at, not just the exercise's current one:
            // pull-up's unassisted sets override to "standard" while the rest are band assisted.
            levelKey = row.plannedSet.levelKeyOverride ?: _state.value.currentLevelKey
        )
        val rows = _state.value.rows.mapIndexed { i, r ->
            if (i == setIndex) r.copy(logged = true) else r
        }
        val done = rows.isNotEmpty() && rows.all { it.logged }
        _state.update {
            it.copy(
                rows = rows,
                ringProgress = rows.count { r -> r.logged }.toFloat() / rows.size,
                allSetsDone = done
            )
        }

        if (done) {
            stopRestTimer()
            // Full ring on the last set: gold flash + confetti + haptic, then auto-return.
            _state.update { it.copy(celebrate = true) }
            val suggestion = repo.completeSession(exerciseId, date, restElapsedSeconds)
            _state.update { it.copy(pendingSuggestion = suggestion) }
        } else {
            startRestTimer()
        }
    }

    fun undoSet(setIndex: Int) = viewModelScope.launch {
        repo.undoSet(sessionId, setIndex)
        val rows = _state.value.rows.mapIndexed { i, r ->
            if (i == setIndex) r.copy(logged = false) else r
        }
        _state.update {
            it.copy(
                rows = rows,
                ringProgress = rows.count { r -> r.logged }.toFloat() / rows.size,
                allSetsDone = false,
                celebrate = false
            )
        }
    }

    fun celebrationShown() { _state.update { it.copy(celebrate = false) } }

    /* ---------------------------- rest timer ----------------------------
     * ONE cumulative clock for the whole exercise: total = plannedSets * restSecondsPerSet.
     * It runs between sets and pauses while a set is being performed, so what is left tells you
     * how much rest budget the exercise has remaining, not how long until the next set.
     */

    fun startRestTimer() {
        if (_state.value.restRunning) return
        _state.update { it.copy(restRunning = true) }
        restJob = viewModelScope.launch {
            while (_state.value.restRemainingSeconds > 0 && _state.value.restRunning) {
                delay(1_000)
                restElapsedSeconds++
                _state.update { it.copy(restRemainingSeconds = (it.restRemainingSeconds - 1).coerceAtLeast(0)) }
            }
            _state.update { it.copy(restRunning = false) }
        }
    }

    fun stopRestTimer() {
        restJob?.cancel()
        restJob = null
        _state.update { it.copy(restRunning = false) }
    }

    fun toggleRestTimer() = if (_state.value.restRunning) stopRestTimer() else startRestTimer()

    fun resetRestTimer() {
        stopRestTimer()
        restElapsedSeconds = 0
        _state.update { it.copy(restRemainingSeconds = it.restTotalSeconds) }
    }

    /* ---------------------------- manual progression ---------------------------- */

    /**
     * User picked a level by hand. Any rung is allowed, including going DOWN after missed
     * workouts. The comparison target follows automatically because history is filtered by level.
     */
    fun selectLevel(levelKey: String) = viewModelScope.launch {
        repo.setLevelManually(exerciseId, levelKey)
        load()
    }

    fun adjustWeight(delta: Double) = viewModelScope.launch {
        val current = _state.value.currentWeightKg ?: return@launch
        repo.setWeightManually(exerciseId, (current + delta).coerceAtLeast(0.0))
        load()
    }

    fun acceptSuggestion() = viewModelScope.launch {
        _state.value.pendingSuggestion?.let { repo.acceptSuggestion(it) }
        _state.update { it.copy(pendingSuggestion = null) }
    }

    fun dismissSuggestion() = viewModelScope.launch {
        _state.value.pendingSuggestion?.let { repo.dismissSuggestion(it) }
        _state.update { it.copy(pendingSuggestion = null) }
    }

    override fun onCleared() {
        super.onCleared()
        restJob?.cancel()
    }
}

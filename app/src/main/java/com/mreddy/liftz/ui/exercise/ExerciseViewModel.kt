package com.mreddy.liftz.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.LevelEntity
import com.mreddy.liftz.data.db.PlannedSetEntity
import com.mreddy.liftz.data.db.ProgressionSuggestionEntity
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.ProgressionEngine
import com.mreddy.liftz.domain.SetTiming
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
    val targetToBeat: Int?,
    /** How long this set took, once it has been timed and logged. 0 = not timed. */
    val durationMs: Long = 0
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
    val primaryMuscle: com.mreddy.liftz.domain.MuscleGroup? = null,
    val secondaryMuscles: List<com.mreddy.liftz.domain.MuscleGroup> = emptyList(),
    val rows: List<SetRowState> = emptyList(),
    /** 0f..1f, drives the pie-chart ring. */
    val ringProgress: Float = 0f,
    val allSetsDone: Boolean = false,
    val celebrate: Boolean = false,
    /* ---- stopwatches ----
     * Two clocks running at once, both counting UP. The exercise clock starts when the screen is
     * first opened and never pauses; the set clock runs only while a set is being performed.
     * Rest is the difference, which is why it does not need a clock of its own — it is measured,
     * not budgeted, and that is the whole point of replacing the old countdown.
     */
    val exerciseElapsedMs: Long = 0,
    /** Which set's stopwatch is currently running, or null between sets. */
    val runningSetIndex: Int? = null,
    val setElapsedMs: Long = 0,
    /** Time since the last set ended. Only meaningful when no set is running. */
    val restElapsedMs: Long = 0,
    val pendingSuggestion: ProgressionSuggestionEntity? = null
) {
    val setsLogged: Int get() = rows.count { it.logged }
    val isResting: Boolean get() = runningSetIndex == null && setsLogged > 0 && !allSetsDone
    /** Total time spent working so far, for the live work/rest split. */
    val workedMs: Long get() = rows.sumOf { it.durationMs } + if (runningSetIndex != null) setElapsedMs else 0L
}

class ExerciseViewModel(
    private val repo: LiftzRepository,
    private val exerciseId: String,
    private val date: LocalDate
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseUiState())
    val state: StateFlow<ExerciseUiState> = _state.asStateFlow()

    private var sessionId: Long = 0
    private var tickJob: Job? = null
    /** Wall-clock start of the whole exercise, from the session row so it survives leaving. */
    private var exerciseStartedAtMs: Long = 0
    /** Wall-clock start of the set currently being performed. */
    private var currentSetStartedAtMs: Long = 0
    /** When the last set ended, so rest can be measured from it. */
    private var lastSetEndedAtMs: Long = 0

    init { load() }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    private fun load() = viewModelScope.launch {
        sessionId = repo.startSession(exerciseId, date)
        val ctx = repo.exerciseContext(exerciseId) ?: return@launch
        val e = ctx.plan.exercise
        val alreadyLogged = repo.exerciseContextLoggedReps(exerciseId, date)

        exerciseStartedAtMs = repo.sessionStartedAtMs(exerciseId, date)
            .takeIf { it > 0 } ?: System.currentTimeMillis()
        // Durations of sets already logged, so backing out and returning does not lose them.
        val timings = repo.sessionTimings(exerciseId, date).associateBy { it.setIndex }
        lastSetEndedAtMs = timings.values.filter { it.isTimed }.maxOfOrNull { it.endedAtMs } ?: 0L

        val rows = ctx.plan.orderedPlannedSets.mapIndexed { index, ps ->
            val logged = alreadyLogged[index]
            SetRowState(
                plannedSet = ps,
                reps = logged ?: ctx.defaultRepsPerSet.getOrElse(index) { e.hypertrophyMin },
                logged = logged != null,
                targetToBeat = if (ps.setType == SetType.TO_FAILURE)
                    ctx.defaultRepsPerSet.getOrNull(index) else null,
                durationMs = timings[index]?.durationMs ?: 0L
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
                primaryMuscle = e.primaryMuscle,
                secondaryMuscles = e.secondaryMuscles,
                rows = rows,
                ringProgress = if (rows.isEmpty()) 0f
                else rows.count { r -> r.logged }.toFloat() / rows.size,
                allSetsDone = rows.isNotEmpty() && rows.all { r -> r.logged }
            )
        }
        startTicking()
    }

    /* ---------------------------- stopwatches ----------------------------
     * One ticker drives every clock on the screen. Each is derived from a wall-clock start time
     * rather than accumulated by counting ticks, so a dropped frame, a backgrounded app or a
     * doze-throttled coroutine cannot make the displayed time drift away from reality — which a
     * counter incrementing itself once a second absolutely would over a 40-minute session.
     */

    private fun startTicking() {
        if (tickJob != null) return
        tickJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _state.update { st ->
                    st.copy(
                        exerciseElapsedMs = (now - exerciseStartedAtMs).coerceAtLeast(0),
                        setElapsedMs = if (st.runningSetIndex != null)
                            (now - currentSetStartedAtMs).coerceAtLeast(0) else 0L,
                        restElapsedMs = if (st.runningSetIndex == null && lastSetEndedAtMs > 0)
                            (now - lastSetEndedAtMs).coerceAtLeast(0) else 0L
                    )
                }
                delay(250)   // four times a second: smooth enough to read as a stopwatch
            }
        }
    }

    /** Begin timing a set. The exercise clock keeps running regardless. */
    fun startSet(setIndex: Int) {
        if (_state.value.runningSetIndex != null) return
        currentSetStartedAtMs = System.currentTimeMillis()
        _state.update { it.copy(runningSetIndex = setIndex, setElapsedMs = 0L, restElapsedMs = 0L) }
    }

    /** Abandon a running set without logging it — a mis-tap, not a completed set. */
    fun cancelSet() {
        _state.update { it.copy(runningSetIndex = null, setElapsedMs = 0L) }
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
        val now = System.currentTimeMillis()

        // Timing is only recorded when the stopwatch was actually started for THIS set. Logging a
        // set without starting it stays valid and simply writes zeros, which the timing stats
        // read as "not timed" rather than "took no time".
        val timed = _state.value.runningSetIndex == setIndex
        val startedAt = if (timed) currentSetStartedAtMs else 0L
        val duration = if (timed) (now - currentSetStartedAtMs).coerceAtLeast(0) else 0L

        repo.logSet(
            sessionId = sessionId,
            setIndex = setIndex,
            reps = row.reps,
            weightKg = _state.value.currentWeightKg,
            setType = row.plannedSet.setType,
            // Record the rung this set was actually done at, not just the exercise's current one:
            // pull-up's unassisted sets override to "standard" while the rest are band assisted.
            levelKey = row.plannedSet.levelKeyOverride ?: _state.value.currentLevelKey,
            startedAtMs = startedAt,
            durationMs = duration
        )
        if (timed) lastSetEndedAtMs = now

        val rows = _state.value.rows.mapIndexed { i, r ->
            if (i == setIndex) r.copy(logged = true, durationMs = duration) else r
        }
        val done = rows.isNotEmpty() && rows.all { it.logged }
        _state.update {
            it.copy(
                rows = rows,
                ringProgress = rows.count { r -> r.logged }.toFloat() / rows.size,
                allSetsDone = done,
                runningSetIndex = null,
                setElapsedMs = 0L
            )
        }

        if (done) {
            tickJob?.cancel(); tickJob = null
            // Full ring on the last set: gold flash + confetti + haptic, then auto-return.
            _state.update { it.copy(celebrate = true) }
            // Rest is now MEASURED rather than budgeted: total span minus time actually worked.
            val timing = SetTiming.of(repo.sessionTimings(exerciseId, date))
            val suggestion = repo.completeSession(
                exerciseId, date, (timing.restMs / 1000L).toInt()
            )
            _state.update { it.copy(pendingSuggestion = suggestion) }
        }
    }

    fun undoSet(setIndex: Int) = viewModelScope.launch {
        repo.undoSet(sessionId, setIndex)
        val rows = _state.value.rows.mapIndexed { i, r ->
            if (i == setIndex) r.copy(logged = false, durationMs = 0L) else r
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

}

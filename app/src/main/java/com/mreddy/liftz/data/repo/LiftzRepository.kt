package com.mreddy.liftz.data.repo

import com.mreddy.liftz.data.db.DailyLogEntity
import com.mreddy.liftz.data.db.ExerciseEntity
import com.mreddy.liftz.data.db.ExerciseSessionEntity
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.db.ExerciseWithPlan
import com.mreddy.liftz.data.db.GoalsEntity
import com.mreddy.liftz.data.db.IncrementsEntity
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.db.ProgressionSuggestionEntity
import com.mreddy.liftz.data.db.RoutineDayEntity
import com.mreddy.liftz.data.db.SessionWithSets
import com.mreddy.liftz.data.db.SetLogEntity
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.data.db.SuggestionKind
import com.mreddy.liftz.data.db.SuggestionStatus
import com.mreddy.liftz.domain.Calories
import com.mreddy.liftz.domain.DayCompletion
import com.mreddy.liftz.domain.ProgressionEngine
import com.mreddy.liftz.domain.TimeEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The single place the UI talks to for data.
 *
 * Rules of the house:
 *  - Room is the source of truth. Nothing is cached in memory beyond a Flow.
 *  - The domain layer stays pure: this class converts Room rows into the plain data classes
 *    [ProgressionEngine] and [TimeEstimator] expect, calls them, and writes the answers back.
 */
class LiftzRepository(private val db: LiftzDatabase) {

    private val exerciseDao = db.exerciseDao()
    private val levelDao = db.levelDao()
    private val plannedSetDao = db.plannedSetDao()
    private val routineDao = db.routineDao()
    private val sessionDao = db.sessionDao()
    private val dailyLogDao = db.dailyLogDao()
    private val configDao = db.configDao()
    private val suggestionDao = db.suggestionDao()

    /* ---------------------------------------------------------------------------------------
     * CONFIG
     * ------------------------------------------------------------------------------------- */

    fun observeGoals(): Flow<GoalsEntity> =
        configDao.observeGoals().map { it ?: GoalsEntity() }

    fun observeIncrements(): Flow<IncrementsEntity> =
        configDao.observeIncrements().map { it ?: IncrementsEntity() }

    suspend fun saveGoals(goals: GoalsEntity) = configDao.upsertGoals(goals.copy(id = 0))

    suspend fun saveIncrements(increments: IncrementsEntity) =
        configDao.upsertIncrements(increments.copy(id = 0))

    fun observeRoutineDays(): Flow<List<RoutineDayEntity>> = routineDao.observeDays()

    /* ---------------------------------------------------------------------------------------
     * CALENDAR
     * ------------------------------------------------------------------------------------- */

    /** One month of day cells, each already carrying its fill fraction and crown flag. */
    fun observeMonth(year: Int, month: Int): Flow<List<CalendarDay>> {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        return combine(
            dailyLogDao.observeRange(first.toEpochDay(), last.toEpochDay()),
            observeGoals(),
            routineDao.observeDays()
        ) { logs, goals, routineDays ->
            val byDay = logs.associateBy { it.epochDay }
            val workoutDayFlags = routineDays.associate { it.dayOfWeek to it.isWorkoutDay }
            (1..first.lengthOfMonth()).map { dom ->
                val date = first.withDayOfMonth(dom)
                val log = byDay[date.toEpochDay()]
                // Denominator comes from the ROUTINE PLAN, known upfront, not from what got logged.
                val isWorkoutDay = log?.isWorkoutDay
                    ?: workoutDayFlags[date.dayOfWeek.value] ?: false
                val completion = DayCompletion.of(
                    progress = DayCompletion.Progress(
                        waterMl = log?.waterMl ?: 0,
                        proteinG = log?.proteinG ?: 0,
                        carbsG = log?.carbsG ?: 0,
                        fatG = log?.fatG ?: 0,
                        calories = caloriesFor(log, goals),
                        isWorkoutDay = isWorkoutDay,
                        workoutCompleted = log?.workoutCompleted ?: false,
                        autoCalcCalories = goals.autoCalcCalories
                    ),
                    goals = DayCompletion.Goals(
                        goals.waterMl, goals.proteinG, goals.carbsG, goals.fatG, goals.calories
                    )
                )
                CalendarDay(
                    date = date,
                    isWorkoutDay = isWorkoutDay,
                    completion = completion
                )
            }
        }
    }

    data class CalendarDay(
        val date: LocalDate,
        val isWorkoutDay: Boolean,
        val completion: DayCompletion.Result
    )

    /* ---------------------------------------------------------------------------------------
     * DAILY LOG / MACROS
     * ------------------------------------------------------------------------------------- */

    fun observeDailyLog(date: LocalDate): Flow<DailyLogEntity?> =
        dailyLogDao.observe(date.toEpochDay())

    /**
     * Fetch the day's log, creating it (with the correct isWorkoutDay flag from the routine plan)
     * if this is the first time the day is touched.
     */
    suspend fun ensureDailyLog(date: LocalDate): DailyLogEntity {
        dailyLogDao.get(date.toEpochDay())?.let { return it }
        val isWorkoutDay = routineDao.getDay(date.dayOfWeek.value)?.isWorkoutDay ?: false
        val fresh = DailyLogEntity(epochDay = date.toEpochDay(), isWorkoutDay = isWorkoutDay)
        dailyLogDao.upsert(fresh)
        return fresh
    }

    /** Add (or subtract, with a negative delta) one increment of a macro. Never goes below zero. */
    suspend fun adjustMacro(date: LocalDate, macro: Macro, delta: Int) {
        val log = ensureDailyLog(date)
        val updated = when (macro) {
            Macro.WATER -> log.copy(waterMl = (log.waterMl + delta).coerceAtLeast(0))
            Macro.PROTEIN -> log.copy(proteinG = (log.proteinG + delta).coerceAtLeast(0))
            Macro.CARBS -> log.copy(carbsG = (log.carbsG + delta).coerceAtLeast(0))
            Macro.FAT -> log.copy(fatG = (log.fatG + delta).coerceAtLeast(0))
            Macro.CALORIES -> log.copy(calories = (log.calories + delta).coerceAtLeast(0))
        }
        dailyLogDao.upsert(updated)
    }

    /** Set a macro to an exact number (used by the "type it in" field). */
    suspend fun setMacro(date: LocalDate, macro: Macro, value: Int) {
        val log = ensureDailyLog(date)
        val v = value.coerceAtLeast(0)
        val updated = when (macro) {
            Macro.WATER -> log.copy(waterMl = v)
            Macro.PROTEIN -> log.copy(proteinG = v)
            Macro.CARBS -> log.copy(carbsG = v)
            Macro.FAT -> log.copy(fatG = v)
            Macro.CALORIES -> log.copy(calories = v)
        }
        dailyLogDao.upsert(updated)
    }

    enum class Macro { WATER, PROTEIN, CARBS, FAT, CALORIES }

    /**
     * The single place that decides what a day's calorie number is: derived from the macros when
     * auto-calc is on, otherwise the hand-entered column. Everything that displays calories goes
     * through here so the app, the widget and the summary screen cannot disagree.
     */
    fun caloriesFor(log: DailyLogEntity?, goals: GoalsEntity): Int = Calories.resolve(
        autoCalc = goals.autoCalcCalories,
        manualCalories = log?.calories ?: 0,
        proteinG = log?.proteinG ?: 0,
        carbsG = log?.carbsG ?: 0,
        fatG = log?.fatG ?: 0
    )

    /* ---------------------------------------------------------------------------------------
     * WORKOUT
     * ------------------------------------------------------------------------------------- */

    fun observeExercisesForDate(date: LocalDate): Flow<List<ExerciseWithPlan>> =
        exerciseDao.observeForDay(date.dayOfWeek.value)

    fun observeSessionsForDate(date: LocalDate): Flow<List<SessionWithSets>> =
        sessionDao.observeSessionsForDay(date.toEpochDay())

    fun observeExercise(exerciseId: String): Flow<ExerciseWithPlan?> =
        exerciseDao.observeWithPlan(exerciseId)

    fun observeSession(exerciseId: String, date: LocalDate): Flow<SessionWithSets?> =
        sessionDao.observeSession(exerciseId, date.toEpochDay())

    /**
     * Get today's session for an exercise, creating it if the user just opened the screen.
     * The level/weight are snapshotted at creation time so later progression changes do not
     * rewrite history.
     */
    suspend fun startSession(exerciseId: String, date: LocalDate): Long {
        sessionDao.getSession(exerciseId, date.toEpochDay())?.let { return it.session.id }
        val exercise = exerciseDao.getWithPlan(exerciseId)?.exercise
        val session = ExerciseSessionEntity(
            exerciseId = exerciseId,
            epochDay = date.toEpochDay(),
            levelKey = exercise?.currentLevelKey,
            weightKg = exercise?.currentWeightKg,
            startedAtMs = System.currentTimeMillis()
        )
        return sessionDao.insertSession(session)
    }

    /** Log or overwrite one set. Overwriting keeps the set index stable. */
    suspend fun logSet(
        sessionId: Long,
        setIndex: Int,
        reps: Int,
        weightKg: Double?,
        setType: SetType,
        /** The rung this set was actually done at: the planned set's override, else the current level. */
        levelKey: String? = null
    ) {
        sessionDao.deleteSet(sessionId, setIndex)
        sessionDao.insertSet(
            SetLogEntity(
                sessionId = sessionId,
                setIndex = setIndex,
                reps = reps.coerceAtLeast(0),
                weightKg = weightKg,
                levelKey = levelKey,
                setType = setType,
                loggedAtMs = System.currentTimeMillis()
            )
        )
    }

    /** Remove a logged set (the undo button on the exercise screen). */
    suspend fun undoSet(sessionId: Long, setIndex: Int) = sessionDao.deleteSet(sessionId, setIndex)

    /**
     * Reps already logged for today's session, keyed by set index.
     * Lets the exercise screen restore state if you back out and come back mid-workout.
     */
    suspend fun exerciseContextLoggedReps(exerciseId: String, date: LocalDate): Map<Int, Int> =
        sessionDao.getSession(exerciseId, date.toEpochDay())
            ?.orderedSets
            ?.associate { it.setIndex to it.reps }
            ?: emptyMap()

    /**
     * Close out an exercise: mark the session complete, roll the day's workout flag if every
     * planned exercise is now done, and evaluate the progression engine.
     *
     * @return a suggestion for the user to confirm, or null.
     */
    suspend fun completeSession(
        exerciseId: String,
        date: LocalDate,
        totalRestSeconds: Int
    ): ProgressionSuggestionEntity? {
        val existing = sessionDao.getSession(exerciseId, date.toEpochDay()) ?: return null
        sessionDao.updateSession(
            existing.session.copy(
                completed = true,
                finishedAtMs = System.currentTimeMillis(),
                totalRestSeconds = totalRestSeconds
            )
        )
        refreshWorkoutCompletion(date)
        return evaluateProgression(exerciseId)
    }

    /** Sets daily_logs.workoutCompleted once every exercise planned for that day is complete. */
    suspend fun refreshWorkoutCompletion(date: LocalDate) {
        val planned = exerciseDao.getForDay(date.dayOfWeek.value)
        if (planned.isEmpty()) return
        val completed = sessionDao.getSessionsForDay(date.toEpochDay())
            .filter { it.session.completed }
            .map { it.session.exerciseId }
            .toSet()
        val allDone = planned.all { it.exercise.id in completed }
        val log = ensureDailyLog(date)
        if (log.workoutCompleted != allDone) {
            dailyLogDao.upsert(log.copy(workoutCompleted = allDone))
        }
    }

    /* ---------------------------------------------------------------------------------------
     * PROGRESSION
     * ------------------------------------------------------------------------------------- */

    /** Room rows -> the pure input type the engine understands. */
    private suspend fun historyFor(exerciseId: String, limit: Int): List<ProgressionEngine.SessionSummary> =
        sessionDao.recentCompletedAnyLevel(exerciseId, limit).map { s ->
            ProgressionEngine.SessionSummary(
                epochDay = s.session.epochDay,
                levelKey = s.session.levelKey,
                weightKg = s.session.weightKg,
                sets = s.orderedSets.map { set ->
                    ProgressionEngine.LoggedSet(
                        setIndex = set.setIndex,
                        reps = set.reps,
                        // Rows written before schema v2 have no per-set level; fall back to the
                        // session's, which is what they were logged under.
                        levelKey = set.levelKey ?: s.session.levelKey
                    )
                }
            )
        }

    private fun snapshot(
        e: ExerciseEntity,
        levelKeysAscending: List<String>
    ) = ProgressionEngine.ExerciseSnapshot(
        exerciseId = e.id,
        type = e.type,
        hypertrophyMin = e.hypertrophyMin,
        hypertrophyMax = e.hypertrophyMax,
        rollingWindow = e.rollingWindow,
        progressionTracked = e.progressionTracked,
        levelKeysAscending = levelKeysAscending,
        currentLevelKey = e.currentLevelKey,
        currentWeightKg = e.currentWeightKg,
        weightIncrementKg = e.weightIncrementKg
    )

    /**
     * Run the engine and, if it says advance, park a PENDING suggestion for the user to confirm.
     * Nothing switches automatically, ever.
     */
    suspend fun evaluateProgression(exerciseId: String): ProgressionSuggestionEntity? {
        val plan = exerciseDao.getWithPlan(exerciseId) ?: return null
        val e = plan.exercise
        if (!e.progressionTracked || e.type == ExerciseType.CORE) return null

        // Pull a generous slice of history; the engine filters by level/weight itself.
        val history = historyFor(exerciseId, limit = e.rollingWindow * 4 + 10)
        val outcome = ProgressionEngine.evaluate(
            snapshot(e, plan.orderedLevels.map { it.levelKey }),
            history
        )

        suggestionDao.pendingFor(exerciseId)?.let { return it }   // one open prompt at a time

        val row = when (outcome) {
            is ProgressionEngine.Outcome.AdvanceLevel -> ProgressionSuggestionEntity(
                exerciseId = exerciseId,
                kind = SuggestionKind.LEVEL_UP,
                fromLevelKey = outcome.fromLevelKey,
                toLevelKey = outcome.toLevelKey,
                createdAtMs = System.currentTimeMillis(),
                rationale = outcome.rationale
            )
            is ProgressionEngine.Outcome.AddWeight -> ProgressionSuggestionEntity(
                exerciseId = exerciseId,
                kind = SuggestionKind.WEIGHT_UP,
                fromWeightKg = outcome.fromWeightKg,
                toWeightKg = outcome.toWeightKg,
                createdAtMs = System.currentTimeMillis(),
                rationale = outcome.rationale
            )
            else -> return null
        }
        val id = suggestionDao.insert(row)
        return row.copy(id = id)
    }

    fun observePendingSuggestions(): Flow<List<ProgressionSuggestionEntity>> =
        suggestionDao.observePending()

    /** User tapped "yes, move me up". */
    suspend fun acceptSuggestion(suggestion: ProgressionSuggestionEntity) {
        when (suggestion.kind) {
            SuggestionKind.LEVEL_UP -> suggestion.toLevelKey?.let {
                exerciseDao.setCurrentLevel(suggestion.exerciseId, it)
            }
            SuggestionKind.WEIGHT_UP -> suggestion.toWeightKg?.let {
                exerciseDao.setCurrentWeight(suggestion.exerciseId, it)
            }
        }
        suggestionDao.setStatus(suggestion.id, SuggestionStatus.ACCEPTED)
    }

    suspend fun dismissSuggestion(suggestion: ProgressionSuggestionEntity) =
        suggestionDao.setStatus(suggestion.id, SuggestionStatus.DISMISSED)

    /**
     * Manual level pick from the exercise screen. Any rung is allowed, including regressing after
     * missed workouts. The comparison target moves with it because history is filtered by level.
     */
    suspend fun setLevelManually(exerciseId: String, levelKey: String) =
        exerciseDao.setCurrentLevel(exerciseId, levelKey)

    suspend fun setWeightManually(exerciseId: String, weightKg: Double) =
        exerciseDao.setCurrentWeight(exerciseId, weightKg)

    suspend fun setRollingWindow(exerciseId: String, window: Int) =
        exerciseDao.setRollingWindow(exerciseId, window.coerceIn(1, 30))

    /* ---------------------------------------------------------------------------------------
     * READ MODELS FOR THE EXERCISE SCREEN
     * ------------------------------------------------------------------------------------- */

    /**
     * Everything the exercise screen needs that is not already in [ExerciseWithPlan]:
     * per-set default reps, the PR/baseline at the current level, and the engine's current verdict.
     */
    suspend fun exerciseContext(exerciseId: String): ExerciseContext? {
        val plan = exerciseDao.getWithPlan(exerciseId) ?: return null
        val e = plan.exercise
        val history = historyFor(exerciseId, limit = e.rollingWindow * 4 + 10)
        val levelKey = e.currentLevelKey

        val defaults = plan.orderedPlannedSets.map { ps ->
            ProgressionEngine.defaultRepsForSet(
                setIndex = ps.setIndex,
                isFixedRep = ps.setType == SetType.FIXED_REP,
                goalReps = ps.goalReps,
                history = history,
                levelKey = ps.levelKeyOverride ?: levelKey,
                hypertrophyMin = e.hypertrophyMin,
                weightKg = e.currentWeightKg
            )
        }

        val durations = sessionDao.recentCompletedAnyLevel(exerciseId, e.rollingWindow).map {
            TimeEstimator.Duration(
                epochDay = it.session.epochDay,
                seconds = ((it.session.finishedAtMs - it.session.startedAtMs) / 1000L).toInt()
            )
        }

        return ExerciseContext(
            plan = plan,
            defaultRepsPerSet = defaults,
            // weightKg only bites when there is no ladder, i.e. WEIGHTED exercises, where the
            // load is the rung: a 10 kg PR must not be shown as the record at 12 kg.
            personalRecord =
                ProgressionEngine.personalRecordAtLevel(history, levelKey, e.currentWeightKg),
            lastSessionTopReps =
                ProgressionEngine.baselineAtLevel(history, levelKey, e.currentWeightKg),
            outcome = ProgressionEngine.evaluate(
                snapshot(e, plan.orderedLevels.map { it.levelKey }), history
            ),
            estimatedSeconds = TimeEstimator.estimateExerciseSeconds(
                durations, e.rollingWindow, e.plannedSets, e.restSecondsPerSet
            ),
            plannedRestSeconds = e.plannedSets * e.restSecondsPerSet
        )
    }

    data class ExerciseContext(
        val plan: ExerciseWithPlan,
        val defaultRepsPerSet: List<Int>,
        val personalRecord: Int?,
        val lastSessionTopReps: Int?,
        val outcome: ProgressionEngine.Outcome,
        val estimatedSeconds: Int,
        val plannedRestSeconds: Int
    )

    /** Per-exercise time estimates for the workout screen header. */
    suspend fun estimatesForDate(date: LocalDate): Map<String, Int> {
        val exercises = exerciseDao.getForDay(date.dayOfWeek.value)
        return exercises.associate { ewp ->
            val e = ewp.exercise
            val durations = sessionDao.recentCompletedAnyLevel(e.id, e.rollingWindow).map {
                TimeEstimator.Duration(
                    it.session.epochDay,
                    ((it.session.finishedAtMs - it.session.startedAtMs) / 1000L).toInt()
                )
            }
            e.id to TimeEstimator.estimateExerciseSeconds(
                durations, e.rollingWindow, e.plannedSets, e.restSecondsPerSet
            )
        }
    }

    /* ---------------------------------------------------------------------------------------
     * POST-WORKOUT SUMMARY
     * ------------------------------------------------------------------------------------- */

    /**
     * What one training day actually amounted to, for the summary screen.
     *
     * Read-only: it reports history, it never writes or re-evaluates anything. A PR here means
     * "the best set of this session beat every earlier session AT THE SAME RUNG", which is the
     * same per-(exercise, level) comparison the engine uses — a set at an easier rung is not a PR
     * just because the number is bigger.
     */
    suspend fun daySummary(date: LocalDate): DaySummary {
        val planned = exerciseDao.getForDay(date.dayOfWeek.value)
        val sessions = sessionDao.getSessionsForDay(date.toEpochDay())
            .associateBy { it.session.exerciseId }

        val lines = planned.map { ewp ->
            val e = ewp.exercise
            val s = sessions[e.id]
            val sets = s?.orderedSets.orEmpty()

            // Earlier sessions at the same rung, so a PR is judged against comparable work only.
            val levelKey = s?.session?.levelKey ?: e.currentLevelKey
            val previousBest = historyFor(e.id, limit = 200)
                .filter { it.epochDay < date.toEpochDay() }
                .let { ProgressionEngine.personalRecordAtLevel(it, levelKey, e.currentWeightKg) }
            val bestThisSession = sets
                .filter { it.levelKey == levelKey || levelKey == null }
                .maxOfOrNull { it.reps }

            DaySummaryLine(
                exerciseId = e.id,
                name = e.name,
                levelLabel = ewp.currentLevel?.displayName,
                weightKg = s?.session?.weightKg ?: e.currentWeightKg,
                completed = s?.session?.completed == true,
                setsLogged = sets.size,
                setsPlanned = e.plannedSets,
                totalReps = sets.sumOf { it.reps },
                topReps = sets.maxOfOrNull { it.reps } ?: 0,
                // Only a PR if there is something to beat: a brand new rung is its own baseline,
                // not an instant record.
                isPersonalRecord = bestThisSession != null && previousBest != null &&
                    bestThisSession > previousBest,
                seconds = s?.session?.let {
                    ((it.finishedAtMs - it.startedAtMs) / 1000L).toInt().coerceAtLeast(0)
                } ?: 0,
                restSeconds = s?.session?.totalRestSeconds ?: 0,
                pendingSuggestion = suggestionDao.pendingFor(e.id)
            )
        }

        return DaySummary(
            date = date,
            lines = lines,
            allComplete = lines.isNotEmpty() && lines.all { it.completed }
        )
    }

    data class DaySummary(
        val date: LocalDate,
        val lines: List<DaySummaryLine>,
        val allComplete: Boolean
    ) {
        val exercisesCompleted: Int get() = lines.count { it.completed }
        val totalSets: Int get() = lines.sumOf { it.setsLogged }
        val totalReps: Int get() = lines.sumOf { it.totalReps }
        val totalSeconds: Int get() = lines.sumOf { it.seconds }
        val totalRestSeconds: Int get() = lines.sumOf { it.restSeconds }
        val personalRecords: List<DaySummaryLine> get() = lines.filter { it.isPersonalRecord }
    }

    data class DaySummaryLine(
        val exerciseId: String,
        val name: String,
        val levelLabel: String?,
        val weightKg: Double?,
        val completed: Boolean,
        val setsLogged: Int,
        val setsPlanned: Int,
        val totalReps: Int,
        val topReps: Int,
        val isPersonalRecord: Boolean,
        val seconds: Int,
        val restSeconds: Int,
        val pendingSuggestion: ProgressionSuggestionEntity?
    )
}

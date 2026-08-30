package com.mreddy.liftz.domain

import com.mreddy.liftz.data.db.ExerciseType

/**
 * ADAPTIVE PROGRESSION ENGINE
 *
 * Pure if-statement logic. No ML, no model, no magic. Everything here is a plain function over
 * plain data so it can be unit tested on the JVM without Room, Android or a device.
 *
 * Two cases:
 *
 *   Case A  BODYWEIGHT_PROGRESSION -> suggest moving one rung up the named level ladder.
 *   Case B  WEIGHTED              -> suggest currentWeightKg + weightIncrementKg.
 *
 * Both use the same trigger: the last `rollingWindow` CONSECUTIVE qualifying sessions at the
 * CURRENT level all hit or exceeded the top of the hypertrophy range.
 *
 * PRs and baselines are tracked per (exercise, level) PAIR, never globally per exercise. Change
 * level in either direction and the comparison target becomes that level's own history, so the
 * first session at a new level is simply that level's baseline.
 *
 * Nothing here mutates anything. The UI shows the suggestion, the user confirms, and only then
 * does the repository write the new level or weight.
 */
object ProgressionEngine {

    /* ---------------------------------------------------------------------------------------
     * INPUT TYPES  (deliberately dumb: no Room types, no Android types)
     * ------------------------------------------------------------------------------------- */

    /**
     * One logged set: how many reps, and which rung it was ACTUALLY performed at.
     *
     * The level lives on the set, not only on the session, because a session can legitimately mix
     * rungs. The seeded pull-up is exactly that: sets 0-1 unassisted at "standard", sets 2-4 at
     * "band_assisted". Attributing all five to the session's level would pool a 4-rep unassisted
     * set into the band-assisted history, which is the opposite of per-(exercise, level) tracking.
     */
    data class LoggedSet(val setIndex: Int, val reps: Int, val levelKey: String?)

    /** One past occurrence of one exercise, flattened to what the rules actually need. */
    data class SessionSummary(
        val epochDay: Long,
        /** The exercise's level at the time. Individual sets may override it. Null for weighted/core. */
        val levelKey: String?,
        /** Weight this session was performed at. Null for bodyweight and core exercises. */
        val weightKg: Double?,
        /** Every logged set, in set order. */
        val sets: List<LoggedSet>
    ) {
        /** Reps of every logged set, in set order, regardless of rung. */
        val reps: List<Int> get() = sets.map { it.reps }

        /**
         * The sets that count when evaluating [levelKey].
         *
         * A null [levelKey] means "this exercise has no ladder" (weighted or core), so every set
         * counts. A non-null one selects only the sets performed at that exact rung.
         */
        fun setsAt(levelKey: String?): List<LoggedSet> =
            if (levelKey == null) sets else sets.filter { it.levelKey == levelKey }

        fun repsAt(levelKey: String?): List<Int> = setsAt(levelKey).map { it.reps }

        val topReps: Int get() = reps.maxOrNull() ?: 0
        val lowestReps: Int get() = reps.minOrNull() ?: 0
        val isEmpty: Boolean get() = sets.isEmpty()

        companion object {
            /**
             * Every set performed at the session's own level — the ordinary, non-mixed case, and
             * what every exercise except pull-up looks like.
             */
            fun uniform(
                epochDay: Long,
                levelKey: String?,
                weightKg: Double?,
                reps: List<Int>
            ): SessionSummary = SessionSummary(
                epochDay = epochDay,
                levelKey = levelKey,
                weightKg = weightKg,
                sets = reps.mapIndexed { i, r -> LoggedSet(i, r, levelKey) }
            )
        }
    }

    /** Everything the engine needs to know about the exercise being evaluated. */
    data class ExerciseSnapshot(
        val exerciseId: String,
        val type: ExerciseType,
        val hypertrophyMin: Int,
        val hypertrophyMax: Int,
        val rollingWindow: Int,
        val progressionTracked: Boolean,
        /** Ladder, easiest first. Empty for weighted/core. */
        val levelKeysAscending: List<String> = emptyList(),
        val currentLevelKey: String? = null,
        val currentWeightKg: Double? = null,
        val weightIncrementKg: Double? = null
    )

    /* ---------------------------------------------------------------------------------------
     * OUTPUT TYPES
     * ------------------------------------------------------------------------------------- */

    sealed interface Outcome {
        /** Nothing to suggest. [reason] explains why, and is shown as a subtitle in the UI. */
        data class Hold(val reason: String, val qualifyingStreak: Int, val needed: Int) : Outcome

        /** Case A: move up the ladder. User confirms before this is applied. */
        data class AdvanceLevel(
            val exerciseId: String,
            val fromLevelKey: String,
            val toLevelKey: String,
            val rationale: String
        ) : Outcome

        /** Case B: add weight. User confirms before this is applied. */
        data class AddWeight(
            val exerciseId: String,
            val fromWeightKg: Double,
            val toWeightKg: Double,
            val rationale: String
        ) : Outcome

        /** Ladder exhausted: already on the hardest rung and still smashing the range. */
        data class TopOfLadder(val exerciseId: String, val levelKey: String) : Outcome
    }

    /* ---------------------------------------------------------------------------------------
     * THE RULES
     * ------------------------------------------------------------------------------------- */

    /**
     * Does this session count as "hit the top of the range"?
     *
     * Double-progression convention: EVERY working set has to reach the top of the range, not
     * just the best one. Using the lowest set stops one strong first set from carrying a session.
     */
    fun sessionQualifies(
        session: SessionSummary,
        hypertrophyMax: Int,
        levelKey: String? = null
    ): Boolean {
        // Only the sets performed at the rung being evaluated. A mixed session's sets at OTHER
        // rungs are irrelevant here and must not drag the minimum down.
        val reps = session.repsAt(levelKey)
        return reps.isNotEmpty() && reps.min() >= hypertrophyMax
    }

    /**
     * Count how many of the most recent sessions qualify, without a gap.
     *
     * [recentFirst] must be newest-first and already filtered to the current level (Case A) or to
     * the current weight (Case B). The count stops at the first session that fails, which is what
     * makes the window CONSECUTIVE rather than "any N of the last M".
     */
    fun qualifyingStreak(
        recentFirst: List<SessionSummary>,
        hypertrophyMax: Int,
        levelKey: String? = null
    ): Int {
        var streak = 0
        for (session in recentFirst) {
            if (sessionQualifies(session, hypertrophyMax, levelKey)) streak++ else break
        }
        return streak
    }

    /**
     * Main entry point.
     *
     * @param history every completed session for this exercise, NEWEST FIRST. The engine does its
     *                own filtering, so callers can just hand over the recent history.
     */
    fun evaluate(exercise: ExerciseSnapshot, history: List<SessionSummary>): Outcome {
        // Core exercises rotate too much to track. Nothing to suggest, ever.
        if (!exercise.progressionTracked || exercise.type == ExerciseType.CORE) {
            return Outcome.Hold("Not progression tracked", 0, 0)
        }
        val window = exercise.rollingWindow.coerceAtLeast(1)

        return when (exercise.type) {
            ExerciseType.BODYWEIGHT_PROGRESSION -> evaluateLadder(exercise, history, window)
            ExerciseType.WEIGHTED -> evaluateWeighted(exercise, history, window)
            ExerciseType.CORE -> Outcome.Hold("Not progression tracked", 0, 0)
        }
    }

    /* ------------------------------ Case A: level ladder ------------------------------ */

    private fun evaluateLadder(
        exercise: ExerciseSnapshot,
        history: List<SessionSummary>,
        window: Int
    ): Outcome {
        val currentLevel = exercise.currentLevelKey
            ?: return Outcome.Hold("No current level set", 0, window)

        // PER (EXERCISE, LEVEL): a session counts if it actually contains work at this rung,
        // and only its sets at this rung are judged. Filtering on the session's own levelKey
        // would be wrong for a mixed session like pull-up.
        val atLevel = history.filter { it.setsAt(currentLevel).isNotEmpty() }
        val streak = qualifyingStreak(atLevel.take(window), exercise.hypertrophyMax, currentLevel)

        if (streak < window) {
            return Outcome.Hold(
                reason = "Need ${exercise.hypertrophyMax}+ reps on every set for $window " +
                    "sessions in a row at this level",
                qualifyingStreak = streak,
                needed = window
            )
        }

        val index = exercise.levelKeysAscending.indexOf(currentLevel)
        if (index < 0) return Outcome.Hold("Current level is not on the ladder", streak, window)
        if (index >= exercise.levelKeysAscending.lastIndex) {
            return Outcome.TopOfLadder(exercise.exerciseId, currentLevel)
        }

        val next = exercise.levelKeysAscending[index + 1]
        return Outcome.AdvanceLevel(
            exerciseId = exercise.exerciseId,
            fromLevelKey = currentLevel,
            toLevelKey = next,
            rationale = "$window sessions in a row at ${exercise.hypertrophyMax}+ reps on every " +
                "set. Ready for the next level."
        )
    }

    /* ------------------------------ Case B: added weight ------------------------------ */

    private fun evaluateWeighted(
        exercise: ExerciseSnapshot,
        history: List<SessionSummary>,
        window: Int
    ): Outcome {
        val current = exercise.currentWeightKg
            ?: return Outcome.Hold("No current weight set", 0, window)
        val increment = exercise.weightIncrementKg ?: 0.0
        if (increment <= 0.0) return Outcome.Hold("No weight increment set", 0, window)

        // Same idea as the level filter: history at a lighter weight does not earn a jump from
        // the current weight, so only sessions at the current load count.
        val atWeight = history.filter { it.weightKg != null && sameWeight(it.weightKg, current) }
        val streak = qualifyingStreak(atWeight.take(window), exercise.hypertrophyMax)

        if (streak < window) {
            return Outcome.Hold(
                reason = "Need ${exercise.hypertrophyMax}+ reps on every set for $window " +
                    "sessions in a row at ${fmt(current)} kg",
                qualifyingStreak = streak,
                needed = window
            )
        }

        return Outcome.AddWeight(
            exerciseId = exercise.exerciseId,
            fromWeightKg = current,
            toWeightKg = current + increment,
            rationale = "$window sessions in a row at ${exercise.hypertrophyMax}+ reps on every " +
                "set. Add ${fmt(increment)} kg."
        )
    }

    /* ---------------------------------------------------------------------------------------
     * BASELINES / TARGETS
     * ------------------------------------------------------------------------------------- */

    /**
     * The number to beat for a TO_FAILURE set, per (exercise, level).
     *
     * Returns null when this level has no history yet, which is exactly the "first session at a
     * new level becomes its baseline" case. Regressing to an easier rung picks that rung's own
     * history back up rather than comparing against the harder one.
     */
    fun baselineAtLevel(
        history: List<SessionSummary>,
        levelKey: String?,
        weightKg: Double? = null
    ): Int? = rungHistory(history, levelKey, weightKg)
        .firstOrNull { it.repsAt(levelKey).isNotEmpty() }
        ?.repsAt(levelKey)
        ?.maxOrNull()

    /** All-time best single set at this rung. Null if the rung is untouched. */
    fun personalRecordAtLevel(
        history: List<SessionSummary>,
        levelKey: String?,
        weightKg: Double? = null
    ): Int? = rungHistory(history, levelKey, weightKg).flatMap { it.repsAt(levelKey) }.maxOrNull()

    /**
     * The history that belongs to one rung.
     *
     * "Rung" is whichever of the two progression axes this exercise uses:
     *
     *   Case A  BODYWEIGHT_PROGRESSION -> the level. Sessions containing work at that level.
     *   Case B  WEIGHTED               -> the load. Sessions performed at that weight, because a
     *                                     10 kg PR is not a 12 kg PR, exactly as evaluateWeighted
     *                                     already refuses to count lighter sessions toward a jump.
     *   Neither (core / untracked)     -> everything.
     */
    private fun rungHistory(
        history: List<SessionSummary>,
        levelKey: String?,
        weightKg: Double?
    ): List<SessionSummary> = when {
        levelKey != null -> history.filter { it.setsAt(levelKey).isNotEmpty() }
        weightKg != null -> history.filter { it.weightKg != null && sameWeight(it.weightKg, weightKg) }
        else -> history
    }

    /**
     * Pre-fill value for one set's rep input.
     *
     * FIXED_REP  -> the planned goal reps for that set.
     * TO_FAILURE -> whatever was logged LAST TIME THIS EXERCISE OCCURRED at the same set index
     *               (not the previous set of today's session). Falls back to the last session's
     *               best set, then to the bottom of the hypertrophy range for a brand new rung.
     */
    fun defaultRepsForSet(
        setIndex: Int,
        isFixedRep: Boolean,
        goalReps: Int,
        history: List<SessionSummary>,
        levelKey: String?,
        hypertrophyMin: Int,
        weightKg: Double? = null
    ): Int {
        if (isFixedRep) return goalReps
        val lastSession = rungHistory(history, levelKey, weightKg)
            .firstOrNull { it.repsAt(levelKey).isNotEmpty() }
            ?: return hypertrophyMin
        // Match on the stored set index rather than a list position, so filtering out sets from
        // other rungs cannot silently shift set 3's target onto set 1.
        return lastSession.sets.firstOrNull { it.setIndex == setIndex && it.levelKey == levelKey }
            ?.reps
            ?: lastSession.repsAt(levelKey).maxOrNull()
            ?: hypertrophyMin
    }

    /* ------------------------------------ helpers ------------------------------------ */

    /** Doubles from the DB are exact enough for gym weights, but compare with a tolerance anyway. */
    private fun sameWeight(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.001

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

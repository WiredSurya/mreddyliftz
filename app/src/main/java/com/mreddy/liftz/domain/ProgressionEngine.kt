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

    /** One past occurrence of one exercise, flattened to what the rules actually need. */
    data class SessionSummary(
        val epochDay: Long,
        /** Level this session was performed at. Null for weighted and core exercises. */
        val levelKey: String?,
        /** Weight this session was performed at. Null for bodyweight and core exercises. */
        val weightKg: Double?,
        /** Reps of every logged set, in set order. */
        val reps: List<Int>
    ) {
        val topReps: Int get() = reps.maxOrNull() ?: 0
        val lowestReps: Int get() = reps.minOrNull() ?: 0
        val isEmpty: Boolean get() = reps.isEmpty()
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
    fun sessionQualifies(session: SessionSummary, hypertrophyMax: Int): Boolean =
        !session.isEmpty && session.lowestReps >= hypertrophyMax

    /**
     * Count how many of the most recent sessions qualify, without a gap.
     *
     * [recentFirst] must be newest-first and already filtered to the current level (Case A) or to
     * the current weight (Case B). The count stops at the first session that fails, which is what
     * makes the window CONSECUTIVE rather than "any N of the last M".
     */
    fun qualifyingStreak(recentFirst: List<SessionSummary>, hypertrophyMax: Int): Int {
        var streak = 0
        for (session in recentFirst) {
            if (sessionQualifies(session, hypertrophyMax)) streak++ else break
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

        // PER (EXERCISE, LEVEL): only sessions performed at this exact rung count.
        val atLevel = history.filter { it.levelKey == currentLevel }
        val streak = qualifyingStreak(atLevel.take(window), exercise.hypertrophyMax)

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
    fun baselineAtLevel(history: List<SessionSummary>, levelKey: String?): Int? {
        val relevant = if (levelKey == null) history else history.filter { it.levelKey == levelKey }
        return relevant.firstOrNull { !it.isEmpty }?.topReps
    }

    /** All-time best single set at this (exercise, level) pair. Null if the rung is untouched. */
    fun personalRecordAtLevel(history: List<SessionSummary>, levelKey: String?): Int? {
        val relevant = if (levelKey == null) history else history.filter { it.levelKey == levelKey }
        return relevant.flatMap { it.reps }.maxOrNull()
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
        hypertrophyMin: Int
    ): Int {
        if (isFixedRep) return goalReps
        val lastSession = (if (levelKey == null) history else history.filter { it.levelKey == levelKey })
            .firstOrNull { !it.isEmpty }
            ?: return hypertrophyMin
        return lastSession.reps.getOrNull(setIndex) ?: lastSession.topReps
    }

    /* ------------------------------------ helpers ------------------------------------ */

    /** Doubles from the DB are exact enough for gym weights, but compare with a tolerance anyway. */
    private fun sameWeight(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.001

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

package com.mreddy.liftz.domain

/**
 * How full a calendar day cell should be.
 *
 * The denominator is known UPFRONT from the routine plan, not computed after the fact:
 *
 *   workout day     -> 5  (water, protein, carbs, calories, workout)
 *   non-workout day -> 4  (water, protein, carbs, calories)
 *
 * A day at 5/5 (or 4/4) is a crown day.
 */
object DayCompletion {

    data class Goals(val waterMl: Int, val proteinG: Int, val carbsG: Int, val calories: Int)

    data class Progress(
        val waterMl: Int,
        val proteinG: Int,
        val carbsG: Int,
        val calories: Int,
        val isWorkoutDay: Boolean,
        val workoutCompleted: Boolean
    )

    data class Result(
        val hits: Int,
        val denominator: Int,
        /** 0f..1f. This is the green fill height of the day cell. */
        val fraction: Float,
        val isCrown: Boolean,
        /** Per-goal breakdown for the day detail sheet, in display order. */
        val breakdown: List<GoalHit>
    )

    data class GoalHit(val label: String, val current: Int, val target: Int, val hit: Boolean)

    fun of(progress: Progress, goals: Goals): Result {
        val breakdown = buildList {
            add(goalHit("Water", progress.waterMl, goals.waterMl))
            add(goalHit("Protein", progress.proteinG, goals.proteinG))
            add(goalHit("Carbs", progress.carbsG, goals.carbsG))
            add(goalHit("Calories", progress.calories, goals.calories))
            if (progress.isWorkoutDay) {
                add(
                    GoalHit(
                        label = "Workout",
                        current = if (progress.workoutCompleted) 1 else 0,
                        target = 1,
                        hit = progress.workoutCompleted
                    )
                )
            }
        }
        val denominator = breakdown.size          // 5 on workout days, 4 otherwise
        val hits = breakdown.count { it.hit }
        val fraction = if (denominator == 0) 0f else hits.toFloat() / denominator
        return Result(
            hits = hits,
            denominator = denominator,
            fraction = fraction,
            isCrown = hits == denominator && denominator > 0,
            breakdown = breakdown
        )
    }

    /** A goal counts as hit when the target is reached or beaten. Zero target = free pass. */
    private fun goalHit(label: String, current: Int, target: Int) =
        GoalHit(label, current, target, hit = target <= 0 || current >= target)
}

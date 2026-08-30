package com.mreddy.liftz.domain

/**
 * Per-exercise time estimates, used for the workout screen's "time to completion".
 *
 * Uses the SAME rolling window as the progression engine rather than full history, so a handful of
 * slow sessions from months ago cannot drag today's estimate around.
 */
object TimeEstimator {

    /** Assumed seconds of actual work per set when there is no history to learn from. */
    private const val FALLBACK_WORK_SECONDS_PER_SET = 40

    /** One past session reduced to its duration. */
    data class Duration(val epochDay: Long, val seconds: Int)

    /**
     * Estimated seconds for one exercise.
     *
     * @param recentDurationsNewestFirst durations of past sessions, newest first
     * @param rollingWindow how many of them to average
     * @param plannedSets used only for the cold-start fallback
     * @param restSecondsPerSet used only for the cold-start fallback
     */
    fun estimateExerciseSeconds(
        recentDurationsNewestFirst: List<Duration>,
        rollingWindow: Int,
        plannedSets: Int,
        restSecondsPerSet: Int
    ): Int {
        val window = recentDurationsNewestFirst.take(rollingWindow.coerceAtLeast(1))
            .filter { it.seconds > 0 }
        if (window.isEmpty()) {
            // Cold start: plan it out from the rest scheme.
            return plannedSets * (restSecondsPerSet + FALLBACK_WORK_SECONDS_PER_SET)
        }
        return window.sumOf { it.seconds } / window.size
    }

    /**
     * Remaining seconds for a whole workout: full estimate for every exercise not yet started,
     * plus a pro-rated remainder for the one in progress.
     */
    fun estimateRemainingSeconds(
        perExerciseEstimate: List<Int>,
        inProgressIndex: Int?,
        inProgressSetsDone: Int,
        inProgressSetsPlanned: Int
    ): Int {
        var total = 0
        perExerciseEstimate.forEachIndexed { index, seconds ->
            when {
                inProgressIndex == null || index > inProgressIndex -> total += seconds
                index == inProgressIndex -> {
                    val planned = inProgressSetsPlanned.coerceAtLeast(1)
                    val remainingFraction =
                        ((planned - inProgressSetsDone).coerceAtLeast(0)).toFloat() / planned
                    total += (seconds * remainingFraction).toInt()
                }
                // index < inProgressIndex: already done, contributes nothing.
            }
        }
        return total
    }

    /** "1h 05m" / "42m" / "50s" */
    fun format(seconds: Int): String {
        if (seconds <= 0) return "0m"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            m > 0 -> "%dm".format(m)
            else -> "%ds".format(s)
        }
    }
}

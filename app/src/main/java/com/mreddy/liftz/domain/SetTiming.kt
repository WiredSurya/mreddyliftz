package com.mreddy.liftz.domain

/**
 * What a stopwatch on every set lets you say that a countdown never could.
 *
 * The old rest timer counted DOWN from a planned budget, so the only thing it could ever tell you
 * was how much of your plan you had spent. Timing each set individually means work time and rest
 * time become separately measured facts, and everything below falls out of the subtraction:
 * rest per set, tempo, session density, and whether you slowed down as the exercise went on.
 *
 * Pure Kotlin, no Android or Room imports, so it is unit tested on the JVM like the progression
 * engine.
 *
 * THE ZERO RULE, everywhere in this file: `durationMs == 0` means NOT TIMED, never "took no
 * time". Sets logged before schema 4, or logged without starting the stopwatch, carry zeros.
 * Averaging them in would report that old workouts were infinitely fast, so they are filtered
 * out at every entry point and the counts reported alongside every average.
 */
object SetTiming {

    data class TimedSet(
        val setIndex: Int,
        val reps: Int,
        val startedAtMs: Long,
        val durationMs: Long
    ) {
        val isTimed: Boolean get() = durationMs > 0 && startedAtMs > 0
        val endedAtMs: Long get() = startedAtMs + durationMs
        /** Seconds per rep. Null when untimed or when no reps were logged. */
        val secondsPerRep: Double?
            get() = if (!isTimed || reps <= 0) null else (durationMs / 1000.0) / reps
    }

    data class Timing(
        /** Sum of timed set durations. */
        val workMs: Long,
        /** Sum of the gaps between consecutive timed sets. */
        val restMs: Long,
        /** First set start to last set end. Work + rest, plus nothing else. */
        val spanMs: Long,
        val timedSets: Int,
        val untimedSets: Int,
        /** Rest before each set after the first, in order. */
        val restGapsMs: List<Long>,
        /** Seconds per rep for each timed set with reps, in set order. */
        val tempos: List<Double>,
        /**
         * Least-squares slope of tempo against set index, in seconds-per-rep per set.
         *
         * Positive means later sets were slower per rep than earlier ones — the measurable
         * signature of fatigue within an exercise. Null with fewer than three timed sets, because
         * a slope through two points is a straight line through noise, not a trend.
         */
        val tempoSlope: Double?
    ) {
        /** Share of the span actually spent working, 0f..1f. Null when nothing was timed. */
        val density: Float?
            get() = if (spanMs <= 0 || timedSets == 0) null
            else (workMs.toFloat() / spanMs).coerceIn(0f, 1f)

        val avgRestMs: Long? get() = restGapsMs.takeIf { it.isNotEmpty() }?.average()?.toLong()
        val avgSetMs: Long? get() = if (timedSets == 0) null else workMs / timedSets
        val avgSecondsPerRep: Double? get() = tempos.takeIf { it.isNotEmpty() }?.average()

        /** True when there is enough timed data to show any of this without misleading. */
        val hasData: Boolean get() = timedSets > 0
    }

    fun of(sets: List<TimedSet>): Timing {
        val ordered = sets.sortedBy { it.setIndex }
        val timed = ordered.filter { it.isTimed }

        if (timed.isEmpty()) {
            return Timing(0, 0, 0, 0, ordered.size, emptyList(), emptyList(), null)
        }

        val work = timed.sumOf { it.durationMs }

        // Rest is the gap between one set ending and the next STARTING. Negative gaps are
        // discarded rather than clamped: they mean the clock was manipulated (a set restarted, a
        // timezone shift), and silently treating that as zero rest would understate real rest.
        val gaps = timed.zipWithNext { a, b -> b.startedAtMs - a.endedAtMs }.filter { it >= 0 }

        val span = timed.last().endedAtMs - timed.first().startedAtMs
        val tempos = timed.mapNotNull { it.secondsPerRep }

        return Timing(
            workMs = work,
            restMs = gaps.sum(),
            spanMs = span.coerceAtLeast(0),
            timedSets = timed.size,
            untimedSets = ordered.size - timed.size,
            restGapsMs = gaps,
            tempos = tempos,
            tempoSlope = slope(tempos)
        )
    }

    /**
     * Least-squares slope of y against its own index.
     *
     * Needs three points minimum — see [Timing.tempoSlope]. Returns null for a flat x, which
     * cannot happen with indices but keeps the division honest.
     */
    internal fun slope(y: List<Double>): Double? {
        if (y.size < 3) return null
        val n = y.size
        val meanX = (n - 1) / 2.0
        val meanY = y.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = i - meanX
            num += dx * (y[i] - meanY)
            den += dx * dx
        }
        return if (den == 0.0) null else num / den
    }

    /**
     * "2h 15m" / "45m" / "30s" — for totals spanning a training history, where M:SS would print
     * a cumulative four hours as "247:30" and read as nonsense.
     */
    fun formatLong(ms: Long): String {
        val totalMinutes = (ms / 60_000).coerceAtLeast(0)
        return when {
            totalMinutes >= 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
            totalMinutes > 0 -> "${totalMinutes}m"
            else -> "${(ms / 1000).coerceAtLeast(0)}s"
        }
    }

    /** "1:24", or "0:47". Minutes and seconds is the only resolution that matters in a gym. */
    fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}

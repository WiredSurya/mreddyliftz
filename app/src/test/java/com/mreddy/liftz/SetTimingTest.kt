package com.mreddy.liftz

import com.mreddy.liftz.domain.SetTiming
import com.mreddy.liftz.domain.SetTiming.TimedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTimingTest {

    /** Set n starts at `start`, runs `dur`, then `rest` before the next one. */
    private fun sets(vararg spec: Triple<Long, Long, Int>): List<TimedSet> {
        var t = 0L
        return spec.mapIndexed { i, (dur, rest, reps) ->
            val s = TimedSet(i, reps, startedAtMs = 1000L + t, durationMs = dur)
            t += dur + rest
            s
        }
    }

    @Test
    fun `work and rest are separately measured, not one budget`() {
        // 30s set, 60s rest, 30s set, 60s rest, 30s set
        val t = SetTiming.of(sets(
            Triple(30_000L, 60_000L, 10),
            Triple(30_000L, 60_000L, 10),
            Triple(30_000L, 0L, 10)
        ))
        assertEquals(90_000L, t.workMs)
        assertEquals(120_000L, t.restMs)
        assertEquals(210_000L, t.spanMs)
        assertEquals(60_000L, t.avgRestMs)
    }

    @Test
    fun `density is the share of the span actually spent working`() {
        val t = SetTiming.of(sets(
            Triple(30_000L, 30_000L, 10),
            Triple(30_000L, 30_000L, 10),
            Triple(30_000L, 0L, 10)
        ))
        // 90s work in a 150s span
        assertEquals(0.6f, t.density!!, 0.001f)
    }

    /*
     * THE ZERO RULE. Sets logged before schema 4 carry durationMs = 0, meaning "not timed".
     * Treating those as zero-length would report the work as instantaneous and the density as
     * perfect, which is exactly backwards.
     */
    @Test
    fun `untimed sets are excluded rather than counted as instant`() {
        val timed = TimedSet(0, 10, startedAtMs = 1000L, durationMs = 30_000L)
        val legacy = TimedSet(1, 10, startedAtMs = 0L, durationMs = 0L)
        val t = SetTiming.of(listOf(timed, legacy))

        assertEquals(1, t.timedSets)
        assertEquals(1, t.untimedSets)
        assertEquals(30_000L, t.workMs)
        assertEquals(30_000L, t.avgSetMs)
    }

    @Test
    fun `a session with nothing timed reports no data rather than zeros`() {
        val t = SetTiming.of(listOf(TimedSet(0, 10, 0L, 0L), TimedSet(1, 9, 0L, 0L)))
        assertFalse(t.hasData)
        assertNull(t.density)
        assertNull(t.avgRestMs)
        assertNull(t.avgSecondsPerRep)
    }

    @Test
    fun `tempo is seconds per rep`() {
        val s = TimedSet(0, 10, startedAtMs = 1000L, durationMs = 30_000L)
        assertEquals(3.0, s.secondsPerRep!!, 0.001)
    }

    @Test
    fun `a set with no reps has no tempo rather than dividing by zero`() {
        assertNull(TimedSet(0, 0, 1000L, 30_000L).secondsPerRep)
    }

    @Test
    fun `slowing down across sets shows as a positive tempo slope`() {
        // 10 reps in 30s, then 10 in 40s, then 10 in 50s: 3.0, 4.0, 5.0 s/rep
        val t = SetTiming.of(sets(
            Triple(30_000L, 60_000L, 10),
            Triple(40_000L, 60_000L, 10),
            Triple(50_000L, 0L, 10)
        ))
        assertTrue("expected positive slope, got ${t.tempoSlope}", t.tempoSlope!! > 0.9)
    }

    @Test
    fun `a steady tempo has a flat slope`() {
        val t = SetTiming.of(sets(
            Triple(30_000L, 60_000L, 10),
            Triple(30_000L, 60_000L, 10),
            Triple(30_000L, 0L, 10)
        ))
        assertEquals(0.0, t.tempoSlope!!, 0.001)
    }

    /*
     * A slope through two points is a line through noise, not a trend. Reporting "you are
     * fatiguing" off two sets would be the kind of confident nonsense that makes a stat useless.
     */
    @Test
    fun `fewer than three timed sets has no slope at all`() {
        val t = SetTiming.of(sets(
            Triple(30_000L, 60_000L, 10),
            Triple(50_000L, 0L, 10)
        ))
        assertNull(t.tempoSlope)
    }

    @Test
    fun `a clock going backwards is discarded rather than counted as zero rest`() {
        val a = TimedSet(0, 10, startedAtMs = 100_000L, durationMs = 30_000L)
        val b = TimedSet(1, 10, startedAtMs = 50_000L, durationMs = 30_000L)  // starts before a ends
        val t = SetTiming.of(listOf(a, b))
        assertTrue(t.restGapsMs.isEmpty())
        assertEquals(0L, t.restMs)
    }

    @Test
    fun `format is minutes and seconds`() {
        assertEquals("0:47", SetTiming.format(47_000))
        assertEquals("1:24", SetTiming.format(84_000))
        assertEquals("0:00", SetTiming.format(-5))
    }
}

class SetTimingFormatLongTest {

    @Test
    fun `hours are shown as hours rather than a huge minute count`() {
        // The bug this pins: M:SS would print four hours as "247:30".
        assertEquals("4h 7m", SetTiming.formatLong(4 * 3_600_000L + 7 * 60_000L))
        assertEquals("45m", SetTiming.formatLong(45 * 60_000L))
        assertEquals("30s", SetTiming.formatLong(30_000L))
        assertEquals("0s", SetTiming.formatLong(0))
    }
}

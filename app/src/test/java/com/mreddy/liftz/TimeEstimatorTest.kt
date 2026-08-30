package com.mreddy.liftz

import com.mreddy.liftz.domain.TimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeEstimatorTest {

    @Test
    fun `cold start falls back to the rest scheme`() {
        val seconds = TimeEstimator.estimateExerciseSeconds(
            recentDurationsNewestFirst = emptyList(),
            rollingWindow = 6,
            plannedSets = 3,
            restSecondsPerSet = 90
        )
        assertEquals(3 * (90 + 40), seconds)
    }

    @Test
    fun `only the rolling window counts so old slow sessions do not drag the estimate`() {
        val durations = listOf(
            TimeEstimator.Duration(1000, 300),
            TimeEstimator.Duration(999, 300),
            TimeEstimator.Duration(998, 3000)   // ancient outlier, outside a window of 2
        )
        val estimate = TimeEstimator.estimateExerciseSeconds(durations, 2, 3, 90)
        assertEquals(300, estimate)
    }

    @Test
    fun `remaining time pro-rates the exercise in progress`() {
        val remaining = TimeEstimator.estimateRemainingSeconds(
            perExerciseEstimate = listOf(600, 400, 200),
            inProgressIndex = 1,
            inProgressSetsDone = 2,
            inProgressSetsPlanned = 4
        )
        // exercise 0 done, half of 400 left, plus all of 200
        assertEquals(400, remaining)
    }

    @Test
    fun `formatting is readable`() {
        assertEquals("0m", TimeEstimator.format(0))
        assertEquals("50s", TimeEstimator.format(50))
        assertEquals("42m", TimeEstimator.format(42 * 60))
        assertTrue(TimeEstimator.format(3900).startsWith("1h"))
    }
}

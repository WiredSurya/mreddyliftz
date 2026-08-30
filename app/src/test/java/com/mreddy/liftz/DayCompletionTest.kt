package com.mreddy.liftz

import com.mreddy.liftz.domain.DayCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCompletionTest {

    private val goals = DayCompletion.Goals(waterMl = 3000, proteinG = 140, carbsG = 250, calories = 2600)

    @Test
    fun `workout day has a denominator of five`() {
        val result = DayCompletion.of(
            DayCompletion.Progress(0, 0, 0, 0, isWorkoutDay = true, workoutCompleted = false),
            goals
        )
        assertEquals(5, result.denominator)
    }

    @Test
    fun `rest day has a denominator of four`() {
        val result = DayCompletion.of(
            DayCompletion.Progress(0, 0, 0, 0, isWorkoutDay = false, workoutCompleted = false),
            goals
        )
        assertEquals(4, result.denominator)
    }

    @Test
    fun `three of five gives a zero point six fill`() {
        val result = DayCompletion.of(
            DayCompletion.Progress(3000, 140, 250, 0, isWorkoutDay = true, workoutCompleted = false),
            goals
        )
        assertEquals(3, result.hits)
        assertEquals(0.6f, result.fraction, 0.0001f)
        assertFalse(result.isCrown)
    }

    @Test
    fun `everything hit on a workout day is a crown`() {
        val result = DayCompletion.of(
            DayCompletion.Progress(3200, 150, 260, 2700, isWorkoutDay = true, workoutCompleted = true),
            goals
        )
        assertEquals(5, result.hits)
        assertEquals(1f, result.fraction, 0.0001f)
        assertTrue(result.isCrown)
    }

    @Test
    fun `a rest day can crown on four goals alone`() {
        val result = DayCompletion.of(
            DayCompletion.Progress(3000, 140, 250, 2600, isWorkoutDay = false, workoutCompleted = false),
            goals
        )
        assertTrue(result.isCrown)
        assertEquals(4, result.denominator)
    }
}

package com.mreddy.liftz

import com.mreddy.liftz.domain.DayCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCompletionTest {

    private val goals = DayCompletion.Goals(
        waterMl = 3000, proteinG = 140, carbsG = 250, fatG = 115, calories = 2600
    )

    /** Everything named, so adding a macro can never silently shift a positional argument. */
    private fun progress(
        waterMl: Int = 0,
        proteinG: Int = 0,
        carbsG: Int = 0,
        fatG: Int = 0,
        calories: Int = 0,
        isWorkoutDay: Boolean,
        workoutCompleted: Boolean = false,
        autoCalcCalories: Boolean = true
    ) = DayCompletion.Progress(
        waterMl = waterMl,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        calories = calories,
        isWorkoutDay = isWorkoutDay,
        workoutCompleted = workoutCompleted,
        autoCalcCalories = autoCalcCalories
    )

    @Test
    fun `workout day has a denominator of five`() {
        val result = DayCompletion.of(progress(isWorkoutDay = true), goals)
        assertEquals(5, result.denominator)
    }

    @Test
    fun `rest day has a denominator of four`() {
        val result = DayCompletion.of(progress(isWorkoutDay = false), goals)
        assertEquals(4, result.denominator)
    }

    /*
     * The 5/4 denominators are load-bearing for the calendar fill, and adding fat could easily
     * have pushed them to 6/5. It does not, because the fourth macro slot holds EITHER fat (when
     * calories are derived) OR calories (when they are typed in) — never both. These two pin that.
     */
    @Test
    fun `denominator stays five when calories are entered by hand`() {
        val result = DayCompletion.of(
            progress(isWorkoutDay = true, autoCalcCalories = false), goals
        )
        assertEquals(5, result.denominator)
    }

    @Test
    fun `the fourth macro goal is fat when auto and calories when manual`() {
        val auto = DayCompletion.of(progress(isWorkoutDay = false), goals)
        assertEquals(
            listOf("Water", "Protein", "Carbs", "Fat"),
            auto.breakdown.map { it.label }
        )
        val manual = DayCompletion.of(
            progress(isWorkoutDay = false, autoCalcCalories = false), goals
        )
        assertEquals(
            listOf("Water", "Protein", "Carbs", "Calories"),
            manual.breakdown.map { it.label }
        )
    }

    @Test
    fun `three of five gives a zero point six fill`() {
        val result = DayCompletion.of(
            progress(waterMl = 3000, proteinG = 140, carbsG = 250, isWorkoutDay = true),
            goals
        )
        assertEquals(3, result.hits)
        assertEquals(0.6f, result.fraction, 0.0001f)
        assertFalse(result.isCrown)
    }

    @Test
    fun `everything hit on a workout day is a crown`() {
        val result = DayCompletion.of(
            progress(
                waterMl = 3200, proteinG = 150, carbsG = 260, fatG = 120,
                isWorkoutDay = true, workoutCompleted = true
            ),
            goals
        )
        assertEquals(5, result.hits)
        assertEquals(1f, result.fraction, 0.0001f)
        assertTrue(result.isCrown)
    }

    @Test
    fun `a rest day can crown on four goals alone`() {
        val result = DayCompletion.of(
            progress(
                waterMl = 3000, proteinG = 140, carbsG = 250, fatG = 115,
                isWorkoutDay = false
            ),
            goals
        )
        assertTrue(result.isCrown)
        assertEquals(4, result.denominator)
    }

    @Test
    fun `hitting protein and carbs but not fat is not a crown`() {
        // The regression this guards: before fat existed, protein+carbs+water+calories was a
        // full house. It must not be one any more, or fat would be cosmetic.
        val result = DayCompletion.of(
            progress(waterMl = 3000, proteinG = 140, carbsG = 250, fatG = 0, isWorkoutDay = false),
            goals
        )
        assertFalse(result.isCrown)
        assertEquals(3, result.hits)
    }
}

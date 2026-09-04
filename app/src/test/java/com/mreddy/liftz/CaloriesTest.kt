package com.mreddy.liftz

import com.mreddy.liftz.domain.Calories
import org.junit.Assert.assertEquals
import org.junit.Test

class CaloriesTest {

    @Test
    fun `atwater factors are four four nine`() {
        assertEquals(4, Calories.fromMacros(proteinG = 1, carbsG = 0, fatG = 0))
        assertEquals(4, Calories.fromMacros(proteinG = 0, carbsG = 1, fatG = 0))
        assertEquals(9, Calories.fromMacros(proteinG = 0, carbsG = 0, fatG = 1))
    }

    @Test
    fun `the seeded targets actually add up to the calorie goal`() {
        // This is the number that motivated the whole change: the shipped goals are 140g protein,
        // 250g carbs and a 2600 kcal target. Without fat that only reaches 1560, so the calorie
        // goal was unreachable. 115g of fat closes the gap.
        assertEquals(1560, Calories.fromMacros(proteinG = 140, carbsG = 250, fatG = 0))
        assertEquals(2595, Calories.fromMacros(proteinG = 140, carbsG = 250, fatG = 115))
    }

    @Test
    fun `negative inputs are floored rather than subtracting energy`() {
        assertEquals(0, Calories.fromMacros(proteinG = -50, carbsG = 0, fatG = 0))
        assertEquals(400, Calories.fromMacros(proteinG = 100, carbsG = -20, fatG = 0))
    }

    @Test
    fun `resolve derives when auto and passes through the manual value when not`() {
        assertEquals(
            2595,
            Calories.resolve(
                autoCalc = true, manualCalories = 9999,
                proteinG = 140, carbsG = 250, fatG = 115
            )
        )
        assertEquals(
            9999,
            Calories.resolve(
                autoCalc = false, manualCalories = 9999,
                proteinG = 140, carbsG = 250, fatG = 115
            )
        )
    }
}

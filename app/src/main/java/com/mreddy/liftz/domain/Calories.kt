package com.mreddy.liftz.domain

/**
 * Energy maths. Pure, no Android or Room, unit-testable on the JVM like the rest of `domain/`.
 *
 * Uses the standard Atwater factors, the same 4/4/9 every nutrition label is built on:
 *   protein 4 kcal/g, carbohydrate 4 kcal/g, fat 9 kcal/g.
 *
 * Fat is not optional here, and that is the whole point of this file existing. Fat is the most
 * energy-dense of the three, so leaving it out does not give a slightly-low answer, it gives a
 * badly wrong one: against the seeded targets, protein and carbs alone come to 1560 kcal against
 * a 2600 goal. Deriving calories from an incomplete macro set would have made the calorie goal
 * permanently unreachable, so fat tracking had to land in the same change.
 */
object Calories {

    const val KCAL_PER_G_PROTEIN = 4
    const val KCAL_PER_G_CARBS = 4
    const val KCAL_PER_G_FAT = 9

    /** Energy from the three macros, in kcal. */
    fun fromMacros(proteinG: Int, carbsG: Int, fatG: Int): Int =
        proteinG.coerceAtLeast(0) * KCAL_PER_G_PROTEIN +
            carbsG.coerceAtLeast(0) * KCAL_PER_G_CARBS +
            fatG.coerceAtLeast(0) * KCAL_PER_G_FAT

    /**
     * What to display for a day: derived when auto-calc is on, otherwise the manually entered
     * number. Keeping this decision in one function stops the UI, the widget and the summary
     * screen from each having their own slightly different idea of what "calories" means.
     */
    fun resolve(
        autoCalc: Boolean,
        manualCalories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int
    ): Int = if (autoCalc) fromMacros(proteinG, carbsG, fatG) else manualCalories
}

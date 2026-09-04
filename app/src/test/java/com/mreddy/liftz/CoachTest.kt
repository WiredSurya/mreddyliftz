package com.mreddy.liftz

import com.mreddy.liftz.domain.Coach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {

    private fun input(
        trackedDays: Int = 10,
        workoutsPlanned: Int = 10,
        workoutsCompleted: Int = 10,
        currentStreak: Int = 0,
        longestStreak: Int = 0,
        crownDays: Int = 0,
        avgProteinG: Int = 140,
        avgWaterMl: Int = 3000,
        avgCalories: Int = 2600,
        exercises: List<Coach.ExerciseState> = emptyList()
    ) = Coach.Input(
        trackedDays = trackedDays,
        workoutsPlanned = workoutsPlanned,
        workoutsCompleted = workoutsCompleted,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        crownDays = crownDays,
        avgProteinG = avgProteinG,
        goalProteinG = 140,
        avgWaterMl = avgWaterMl,
        goalWaterMl = 3000,
        avgCalories = avgCalories,
        goalCalories = 2600,
        exercises = exercises
    )

    private fun ex(
        name: String = "Pull-up",
        sessions: Int = 3,
        qualifyingStreak: Int = 0,
        windowNeeded: Int = 6,
        readyToAdvance: Boolean = false,
        atTopOfLadder: Boolean = false
    ) = Coach.ExerciseState(
        name = name, levelLabel = "Band assisted", sessions = sessions,
        qualifyingStreak = qualifyingStreak, windowNeeded = windowNeeded,
        readyToAdvance = readyToAdvance, atTopOfLadder = atTopOfLadder,
        personalRecord = 10, lastReps = 9
    )

    @Test
    fun `a brand new user gets one actionable prompt, not a wall of nothing`() {
        val out = Coach.insights(
            input(trackedDays = 0, workoutsPlanned = 0, workoutsCompleted = 0)
        )
        assertEquals(1, out.size)
        assertEquals(Coach.Kind.ACTION, out.first().kind)
    }

    @Test
    fun `ready to advance is surfaced above everything else`() {
        val out = Coach.insights(
            input(crownDays = 5, exercises = listOf(ex(readyToAdvance = true)))
        )
        assertTrue(out.first().title.contains("ready to move up", ignoreCase = true))
    }

    @Test
    fun `nearly there tells you exactly how many sessions remain`() {
        val out = Coach.insights(input(exercises = listOf(ex(qualifyingStreak = 4, windowNeeded = 6))))
        val hit = out.first { it.title.contains("more to level up") }
        assertTrue(hit.title.contains("2 more"))
    }

    @Test
    fun `a stalled exercise is called out with a regression suggestion`() {
        val out = Coach.insights(input(exercises = listOf(ex(sessions = 6, qualifyingStreak = 0))))
        val hit = out.first { it.title.contains("stalled") }
        assertEquals(Coach.Kind.WATCH, hit.kind)
        assertTrue(hit.body.contains("Dropping back", ignoreCase = true))
    }

    @Test
    fun `low protein is flagged only once there are enough tracked days`() {
        assertTrue(
            Coach.insights(input(trackedDays = 2, avgProteinG = 50))
                .none { it.title.contains("Protein") }
        )
        assertTrue(
            Coach.insights(input(trackedDays = 10, avgProteinG = 50))
                .any { it.title.contains("Protein") }
        )
    }

    @Test
    fun `hitting every goal produces no nagging`() {
        val out = Coach.insights(input(exercises = listOf(ex(qualifyingStreak = 1))))
        assertTrue(out.none { it.kind == Coach.Kind.WATCH })
    }

    @Test
    fun `insights come back highest priority first`() {
        val out = Coach.insights(
            input(
                trackedDays = 10, avgProteinG = 50, crownDays = 2,
                exercises = listOf(ex(readyToAdvance = true))
            )
        )
        assertEquals(out, out.sortedByDescending { it.priority })
    }
}

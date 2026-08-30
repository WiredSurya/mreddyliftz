package com.mreddy.liftz

import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.domain.ProgressionEngine
import com.mreddy.liftz.domain.ProgressionEngine.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests. No device, no Room, no emulator, which matters on an 8GB dev machine.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class ProgressionEngineTest {

    private val pullUp = ProgressionEngine.ExerciseSnapshot(
        exerciseId = "pull_up",
        type = ExerciseType.BODYWEIGHT_PROGRESSION,
        hypertrophyMin = 8,
        hypertrophyMax = 12,
        rollingWindow = 6,
        progressionTracked = true,
        levelKeysAscending = listOf(
            "dead_hang", "scapular_pull", "negative", "band_assisted",
            "standard", "weighted", "archer"
        ),
        currentLevelKey = "band_assisted"
    )

    private val dbPress = ProgressionEngine.ExerciseSnapshot(
        exerciseId = "standing_db_press",
        type = ExerciseType.WEIGHTED,
        hypertrophyMin = 8,
        hypertrophyMax = 12,
        rollingWindow = 6,
        progressionTracked = true,
        currentWeightKg = 10.0,
        weightIncrementKg = 2.0
    )

    private fun sessions(
        count: Int,
        reps: List<Int>,
        level: String? = null,
        weight: Double? = null,
        startDay: Long = 1000
    ) = (0 until count).map {
        SessionSummary.uniform(startDay - it, level, weight, reps)
    }

    /* ------------------------------- Case A: ladder ------------------------------- */

    @Test
    fun `no suggestion before the window is full`() {
        val history = sessions(5, listOf(12, 12, 12, 12, 12), level = "band_assisted")
        val outcome = ProgressionEngine.evaluate(pullUp, history)
        assertTrue(outcome is ProgressionEngine.Outcome.Hold)
        assertEquals(5, (outcome as ProgressionEngine.Outcome.Hold).qualifyingStreak)
    }

    @Test
    fun `six qualifying sessions at the level suggests the next rung`() {
        val history = sessions(6, listOf(12, 12, 12, 12, 12), level = "band_assisted")
        val outcome = ProgressionEngine.evaluate(pullUp, history)
        assertTrue(outcome is ProgressionEngine.Outcome.AdvanceLevel)
        outcome as ProgressionEngine.Outcome.AdvanceLevel
        assertEquals("band_assisted", outcome.fromLevelKey)
        assertEquals("standard", outcome.toLevelKey)
    }

    @Test
    fun `one weak set inside the window breaks the streak`() {
        // Newest session has a set at 11: the whole session fails to qualify.
        val history = listOf(SessionSummary.uniform(1000, "band_assisted", null, listOf(12, 12, 11))) +
            sessions(6, listOf(12, 12, 12), level = "band_assisted", startDay = 999)
        val outcome = ProgressionEngine.evaluate(pullUp, history)
        assertTrue(outcome is ProgressionEngine.Outcome.Hold)
        assertEquals(0, (outcome as ProgressionEngine.Outcome.Hold).qualifyingStreak)
    }

    @Test
    fun `sessions at a different level do not count toward this level`() {
        // Six perfect sessions, but all at the easier rung.
        val history = sessions(6, listOf(12, 12, 12), level = "negative")
        val outcome = ProgressionEngine.evaluate(pullUp, history)
        assertTrue(outcome is ProgressionEngine.Outcome.Hold)
    }

    @Test
    fun `top of the ladder reports top of ladder rather than advancing`() {
        val atTop = pullUp.copy(currentLevelKey = "archer")
        val history = sessions(6, listOf(12, 12, 12), level = "archer")
        val outcome = ProgressionEngine.evaluate(atTop, history)
        assertTrue(outcome is ProgressionEngine.Outcome.TopOfLadder)
    }

    /* ------------------------------- Case B: weight ------------------------------- */

    @Test
    fun `six qualifying sessions at the current weight suggests adding the increment`() {
        val history = sessions(6, listOf(12, 12, 12), weight = 10.0)
        val outcome = ProgressionEngine.evaluate(dbPress, history)
        assertTrue(outcome is ProgressionEngine.Outcome.AddWeight)
        outcome as ProgressionEngine.Outcome.AddWeight
        assertEquals(10.0, outcome.fromWeightKg, 0.001)
        assertEquals(12.0, outcome.toWeightKg, 0.001)
    }

    @Test
    fun `history at a lighter weight does not earn a jump`() {
        val history = sessions(6, listOf(12, 12, 12), weight = 8.0)
        val outcome = ProgressionEngine.evaluate(dbPress, history)
        assertTrue(outcome is ProgressionEngine.Outcome.Hold)
    }

    /* ------------------------------- core & config ------------------------------- */

    @Test
    fun `core exercises never progress`() {
        val core = pullUp.copy(type = ExerciseType.CORE, progressionTracked = false)
        val outcome = ProgressionEngine.evaluate(core, sessions(20, listOf(50), level = "band_assisted"))
        assertTrue(outcome is ProgressionEngine.Outcome.Hold)
    }

    @Test
    fun `editing the rolling window changes how long it takes`() {
        val shortWindow = pullUp.copy(rollingWindow = 3)
        val history = sessions(3, listOf(12, 12, 12), level = "band_assisted")
        assertTrue(ProgressionEngine.evaluate(shortWindow, history)
            is ProgressionEngine.Outcome.AdvanceLevel)
        assertTrue(ProgressionEngine.evaluate(pullUp, history)
            is ProgressionEngine.Outcome.Hold)
    }

    /* ------------------------------- baselines / PRs ------------------------------- */

    @Test
    fun `PR and baseline are per exercise-level pair`() {
        val history = listOf(
            SessionSummary.uniform(1000, "band_assisted", null, listOf(9, 8, 8)),
            SessionSummary.uniform(999, "band_assisted", null, listOf(11, 10, 9)),
            SessionSummary.uniform(998, "negative", null, listOf(15, 14, 14))
        )
        assertEquals(11, ProgressionEngine.personalRecordAtLevel(history, "band_assisted"))
        assertEquals(15, ProgressionEngine.personalRecordAtLevel(history, "negative"))
        // Baseline = most recent session at that level, i.e. the number to beat next time.
        assertEquals(9, ProgressionEngine.baselineAtLevel(history, "band_assisted"))
    }

    @Test
    fun `a brand new level has no baseline`() {
        val history = sessions(6, listOf(12, 12), level = "band_assisted")
        assertNull(ProgressionEngine.baselineAtLevel(history, "standard"))
        assertNull(ProgressionEngine.personalRecordAtLevel(history, "standard"))
    }

    /* ------------------------------- set defaults ------------------------------- */

    @Test
    fun `fixed rep sets pre-fill with the planned goal`() {
        val value = ProgressionEngine.defaultRepsForSet(
            setIndex = 2, isFixedRep = true, goalReps = 8,
            history = emptyList(), levelKey = "band_assisted", hypertrophyMin = 8
        )
        assertEquals(8, value)
    }

    @Test
    fun `to failure sets pre-fill with the same set index from the last session of this exercise`() {
        val history = listOf(
            SessionSummary.uniform(1000, "negative", null, listOf(7, 6, 5)),
            SessionSummary.uniform(999, "negative", null, listOf(4, 4, 4))
        )
        assertEquals(6, ProgressionEngine.defaultRepsForSet(
            setIndex = 1, isFixedRep = false, goalReps = 0,
            history = history, levelKey = "negative", hypertrophyMin = 8
        ))
    }

    @Test
    fun `to failure falls back to hypertrophy min on a fresh level`() {
        assertEquals(6, ProgressionEngine.defaultRepsForSet(
            setIndex = 0, isFixedRep = false, goalReps = 0,
            history = emptyList(), levelKey = "full_nordic_curl", hypertrophyMin = 6
        ))
    }

    /* =====================================================================================
     * MIXED-RUNG SESSIONS
     *
     * The seeded pull-up logs sets 0-1 unassisted at "standard" and sets 2-4 at "band_assisted"
     * in one session. Before per-set level attribution existed, all five were credited to the
     * session's level, so a 4-rep unassisted set held the session minimum below hypertrophyMax
     * forever and pull-up could never progress. These lock that shut.
     * ================================================================================== */

    /** The real seeded shape: two hard unassisted sets, then three band-assisted ones. */
    private fun pullUpSession(day: Long, unassisted: List<Int>, banded: List<Int>) =
        SessionSummary(
            epochDay = day,
            levelKey = "band_assisted",
            weightKg = null,
            sets = unassisted.mapIndexed { i, r ->
                ProgressionEngine.LoggedSet(i, r, "standard")
            } + banded.mapIndexed { i, r ->
                ProgressionEngine.LoggedSet(unassisted.size + i, r, "band_assisted")
            }
        )

    @Test
    fun `unassisted sets do not block band assisted progression`() {
        // Every band-assisted set is at the top of the range; the unassisted ones never will be.
        val history = (0 until 6).map { pullUpSession(1000L - it, listOf(4, 3), listOf(12, 12, 12)) }
        val outcome = ProgressionEngine.evaluate(pullUp, history)
        assertTrue(
            "Six qualifying band-assisted sessions should advance the ladder, but got $outcome. " +
                "A low unassisted set at a DIFFERENT rung must not count against this one.",
            outcome is ProgressionEngine.Outcome.AdvanceLevel
        )
        assertEquals("standard", (outcome as ProgressionEngine.Outcome.AdvanceLevel).toLevelKey)
    }

    @Test
    fun `a weak band assisted set still blocks progression`() {
        // Same shape, but one banded set falls short. The rung being evaluated must still fail.
        val history = (0 until 6).map { pullUpSession(1000L - it, listOf(4, 3), listOf(12, 11, 12)) }
        assertTrue(ProgressionEngine.evaluate(pullUp, history) is ProgressionEngine.Outcome.Hold)
    }

    @Test
    fun `PR does not leak across rungs inside one session`() {
        val history = listOf(pullUpSession(1000, listOf(6, 5), listOf(12, 11, 10)))
        assertEquals(
            "Band-assisted PR must come from the band-assisted sets only",
            12, ProgressionEngine.personalRecordAtLevel(history, "band_assisted")
        )
        assertEquals(
            "Unassisted PR must come from the standard-level sets only",
            6, ProgressionEngine.personalRecordAtLevel(history, "standard")
        )
    }

    @Test
    fun `baseline reads only the sets at the rung asked for`() {
        val history = listOf(pullUpSession(1000, listOf(6, 5), listOf(9, 9, 8)))
        assertEquals(9, ProgressionEngine.baselineAtLevel(history, "band_assisted"))
        assertEquals(6, ProgressionEngine.baselineAtLevel(history, "standard"))
    }

    @Test
    fun `TO_FAILURE prefill matches the stored set index at the same rung`() {
        val history = listOf(pullUpSession(1000, listOf(6, 5), listOf(9, 9, 8)))
        // Set 1 is the second unassisted set -> 5, not the second element of the whole session.
        assertEquals(
            5,
            ProgressionEngine.defaultRepsForSet(
                setIndex = 1, isFixedRep = false, goalReps = 0,
                history = history, levelKey = "standard", hypertrophyMin = 8
            )
        )
    }

    /* =====================================================================================
     * WEIGHTED EXERCISES: the load IS the rung
     * ================================================================================== */

    @Test
    fun `weighted PR does not leak across loads`() {
        val history = sessions(1, listOf(12, 12, 12), weight = 10.0, startDay = 1000) +
            sessions(1, listOf(8, 8, 7), weight = 12.0, startDay = 999)
        assertEquals(
            "12 kg PR must not report the easier 10 kg session's 12 reps",
            8, ProgressionEngine.personalRecordAtLevel(history, null, weightKg = 12.0)
        )
        assertEquals(
            12, ProgressionEngine.personalRecordAtLevel(history, null, weightKg = 10.0)
        )
    }

    @Test
    fun `dropping back to a lighter load reads that load's own history`() {
        val history = sessions(1, listOf(6, 6, 5), weight = 12.0, startDay = 1000) +
            sessions(1, listOf(11, 10, 10), weight = 10.0, startDay = 999)
        assertEquals(
            "Regressing to 10 kg must compare against 10 kg history, mirroring the ladder rule",
            11, ProgressionEngine.baselineAtLevel(history, null, weightKg = 10.0)
        )
    }

    @Test
    fun `a load with no history has no PR or baseline`() {
        val history = sessions(3, listOf(12, 12, 12), weight = 10.0)
        assertNull(ProgressionEngine.personalRecordAtLevel(history, null, weightKg = 14.0))
        assertNull(ProgressionEngine.baselineAtLevel(history, null, weightKg = 14.0))
    }
}

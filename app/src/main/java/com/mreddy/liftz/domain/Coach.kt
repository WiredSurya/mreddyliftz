package com.mreddy.liftz.domain

/**
 * The rule-based coach.
 *
 * Turns what is already logged into specific, actionable observations. Every insight here names
 * a real number from the user's own history — there is deliberately no library of generic advice
 * to shuffle through, because generic tips are exactly what feels canned on the second read.
 * If the data does not support an observation, nothing is said about it.
 *
 * Pure: no Android, no Room, unit-testable on the JVM like the rest of `domain/`.
 *
 * Scope note: this talks about training mechanics and the numbers in the app. It is not medical
 * or nutritional advice and should not start giving any.
 */
object Coach {

    enum class Kind {
        /** Something went well and is worth reinforcing. */
        WIN,
        /** A concrete next action. */
        ACTION,
        /** Something drifting that has not broken yet. */
        WATCH
    }

    data class Insight(
        val kind: Kind,
        val title: String,
        val body: String,
        /** Higher sorts first. */
        val priority: Int
    )

    /** Inputs, kept as plain numbers so the engine never touches Room or the repository. */
    data class ExerciseState(
        val name: String,
        val levelLabel: String?,
        val sessions: Int,
        val qualifyingStreak: Int,
        val windowNeeded: Int,
        val readyToAdvance: Boolean,
        val atTopOfLadder: Boolean,
        val personalRecord: Int?,
        val lastReps: Int?
    )

    data class Input(
        val trackedDays: Int,
        val workoutsPlanned: Int,
        val workoutsCompleted: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        val crownDays: Int,
        val avgProteinG: Int,
        val goalProteinG: Int,
        val avgWaterMl: Int,
        val goalWaterMl: Int,
        val avgCalories: Int,
        val goalCalories: Int,
        val exercises: List<ExerciseState>
    )

    fun insights(input: Input): List<Insight> {
        val out = mutableListOf<Insight>()

        if (input.trackedDays == 0 && input.exercises.all { it.sessions == 0 }) {
            return listOf(
                Insight(
                    Kind.ACTION,
                    "Log one session to start",
                    "The coach reads your own history, so it has nothing to work with yet. " +
                        "Finish one workout and specific observations will appear here.",
                    priority = 100
                )
            )
        }

        /* ---- progression ---- */
        input.exercises.filter { it.readyToAdvance }.forEach { ex ->
            out += Insight(
                Kind.ACTION,
                "${ex.name} is ready to move up",
                "You have hit the top of the rep range for ${ex.windowNeeded} sessions in a row" +
                    (ex.levelLabel?.let { " at $it" } ?: "") +
                    ". Confirm the step up on the exercise screen — nothing changes until you do.",
                priority = 90
            )
        }

        input.exercises.filter { it.atTopOfLadder }.forEach { ex ->
            out += Insight(
                Kind.WIN,
                "${ex.name}: top of the ladder",
                "There is no harder progression left for this one. Add reps, slow the tempo, or " +
                    "start loading it if you want to keep pushing.",
                priority = 55
            )
        }

        // Close but not there — the most motivating thing to surface.
        input.exercises
            .filter { !it.readyToAdvance && it.windowNeeded > 0 && it.qualifyingStreak > 0 }
            .filter { it.qualifyingStreak >= it.windowNeeded - 2 }
            .forEach { ex ->
                val left = ex.windowNeeded - ex.qualifyingStreak
                out += Insight(
                    Kind.ACTION,
                    "${ex.name}: $left more to level up",
                    "You are ${ex.qualifyingStreak}/${ex.windowNeeded} qualifying sessions in. " +
                        "Hold the top of the range for $left more and the app will offer the " +
                        "next step.",
                    priority = 80
                )
            }

        // Plenty of sessions, no streak at all: the classic plateau signature.
        input.exercises
            .filter { it.sessions >= 4 && it.qualifyingStreak == 0 && !it.atTopOfLadder }
            .forEach { ex ->
                out += Insight(
                    Kind.WATCH,
                    "${ex.name} has stalled",
                    "${ex.sessions} sessions logged and no qualifying one yet" +
                        (ex.levelLabel?.let { " at $it" } ?: "") +
                        ". That usually means the current step is too big. Dropping back a level " +
                        "for a couple of weeks is a normal fix, not a failure — the app tracks " +
                        "records per level, so nothing is lost.",
                    priority = 70
                )
            }

        /* ---- consistency ---- */
        if (input.currentStreak >= 3) {
            out += Insight(
                Kind.WIN,
                "${input.currentStreak} workouts in a row",
                if (input.currentStreak >= input.longestStreak && input.longestStreak > 0)
                    "That matches or beats your best run so far."
                else "Your best run is ${input.longestStreak}. Keep going.",
                priority = 60
            )
        }

        if (input.workoutsPlanned >= 4) {
            val rate = input.workoutsCompleted.toFloat() / input.workoutsPlanned
            if (rate < 0.6f) {
                out += Insight(
                    Kind.WATCH,
                    "Finishing about ${(rate * 100).toInt()}% of planned workouts",
                    "${input.workoutsCompleted} of ${input.workoutsPlanned}. If the plan is the " +
                        "problem rather than the week, cutting to fewer days you actually hit " +
                        "beats missing more of them.",
                    priority = 65
                )
            }
        }

        /* ---- macros ---- */
        if (input.trackedDays >= 3) {
            if (input.goalProteinG > 0 && input.avgProteinG < input.goalProteinG * 0.8) {
                out += Insight(
                    Kind.WATCH,
                    "Protein averaging ${input.avgProteinG}g",
                    "Against a ${input.goalProteinG}g goal, across ${input.trackedDays} tracked " +
                        "days. Of everything the app tracks, this is the one most likely to be " +
                        "holding back what the training is doing.",
                    priority = 50
                )
            }
            if (input.goalWaterMl > 0 && input.avgWaterMl < input.goalWaterMl * 0.7) {
                out += Insight(
                    Kind.WATCH,
                    "Water averaging ${input.avgWaterMl}ml",
                    "Against ${input.goalWaterMl}ml. Easiest of the four to fix — the widget's " +
                        "plus button exists for exactly this.",
                    priority = 40
                )
            }
            if (input.goalCalories > 0 && input.avgCalories < input.goalCalories * 0.75) {
                out += Insight(
                    Kind.WATCH,
                    "Eating under your calorie target",
                    "Averaging ${input.avgCalories} against ${input.goalCalories} kcal. Worth a " +
                        "look if you are training to add size rather than lean out.",
                    priority = 45
                )
            }
        }

        if (input.crownDays > 0) {
            out += Insight(
                Kind.WIN,
                "${input.crownDays} perfect ${if (input.crownDays == 1) "day" else "days"}",
                "Every goal hit, workout included. That is the hardest thing this app asks for.",
                priority = 35
            )
        }

        if (out.isEmpty()) {
            out += Insight(
                Kind.ACTION,
                "Nothing to flag",
                "No stalls, no gaps worth calling out, nothing ready to level up yet. Keep " +
                    "logging and this fills in.",
                priority = 10
            )
        }

        return out.sortedByDescending { it.priority }
    }
}

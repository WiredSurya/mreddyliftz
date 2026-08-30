package com.mreddy.liftz.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * THE PORTABLE FORMAT.
 *
 * Deliberately snake_case, flat, and self-describing so a human (or a future AI session with zero
 * prior context) can read one export and understand the entire app state, or hand-write an import
 * file to change the routine.
 *
 * Everything except [exercises] is optional on import, so a minimal file is still valid.
 */
@Serializable
data class LiftzExport(
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("exported_at") val exportedAt: String = "",
    @SerialName("app") val app: String = "mreddyLiftz",

    /** Progression-tracked exercises: bodyweight ladders and weighted lifts. */
    val exercises: List<ExerciseJson> = emptyList(),

    /** Rotating core work. No advancement logic, plain set/rep logging. */
    @SerialName("core_exercises") val coreExercises: List<CoreExerciseJson> = emptyList(),

    val goals: GoalsJson = GoalsJson(),
    val increments: IncrementsJson = IncrementsJson(),

    /** Weekly plan. dayOfWeek: 1 = Monday .. 7 = Sunday. */
    @SerialName("routine_days") val routineDays: List<RoutineDayJson> = emptyList(),

    /** Optional full history, so an export doubles as a backup. Safe to omit. */
    @SerialName("daily_logs") val dailyLogs: List<DailyLogJson> = emptyList(),
    @SerialName("sessions") val sessions: List<SessionJson> = emptyList()
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

@Serializable
data class ExerciseJson(
    val id: String,
    val name: String,
    /** "bodyweight_progression" or "weighted" */
    val type: String,
    /** "FIXED_REP" or "TO_FAILURE" (the default for sets that do not override it) */
    @SerialName("set_type") val setType: String,
    /** [min, max] */
    @SerialName("hypertrophy_range") val hypertrophyRange: List<Int> = listOf(8, 12),
    @SerialName("rolling_window") val rollingWindow: Int = 6,
    @SerialName("planned_sets") val plannedSets: Int = 3,

    /* --- bodyweight_progression only --- */
    /** Ordered easiest -> hardest. */
    val levels: List<String> = emptyList(),
    @SerialName("current_level") val currentLevel: String? = null,

    /* --- weighted only --- */
    @SerialName("current_weight_kg") val currentWeightKg: Double? = null,
    @SerialName("weight_increment_kg") val weightIncrementKg: Double? = null,

    /**
     * Per-set plan. Present when the sets are NOT uniform (e.g. pull-up: 2 unassisted to failure
     * then 3 band assisted). Omit it and planned_sets identical sets are generated.
     */
    @SerialName("set_plan") val setPlan: List<PlannedSetJson> = emptyList(),

    @SerialName("rest_seconds_per_set") val restSecondsPerSet: Int = 90,
    @SerialName("form_description") val formDescription: String = "",
    val notes: String = ""
)

@Serializable
data class PlannedSetJson(
    @SerialName("set_index") val setIndex: Int,
    @SerialName("set_type") val setType: String,
    /** Only meaningful for FIXED_REP. */
    @SerialName("goal_reps") val goalReps: Int = 0,
    /** Overrides the exercise's current_level for this one set. */
    @SerialName("level") val levelKeyOverride: String? = null,
    val label: String = ""
)

@Serializable
data class CoreExerciseJson(
    val id: String,
    val name: String,
    @SerialName("planned_sets") val plannedSets: Int = 3,
    @SerialName("rest_seconds_per_set") val restSecondsPerSet: Int = 45,
    @SerialName("form_description") val formDescription: String = "",
    val notes: String = ""
)

@Serializable
data class GoalsJson(
    @SerialName("water_ml") val waterMl: Int = 3000,
    @SerialName("protein_g") val proteinG: Int = 140,
    @SerialName("carbs_g") val carbsG: Int = 250,
    val calories: Int = 2600
)

@Serializable
data class IncrementsJson(
    @SerialName("water_ml") val waterMl: Int = 250,
    @SerialName("protein_g") val proteinG: Int = 10,
    @SerialName("carbs_g") val carbsG: Int = 10
)

@Serializable
data class RoutineDayJson(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("is_workout_day") val isWorkoutDay: Boolean,
    val name: String = "",
    /** Exercise ids in the order they are performed. */
    val exercises: List<String> = emptyList()
)

@Serializable
data class DailyLogJson(
    /** ISO date, e.g. "2026-08-30". Readable on purpose; converted to epoch day internally. */
    val date: String,
    @SerialName("water_ml") val waterMl: Int = 0,
    @SerialName("protein_g") val proteinG: Int = 0,
    @SerialName("carbs_g") val carbsG: Int = 0,
    val calories: Int = 0,
    @SerialName("is_workout_day") val isWorkoutDay: Boolean = false,
    @SerialName("workout_completed") val workoutCompleted: Boolean = false
)

@Serializable
data class SessionJson(
    val date: String,
    @SerialName("exercise_id") val exerciseId: String,
    val level: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val completed: Boolean = true,
    @SerialName("total_rest_seconds") val totalRestSeconds: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    /** Reps in set order. */
    val sets: List<Int> = emptyList()
)

package com.mreddy.liftz.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/* ------------------------------------------------------------------------------------------
 * ROUTINE DEFINITION  (what you PLAN to do)
 * ---------------------------------------------------------------------------------------- */

/**
 * One exercise in the routine library.
 *
 * Progression state (currentLevelKey / currentWeightKg) lives here because there is exactly one
 * "where I am right now" per exercise. History lives in [SetLogEntity].
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    /** Stable slug, e.g. "pull_up". Used as the JSON export id too, so keep it human readable. */
    @PrimaryKey val id: String,
    val name: String,
    val type: ExerciseType,
    /** Default set type. Individual planned sets may override it (see [PlannedSetEntity]). */
    val setType: SetType,
    val hypertrophyMin: Int = 8,
    val hypertrophyMax: Int = 12,
    /** How many recent sessions the progression engine and time estimator look at. */
    val rollingWindow: Int = 6,
    val plannedSets: Int = 3,
    /** BODYWEIGHT_PROGRESSION only: which level of the ladder is active. */
    val currentLevelKey: String? = null,
    /** WEIGHTED only. */
    val currentWeightKg: Double? = null,
    val weightIncrementKg: Double? = null,
    /** CORE exercises set this false: no advancement logic at all. */
    val progressionTracked: Boolean = true,
    /** Planned rest per set, in seconds. The exercise screen counts down plannedSets * this. */
    val restSecondsPerSet: Int = 90,
    /** Free-text form cue shown in the collapsible description block. */
    val formDescription: String = "",
    val notes: String = "",
    val orderIndex: Int = 0
)

/**
 * One rung of a bodyweight progression ladder, ordered easiest -> hardest.
 *
 * Composite key (exerciseId, levelKey) because PRs are tracked per (exercise, level) PAIR,
 * never globally per exercise.
 */
@Entity(
    tableName = "levels",
    primaryKeys = ["exerciseId", "levelKey"],
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exerciseId")]
)
data class LevelEntity(
    val exerciseId: String,
    /** Slug, e.g. "band_assisted". */
    val levelKey: String,
    /** 0 = easiest. Advancing means orderIndex + 1. */
    val orderIndex: Int,
    val displayName: String
)

/**
 * Per-set planned configuration.
 *
 * This exists so a single exercise can have MIXED sets, e.g. pull-up = 5 sets where sets 1-2 are
 * unassisted to failure and sets 3-5 are band assisted at a fixed rep goal. Uniform exercises just
 * get N identical rows.
 */
@Entity(
    tableName = "planned_sets",
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exerciseId")]
)
data class PlannedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    /** 0-based position within the exercise. */
    val setIndex: Int,
    val setType: SetType,
    /** Only meaningful for FIXED_REP. The number the input box pre-fills with. */
    val goalReps: Int = 0,
    /**
     * Optional override of the exercise's currentLevelKey for THIS set.
     * Pull-up sets 1-2 use the harder "standard" level while 3-5 use "band_assisted".
     * Null means "use the exercise's current level".
     */
    val levelKeyOverride: String? = null,
    /** Shown next to the set row, e.g. "unassisted, to failure". */
    val label: String = ""
)

/** A day of the routine week. dayOfWeek uses java.time.DayOfWeek.value (1 = Monday .. 7 = Sunday). */
@Entity(tableName = "routine_days")
data class RoutineDayEntity(
    @PrimaryKey val dayOfWeek: Int,
    val isWorkoutDay: Boolean,
    val name: String
)

/** Join table: which exercises belong to which routine day, in order. */
@Entity(
    tableName = "routine_day_exercises",
    primaryKeys = ["dayOfWeek", "exerciseId"],
    indices = [Index("exerciseId")]
)
data class RoutineDayExerciseEntity(
    val dayOfWeek: Int,
    val exerciseId: String,
    val orderIndex: Int
)

/* ------------------------------------------------------------------------------------------
 * LOGGED HISTORY  (what you ACTUALLY did)
 * ---------------------------------------------------------------------------------------- */

/**
 * One occurrence of one exercise on one date. This is the unit the rolling window counts:
 * "the last 6 sessions of pull-up at level band_assisted".
 */
@Entity(
    tableName = "exercise_sessions",
    indices = [Index("exerciseId"), Index("epochDay"), Index(value = ["exerciseId", "levelKey"])]
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    /** LocalDate.toEpochDay(). */
    val epochDay: Long,
    /** Snapshot of the level at the time. Null for weighted/core. */
    val levelKey: String? = null,
    /** Snapshot of the weight at the time. Null for bodyweight/core. */
    val weightKg: Double? = null,
    val startedAtMs: Long = 0,
    val finishedAtMs: Long = 0,
    /** Total seconds spent resting across the whole exercise (cumulative, not per set). */
    val totalRestSeconds: Int = 0,
    val completed: Boolean = false
)

/** One logged set. */
@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = ExerciseSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val setType: SetType,
    val loggedAtMs: Long
)

/**
 * One calendar day's macro + workout state.
 *
 * isWorkoutDay is stored, not computed, because the calendar denominator has to be known UPFRONT
 * from the routine plan (5 goals on a workout day, 4 otherwise) rather than inferred afterwards.
 */
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val epochDay: Long,
    val waterMl: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val calories: Int = 0,
    val isWorkoutDay: Boolean = false,
    /** True once every exercise planned for the day has a completed session. */
    val workoutCompleted: Boolean = false
)

/* ------------------------------------------------------------------------------------------
 * SINGLETON CONFIG ROWS
 * ---------------------------------------------------------------------------------------- */

/** Daily macro targets. Single row, id is always 0. */
@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey val id: Int = 0,
    val waterMl: Int = 3000,
    val proteinG: Int = 140,
    val carbsG: Int = 250,
    val calories: Int = 2600
)

/** Per-click increments configured in Settings. Single row, id is always 0. */
@Entity(tableName = "increments")
data class IncrementsEntity(
    @PrimaryKey val id: Int = 0,
    val waterMl: Int = 250,
    val proteinG: Int = 10,
    /** Placeholder default: no carb-tracking history to tune this from yet. */
    val carbsG: Int = 10,
    val calories: Int = 100,
    /** Rep increment is fixed at 1 by design and is NOT editable; kept here for clarity only. */
    val repIncrement: Int = 1
)

/** A pending "you should move up" prompt. The user always confirms before anything switches. */
@Entity(tableName = "progression_suggestions", indices = [Index("exerciseId")])
data class ProgressionSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val kind: SuggestionKind,
    val fromLevelKey: String? = null,
    val toLevelKey: String? = null,
    val fromWeightKg: Double? = null,
    val toWeightKg: Double? = null,
    val createdAtMs: Long,
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    /** Human readable reason, shown in the confirm dialog. */
    val rationale: String = ""
)

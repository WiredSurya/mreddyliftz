package com.mreddy.liftz.data.db

/**
 * What kind of exercise this is. Decides which progression rule applies.
 *
 *  - BODYWEIGHT_PROGRESSION : advances by moving up a named level ladder (pull-up, ring dip...)
 *  - WEIGHTED              : advances by adding weight (standing DB press, single-leg RDL...)
 *  - CORE                  : not progression tracked at all, plain set/rep logging
 */
enum class ExerciseType {
    BODYWEIGHT_PROGRESSION,
    WEIGHTED,
    CORE;

    companion object {
        /** Parse the snake_case value used in the JSON export format. */
        fun fromJson(raw: String): ExerciseType = when (raw.lowercase()) {
            "bodyweight_progression" -> BODYWEIGHT_PROGRESSION
            "weighted" -> WEIGHTED
            else -> CORE
        }
    }

    /** The snake_case value written back out to JSON. */
    fun toJson(): String = name.lowercase()
}

/**
 * How the rep input box behaves when logging a set.
 *
 *  - FIXED_REP  : input pre-fills with the planned goal reps for that set.
 *                 User bumps it with +/- if they got extra reps.
 *  - TO_FAILURE : input pre-fills with whatever was logged LAST TIME THIS EXERCISE
 *                 OCCURRED (not the previous set). The number to beat.
 */
enum class SetType {
    FIXED_REP,
    TO_FAILURE;

    companion object {
        fun fromJson(raw: String): SetType =
            if (raw.equals("TO_FAILURE", true) || raw.equals("to_failure", true)) TO_FAILURE
            else FIXED_REP
    }

    fun toJson(): String = name
}

/** Lifecycle of an exercise inside a workout, Spotify-queue style. */
enum class QueueState { UPCOMING, IN_PROGRESS, COMPLETED }

/** State of an auto-generated progression suggestion awaiting user confirmation. */
enum class SuggestionStatus { PENDING, ACCEPTED, DISMISSED }

/** What kind of change a progression suggestion proposes. */
enum class SuggestionKind { LEVEL_UP, WEIGHT_UP }

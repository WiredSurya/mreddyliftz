package com.mreddy.liftz.data.seed

import com.mreddy.liftz.data.db.ExerciseEntity
import com.mreddy.liftz.domain.MuscleGroup
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.db.GoalsEntity
import com.mreddy.liftz.data.db.IncrementsEntity
import com.mreddy.liftz.data.db.LevelEntity
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.db.PlannedSetEntity
import com.mreddy.liftz.data.db.RoutineDayEntity
import com.mreddy.liftz.data.db.RoutineDayExerciseEntity
import com.mreddy.liftz.data.db.SetType

/**
 * The real starting routine, written into Room the first time the DB is created.
 *
 * Anything here can be replaced wholesale later by importing a JSON export from Settings.
 */
object SeedData {

    /* ---------------- exercise ids (also the JSON export ids) ---------------- */
    const val PULL_UP = "pull_up"
    const val RING_DIP = "ring_dip"
    const val STANDING_DB_PRESS = "standing_db_press"
    const val SINGLE_LEG_RDL = "single_leg_rdl"
    const val NORDIC_CURL_NEGATIVE = "nordic_curl_negative"
    const val PLANK = "plank"
    const val HANGING_KNEE_RAISE = "hanging_knee_raise"
    const val SIDE_PLANK = "side_plank"

    /* ---------------- exercises ---------------- */

    val exercises: List<ExerciseEntity> = listOf(
        ExerciseEntity(
            id = PULL_UP,
            name = "Pull-up",
            type = ExerciseType.BODYWEIGHT_PROGRESSION,
            setType = SetType.FIXED_REP,
            hypertrophyMin = 8,
            hypertrophyMax = 12,
            rollingWindow = 6,
            plannedSets = 5,
            currentLevelKey = "band_assisted",
            restSecondsPerSet = 120,
            formDescription = "Dead hang start, shoulders packed down before the pull. " +
                "Drive elbows to the ribs, chin clears the bar, control the negative for 2s. " +
                "No kipping, no chin craning.",
            notes = "5 sets: 2 unassisted to failure, then 3 band assisted at goal reps.",
            orderIndex = 0,
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK, MuscleGroup.FOREARMS)
        ),
        ExerciseEntity(
            id = RING_DIP,
            name = "Ring dip",
            type = ExerciseType.BODYWEIGHT_PROGRESSION,
            setType = SetType.TO_FAILURE,
            hypertrophyMin = 8,
            hypertrophyMax = 12,
            rollingWindow = 6,
            plannedSets = 3,
            currentLevelKey = "negative",
            restSecondsPerSet = 120,
            formDescription = "Rings turned out at the top, elbows tracking back not flared. " +
                "Lower until shoulders sit just below the elbows. Keep the ribcage down.",
            notes = "",
            orderIndex = 1,
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
        ),
        ExerciseEntity(
            id = STANDING_DB_PRESS,
            name = "Standing DB press",
            type = ExerciseType.WEIGHTED,
            setType = SetType.FIXED_REP,
            hypertrophyMin = 8,
            hypertrophyMax = 12,
            rollingWindow = 6,
            plannedSets = 3,
            currentWeightKg = 10.0,
            weightIncrementKg = 2.0,
            restSecondsPerSet = 90,
            formDescription = "Standing, not seated. Glutes and abs braced so the lower back " +
                "does not arch. Press slightly back so the bells finish over the ears.",
            notes = "",
            orderIndex = 2,
            primaryMuscle = MuscleGroup.SHOULDERS,
            secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.UPPER_BACK, MuscleGroup.ABS)
        ),
        ExerciseEntity(
            id = SINGLE_LEG_RDL,
            name = "Single-leg RDL",
            type = ExerciseType.WEIGHTED,
            setType = SetType.FIXED_REP,
            hypertrophyMin = 8,
            hypertrophyMax = 12,
            rollingWindow = 6,
            plannedSets = 3,
            currentWeightKg = 8.0,
            weightIncrementKg = 2.0,
            restSecondsPerSet = 90,
            formDescription = "Hinge from the hip, back leg and torso forming one line. " +
                "Square the hips (no opening up). Slight bend in the standing knee.",
            notes = "Reps are per side.",
            orderIndex = 3,
            primaryMuscle = MuscleGroup.HAMSTRINGS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK, MuscleGroup.ABS)
        ),
        ExerciseEntity(
            id = NORDIC_CURL_NEGATIVE,
            name = "Nordic curl negative",
            type = ExerciseType.BODYWEIGHT_PROGRESSION,
            setType = SetType.TO_FAILURE,
            hypertrophyMin = 6,
            hypertrophyMax = 10,
            rollingWindow = 6,
            plannedSets = 3,
            currentLevelKey = "partial_rom",
            restSecondsPerSet = 120,
            formDescription = "Hips locked out with the glutes the whole way down, no piking. " +
                "Resist as slowly as possible, catch with the hands, push back up.",
            notes = "",
            orderIndex = 4,
            primaryMuscle = MuscleGroup.HAMSTRINGS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES)
        ),
        // Core: rotating variants, so no progression logic at all. Plain set/rep logging.
        ExerciseEntity(
            id = PLANK,
            name = "Plank",
            type = ExerciseType.CORE,
            setType = SetType.TO_FAILURE,
            rollingWindow = 6,
            plannedSets = 3,
            progressionTracked = false,
            restSecondsPerSet = 45,
            formDescription = "Ribs down, glutes on, neutral neck. Log seconds in the reps field.",
            notes = "Logged as seconds.",
            orderIndex = 5,
            primaryMuscle = MuscleGroup.ABS,
            secondaryMuscles = listOf(MuscleGroup.OBLIQUES, MuscleGroup.SHOULDERS, MuscleGroup.LOWER_BACK)
        ),
        ExerciseEntity(
            id = HANGING_KNEE_RAISE,
            name = "Hanging knee raise",
            type = ExerciseType.CORE,
            setType = SetType.TO_FAILURE,
            rollingWindow = 6,
            plannedSets = 3,
            progressionTracked = false,
            restSecondsPerSet = 45,
            formDescription = "No swing. Posterior pelvic tilt at the top, slow return.",
            orderIndex = 6,
            primaryMuscle = MuscleGroup.ABS,
            secondaryMuscles = listOf(MuscleGroup.OBLIQUES, MuscleGroup.FOREARMS)
        ),
        ExerciseEntity(
            id = SIDE_PLANK,
            name = "Side plank",
            type = ExerciseType.CORE,
            setType = SetType.TO_FAILURE,
            rollingWindow = 6,
            plannedSets = 2,
            progressionTracked = false,
            restSecondsPerSet = 45,
            formDescription = "Stack the feet, hips high, log seconds per side.",
            notes = "Logged as seconds, per side.",
            orderIndex = 7,
            primaryMuscle = MuscleGroup.OBLIQUES,
            secondaryMuscles = listOf(MuscleGroup.ABS, MuscleGroup.SHOULDERS, MuscleGroup.GLUTES)
        )
    )

    /* ---------------- progression ladders (easiest -> hardest) ---------------- */

    val levels: List<LevelEntity> = buildList {
        addAll(ladder(PULL_UP, listOf(
            "dead_hang" to "Dead hang",
            "scapular_pull" to "Scapular pull",
            "negative" to "Negative",
            "band_assisted" to "Band assisted",
            "standard" to "Standard",
            "weighted" to "Weighted",
            "archer" to "Archer"
        )))
        addAll(ladder(RING_DIP, listOf(
            "support_hold" to "Support hold",
            "negative" to "Negative",
            "band_assisted_full" to "Band assisted full",
            "standard" to "Standard",
            "weighted" to "Weighted"
        )))
        addAll(ladder(NORDIC_CURL_NEGATIVE, listOf(
            "partial_rom" to "Partial ROM",
            "full_rom_negative" to "Full ROM negative",
            "assisted_concentric" to "Assisted concentric",
            "full_nordic_curl" to "Full nordic curl"
        )))
    }

    private fun ladder(exerciseId: String, rungs: List<Pair<String, String>>): List<LevelEntity> =
        rungs.mapIndexed { index, (key, display) ->
            LevelEntity(exerciseId = exerciseId, levelKey = key, orderIndex = index, displayName = display)
        }

    /* ---------------- per-set plans ---------------- */

    val plannedSets: List<PlannedSetEntity> = buildList {
        // Pull-up is the mixed one: sets 1-2 unassisted to failure, sets 3-5 band assisted.
        add(PlannedSetEntity(exerciseId = PULL_UP, setIndex = 0, setType = SetType.TO_FAILURE,
            levelKeyOverride = "standard", label = "unassisted, to failure"))
        add(PlannedSetEntity(exerciseId = PULL_UP, setIndex = 1, setType = SetType.TO_FAILURE,
            levelKeyOverride = "standard", label = "unassisted, to failure"))
        repeat(3) { i ->
            add(PlannedSetEntity(exerciseId = PULL_UP, setIndex = 2 + i, setType = SetType.FIXED_REP,
                goalReps = 8, levelKeyOverride = "band_assisted", label = "band assisted"))
        }
        addAll(uniform(RING_DIP, 3, SetType.TO_FAILURE, 0))
        addAll(uniform(STANDING_DB_PRESS, 3, SetType.FIXED_REP, 10))
        addAll(uniform(SINGLE_LEG_RDL, 3, SetType.FIXED_REP, 10, label = "per side"))
        addAll(uniform(NORDIC_CURL_NEGATIVE, 3, SetType.TO_FAILURE, 0))
        addAll(uniform(PLANK, 3, SetType.TO_FAILURE, 0, label = "seconds"))
        addAll(uniform(HANGING_KNEE_RAISE, 3, SetType.TO_FAILURE, 0))
        addAll(uniform(SIDE_PLANK, 2, SetType.TO_FAILURE, 0, label = "seconds, per side"))
    }

    private fun uniform(
        exerciseId: String,
        count: Int,
        setType: SetType,
        goalReps: Int,
        label: String = ""
    ): List<PlannedSetEntity> = (0 until count).map {
        PlannedSetEntity(
            exerciseId = exerciseId,
            setIndex = it,
            setType = setType,
            goalReps = goalReps,
            label = label
        )
    }

    /* ---------------- weekly plan ----------------
     * Full body on Mon / Wed / Fri. Everything else is a rest day, which still counts on the
     * calendar (4 macro goals instead of 5). Edit freely: the calendar denominator reads
     * RoutineDayEntity.isWorkoutDay, so changing this changes the maths automatically.
     */

    /**
     * A blank week. Every day starts as a rest day and becomes a training day the moment an
     * exercise is assigned to it, so the calendar's 4-goal denominator is right from day one
     * without anyone having to configure a split first.
     */
    val routineDays: List<RoutineDayEntity> = listOf(
        RoutineDayEntity(1, false, "Monday"),
        RoutineDayEntity(2, false, "Tuesday"),
        RoutineDayEntity(3, false, "Wednesday"),
        RoutineDayEntity(4, false, "Thursday"),
        RoutineDayEntity(5, false, "Friday"),
        RoutineDayEntity(6, false, "Saturday"),
        RoutineDayEntity(7, false, "Sunday")
    )

    private val workoutOrder = listOf(
        PULL_UP, RING_DIP, STANDING_DB_PRESS, SINGLE_LEG_RDL,
        NORDIC_CURL_NEGATIVE, HANGING_KNEE_RAISE, PLANK
    )

    val routineDayExercises: List<RoutineDayExerciseEntity> =
        listOf(1, 3, 5).flatMap { day ->
            workoutOrder.mapIndexed { index, id ->
                RoutineDayExerciseEntity(dayOfWeek = day, exerciseId = id, orderIndex = index)
            }
        }

    val goals = GoalsEntity(id = 0, waterMl = 3000, proteinG = 140, carbsG = 250, calories = 2600)
    val increments = IncrementsEntity(id = 0, waterMl = 250, proteinG = 10, carbsG = 10)

    /**
     * Called once, from the Room onCreate callback.
     *
     * NEW INSTALLS GET A BLANK SLATE. Only the week skeleton and generic macro defaults are
     * written - no exercises, no levels, no planned sets.
     *
     * The exercise definitions above are kept as a REFERENCE, not as the shipped default. They
     * were one person's real training numbers, and shipping them meant every stranger who
     * installed the app opened it to somebody else's pull-up level and working weights. They stay
     * in the file because they are the worked example of every field the schema supports, and
     * because `mreddyliftz_export_template.json` mirrors them for anyone who wants a starting
     * point - but importing that is now a choice, not the default.
     *
     * A user fills this in one of two ways, both first-class: build it by hand in the app, or
     * hand their goals to an assistant from the Coach screen and import what comes back.
     */
    suspend fun seed(db: LiftzDatabase) {
        db.routineDao().upsertDays(routineDays)
        db.configDao().upsertGoals(goals)
        db.configDao().upsertIncrements(increments)
    }

    /**
     * The reference routine, written on demand rather than at install. Settings offers this as
     * "load the example routine" for anyone who would rather start from something than nothing.
     */
    suspend fun loadExampleRoutine(db: LiftzDatabase) {
        db.exerciseDao().upsertAll(exercises)
        db.levelDao().upsertAll(levels)
        db.plannedSetDao().insertAll(plannedSets)
        db.routineDao().upsertDays(
            listOf(
                RoutineDayEntity(1, true, "Full body A"),
                RoutineDayEntity(2, false, "Rest"),
                RoutineDayEntity(3, true, "Full body B"),
                RoutineDayEntity(4, false, "Rest"),
                RoutineDayEntity(5, true, "Full body C"),
                RoutineDayEntity(6, false, "Rest"),
                RoutineDayEntity(7, false, "Rest")
            )
        )
        db.routineDao().upsertDayExercises(routineDayExercises)
    }
}

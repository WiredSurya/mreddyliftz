package com.mreddy.liftz.data.json

import com.mreddy.liftz.data.db.DailyLogEntity
import com.mreddy.liftz.data.db.ExerciseEntity
import com.mreddy.liftz.data.db.ExerciseSessionEntity
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.db.GoalsEntity
import com.mreddy.liftz.data.db.IncrementsEntity
import com.mreddy.liftz.data.db.LevelEntity
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.db.PlannedSetEntity
import com.mreddy.liftz.data.db.RoutineDayEntity
import com.mreddy.liftz.data.db.RoutineDayExerciseEntity
import com.mreddy.liftz.data.db.SetLogEntity
import com.mreddy.liftz.data.db.SetType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Room <-> JSON.
 *
 * Two modes on import:
 *   OVERWRITE : wipe the routine definition and replace it with the file (history is kept unless
 *               the file carries its own history).
 *   MERGE     : upsert whatever the file contains and leave everything else alone.
 */
object JsonPort {

    enum class ImportMode { OVERWRITE, MERGE }

    // prettyPrintIndent and explicitNulls are still marked experimental in kotlinx.serialization.
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true      // forward compatible: a newer file still imports
        encodeDefaults = true
        explicitNulls = false
    }

    /* ---------------------------------------------------------------------------------------
     * EXPORT
     * ------------------------------------------------------------------------------------- */

    suspend fun export(db: LiftzDatabase, includeHistory: Boolean = true): LiftzExport {
        val allExercises = db.exerciseDao().getAll()
        val routineDays = db.routineDao().getDays()

        val tracked = allExercises.filter { it.type != ExerciseType.CORE }
        val core = allExercises.filter { it.type == ExerciseType.CORE }

        val exercisesJson = tracked.map { e ->
            val levels = db.levelDao().getForExercise(e.id).sortedBy { it.orderIndex }
            val plan = db.plannedSetDao().getForExercise(e.id)
            ExerciseJson(
                id = e.id,
                name = e.name,
                type = e.type.toJson(),
                setType = e.setType.toJson(),
                hypertrophyRange = listOf(e.hypertrophyMin, e.hypertrophyMax),
                rollingWindow = e.rollingWindow,
                plannedSets = e.plannedSets,
                levels = levels.map { it.levelKey },
                currentLevel = e.currentLevelKey,
                currentWeightKg = e.currentWeightKg,
                weightIncrementKg = e.weightIncrementKg,
                setPlan = plan.map { ps ->
                    PlannedSetJson(
                        setIndex = ps.setIndex,
                        setType = ps.setType.toJson(),
                        goalReps = ps.goalReps,
                        levelKeyOverride = ps.levelKeyOverride,
                        label = ps.label
                    )
                },
                restSecondsPerSet = e.restSecondsPerSet,
                formDescription = e.formDescription,
                notes = e.notes
            )
        }

        val coreJson = core.map { e ->
            CoreExerciseJson(
                id = e.id,
                name = e.name,
                plannedSets = e.plannedSets,
                restSecondsPerSet = e.restSecondsPerSet,
                formDescription = e.formDescription,
                notes = e.notes
            )
        }

        val goals = db.configDao().getGoals() ?: GoalsEntity()
        val increments = db.configDao().getIncrements() ?: IncrementsEntity()

        val dayExercises = routineDays.associate { day ->
            day.dayOfWeek to db.exerciseDao().getForDay(day.dayOfWeek).map { it.exercise.id }
        }

        val logs = if (includeHistory) db.dailyLogDao().getAll() else emptyList()
        val sessions = if (includeHistory) {
            logs.flatMap { log ->
                db.sessionDao().getSessionsForDay(log.epochDay).map { s ->
                    SessionJson(
                        date = LocalDate.ofEpochDay(s.session.epochDay).toString(),
                        exerciseId = s.session.exerciseId,
                        level = s.session.levelKey,
                        weightKg = s.session.weightKg,
                        completed = s.session.completed,
                        totalRestSeconds = s.session.totalRestSeconds,
                        durationSeconds =
                            ((s.session.finishedAtMs - s.session.startedAtMs) / 1000L).toInt()
                                .coerceAtLeast(0),
                        sets = s.orderedSets.map { it.reps },
                        // Only written when the session actually mixes rungs; otherwise the
                        // session-level `level` already says everything and this stays absent.
                        setLevels = s.orderedSets
                            .map { it.levelKey ?: s.session.levelKey }
                            .takeIf { levels -> levels.any { it != s.session.levelKey } }
                            ?: emptyList()
                    )
                }
            }
        } else emptyList()

        return LiftzExport(
            schemaVersion = LiftzExport.SCHEMA_VERSION,
            exportedAt = LocalDate.now().toString(),
            exercises = exercisesJson,
            coreExercises = coreJson,
            goals = GoalsJson(goals.waterMl, goals.proteinG, goals.carbsG, goals.calories),
            increments = IncrementsJson(increments.waterMl, increments.proteinG, increments.carbsG),
            routineDays = routineDays.map { day ->
                RoutineDayJson(
                    dayOfWeek = day.dayOfWeek,
                    isWorkoutDay = day.isWorkoutDay,
                    name = day.name,
                    exercises = dayExercises[day.dayOfWeek].orEmpty()
                )
            },
            dailyLogs = logs.map { l ->
                DailyLogJson(
                    date = LocalDate.ofEpochDay(l.epochDay).toString(),
                    waterMl = l.waterMl,
                    proteinG = l.proteinG,
                    carbsG = l.carbsG,
                    calories = l.calories,
                    isWorkoutDay = l.isWorkoutDay,
                    workoutCompleted = l.workoutCompleted
                )
            },
            sessions = sessions
        )
    }

    suspend fun exportToString(db: LiftzDatabase, includeHistory: Boolean = true): String =
        json.encodeToString(LiftzExport.serializer(), export(db, includeHistory))

    /* ---------------------------------------------------------------------------------------
     * IMPORT
     * ------------------------------------------------------------------------------------- */

    fun parse(text: String): LiftzExport = json.decodeFromString(LiftzExport.serializer(), text)

    suspend fun import(db: LiftzDatabase, export: LiftzExport, mode: ImportMode) {
        if (mode == ImportMode.OVERWRITE) {
            // Wipe the routine DEFINITION only. History survives unless the file replaces it.
            db.routineDao().clearDayExercises()
            db.routineDao().clearDays()
            db.plannedSetDao().clear()
            db.levelDao().clear()
            db.exerciseDao().clear()
            db.suggestionDao().clear()
        }

        /* ---- exercises ---- */
        val exerciseRows = mutableListOf<ExerciseEntity>()
        val levelRows = mutableListOf<LevelEntity>()
        val plannedRows = mutableListOf<PlannedSetEntity>()

        export.exercises.forEachIndexed { index, e ->
            val type = ExerciseType.fromJson(e.type)
            val min = e.hypertrophyRange.getOrElse(0) { 8 }
            val max = e.hypertrophyRange.getOrElse(1) { 12 }
            exerciseRows += ExerciseEntity(
                id = e.id,
                name = e.name,
                type = type,
                setType = SetType.fromJson(e.setType),
                hypertrophyMin = min,
                hypertrophyMax = max,
                rollingWindow = e.rollingWindow,
                plannedSets = e.plannedSets,
                currentLevelKey = e.currentLevel,
                currentWeightKg = e.currentWeightKg,
                weightIncrementKg = e.weightIncrementKg,
                progressionTracked = true,
                restSecondsPerSet = e.restSecondsPerSet,
                formDescription = e.formDescription,
                notes = e.notes,
                orderIndex = index
            )
            e.levels.forEachIndexed { levelIndex, key ->
                levelRows += LevelEntity(
                    exerciseId = e.id,
                    levelKey = key,
                    orderIndex = levelIndex,
                    displayName = prettify(key)
                )
            }
            val plan = if (e.setPlan.isNotEmpty()) {
                e.setPlan.map { ps ->
                    PlannedSetEntity(
                        exerciseId = e.id,
                        setIndex = ps.setIndex,
                        setType = SetType.fromJson(ps.setType),
                        goalReps = ps.goalReps,
                        levelKeyOverride = ps.levelKeyOverride,
                        label = ps.label
                    )
                }
            } else {
                // No explicit plan: generate planned_sets identical sets from the exercise default.
                (0 until e.plannedSets).map { i ->
                    PlannedSetEntity(
                        exerciseId = e.id,
                        setIndex = i,
                        setType = SetType.fromJson(e.setType),
                        goalReps = if (SetType.fromJson(e.setType) == SetType.FIXED_REP) min else 0
                    )
                }
            }
            plannedRows += plan
        }

        export.coreExercises.forEachIndexed { index, c ->
            exerciseRows += ExerciseEntity(
                id = c.id,
                name = c.name,
                type = ExerciseType.CORE,
                setType = SetType.TO_FAILURE,
                plannedSets = c.plannedSets,
                progressionTracked = false,
                restSecondsPerSet = c.restSecondsPerSet,
                formDescription = c.formDescription,
                notes = c.notes,
                orderIndex = 100 + index
            )
            plannedRows += (0 until c.plannedSets).map { i ->
                PlannedSetEntity(
                    exerciseId = c.id,
                    setIndex = i,
                    setType = SetType.TO_FAILURE,
                    goalReps = 0
                )
            }
        }

        if (exerciseRows.isNotEmpty()) {
            db.exerciseDao().upsertAll(exerciseRows)
            db.levelDao().upsertAll(levelRows)
            exerciseRows.forEach { db.plannedSetDao().deleteForExercise(it.id) }
            db.plannedSetDao().insertAll(plannedRows)
        }

        /* ---- config ---- */
        db.configDao().upsertGoals(
            GoalsEntity(
                id = 0,
                waterMl = export.goals.waterMl,
                proteinG = export.goals.proteinG,
                carbsG = export.goals.carbsG,
                calories = export.goals.calories
            )
        )
        db.configDao().upsertIncrements(
            IncrementsEntity(
                id = 0,
                waterMl = export.increments.waterMl,
                proteinG = export.increments.proteinG,
                carbsG = export.increments.carbsG
            )
        )

        /* ---- weekly plan ---- */
        if (export.routineDays.isNotEmpty()) {
            db.routineDao().upsertDays(
                export.routineDays.map { RoutineDayEntity(it.dayOfWeek, it.isWorkoutDay, it.name) }
            )
            db.routineDao().upsertDayExercises(
                export.routineDays.flatMap { day ->
                    day.exercises.mapIndexed { i, id ->
                        RoutineDayExerciseEntity(day.dayOfWeek, id, i)
                    }
                }
            )
        }

        /* ---- optional history ---- */
        if (export.dailyLogs.isNotEmpty()) {
            db.dailyLogDao().upsertAll(
                export.dailyLogs.map { l ->
                    DailyLogEntity(
                        epochDay = LocalDate.parse(l.date).toEpochDay(),
                        waterMl = l.waterMl,
                        proteinG = l.proteinG,
                        carbsG = l.carbsG,
                        calories = l.calories,
                        isWorkoutDay = l.isWorkoutDay,
                        workoutCompleted = l.workoutCompleted
                    )
                }
            )
        }
        export.sessions.forEach { s ->
            val epochDay = LocalDate.parse(s.date).toEpochDay()
            if (db.sessionDao().getSession(s.exerciseId, epochDay) != null) return@forEach
            val id = db.sessionDao().insertSession(
                ExerciseSessionEntity(
                    exerciseId = s.exerciseId,
                    epochDay = epochDay,
                    levelKey = s.level,
                    weightKg = s.weightKg,
                    startedAtMs = 0,
                    finishedAtMs = s.durationSeconds * 1000L,
                    totalRestSeconds = s.totalRestSeconds,
                    completed = s.completed
                )
            )
            s.sets.forEachIndexed { i, reps ->
                db.sessionDao().insertSet(
                    SetLogEntity(
                        sessionId = id,
                        setIndex = i,
                        reps = reps,
                        weightKg = s.weightKg,
                        // Per-set rung if the file carries one, else the session's level, which is
                        // what pre-v2 files meant implicitly.
                        levelKey = s.setLevels.getOrNull(i) ?: s.level,
                        setType = SetType.TO_FAILURE,
                        loggedAtMs = 0
                    )
                )
            }
        }
    }

    /** "band_assisted" -> "Band assisted". Used when a file gives levels as bare slugs. */
    private fun prettify(key: String): String {
        val words = key.split('_').filter { it.isNotBlank() }
        if (words.isEmpty()) return key
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}

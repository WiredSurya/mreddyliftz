package com.mreddy.liftz.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Transaction
    @Query("SELECT * FROM exercises ORDER BY orderIndex")
    fun observeAllWithPlan(): Flow<List<ExerciseWithPlan>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeWithPlan(id: String): Flow<ExerciseWithPlan?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getWithPlan(id: String): ExerciseWithPlan?

    @Query("SELECT * FROM exercises ORDER BY orderIndex")
    suspend fun getAll(): List<ExerciseEntity>

    @Transaction
    @Query(
        """
        SELECT e.* FROM exercises e
        INNER JOIN routine_day_exercises rde ON rde.exerciseId = e.id
        WHERE rde.dayOfWeek = :dayOfWeek
        ORDER BY rde.orderIndex
        """
    )
    fun observeForDay(dayOfWeek: Int): Flow<List<ExerciseWithPlan>>

    @Transaction
    @Query(
        """
        SELECT e.* FROM exercises e
        INNER JOIN routine_day_exercises rde ON rde.exerciseId = e.id
        WHERE rde.dayOfWeek = :dayOfWeek
        ORDER BY rde.orderIndex
        """
    )
    suspend fun getForDay(dayOfWeek: Int): List<ExerciseWithPlan>

    @Upsert suspend fun upsert(exercise: ExerciseEntity)
    @Upsert suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercises SET currentLevelKey = :levelKey WHERE id = :exerciseId")
    suspend fun setCurrentLevel(exerciseId: String, levelKey: String)

    @Query("UPDATE exercises SET currentWeightKg = :weightKg WHERE id = :exerciseId")
    suspend fun setCurrentWeight(exerciseId: String, weightKg: Double)

    @Query("UPDATE exercises SET rollingWindow = :window WHERE id = :exerciseId")
    suspend fun setRollingWindow(exerciseId: String, window: Int)

    @Query("DELETE FROM exercises")
    suspend fun clear()
}

@Dao
interface LevelDao {
    @Query("SELECT * FROM levels WHERE exerciseId = :exerciseId ORDER BY orderIndex")
    suspend fun getForExercise(exerciseId: String): List<LevelEntity>

    @Upsert suspend fun upsertAll(levels: List<LevelEntity>)

    @Query("DELETE FROM levels") suspend fun clear()
}

@Dao
interface PlannedSetDao {
    @Query("SELECT * FROM planned_sets WHERE exerciseId = :exerciseId ORDER BY setIndex")
    suspend fun getForExercise(exerciseId: String): List<PlannedSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<PlannedSetEntity>)

    @Query("DELETE FROM planned_sets WHERE exerciseId = :exerciseId")
    suspend fun deleteForExercise(exerciseId: String)

    @Query("DELETE FROM planned_sets") suspend fun clear()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_days ORDER BY dayOfWeek")
    fun observeDays(): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days ORDER BY dayOfWeek")
    suspend fun getDays(): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_days WHERE dayOfWeek = :dayOfWeek")
    suspend fun getDay(dayOfWeek: Int): RoutineDayEntity?

    @Upsert suspend fun upsertDays(days: List<RoutineDayEntity>)
    @Upsert suspend fun upsertDayExercises(rows: List<RoutineDayExerciseEntity>)

    @Query("DELETE FROM routine_days") suspend fun clearDays()
    @Query("DELETE FROM routine_day_exercises") suspend fun clearDayExercises()
}

@Dao
interface SessionDao {

    @Transaction
    @Query("SELECT * FROM exercise_sessions WHERE exerciseId = :exerciseId AND epochDay = :epochDay LIMIT 1")
    suspend fun getSession(exerciseId: String, epochDay: Long): SessionWithSets?

    @Transaction
    @Query("SELECT * FROM exercise_sessions WHERE exerciseId = :exerciseId AND epochDay = :epochDay LIMIT 1")
    fun observeSession(exerciseId: String, epochDay: Long): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM exercise_sessions WHERE epochDay = :epochDay")
    fun observeSessionsForDay(epochDay: Long): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM exercise_sessions WHERE epochDay = :epochDay")
    suspend fun getSessionsForDay(epochDay: Long): List<SessionWithSets>

    /**
     * History for the progression engine.
     *
     * Filtered by levelKey because a PR / baseline belongs to an (exercise, level) PAIR.
     * Dropping to an easier level therefore compares against that level's own history, and the
     * first session at a brand new level becomes its baseline (this query returns nothing yet).
     *
     * Ordered newest first, so `take(rollingWindow)` gives exactly the rolling window.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM exercise_sessions
        WHERE exerciseId = :exerciseId
          AND completed = 1
          AND (:levelKey IS NULL OR levelKey = :levelKey)
        ORDER BY epochDay DESC
        LIMIT :limit
        """
    )
    suspend fun recentCompleted(exerciseId: String, levelKey: String?, limit: Int): List<SessionWithSets>

    /** Same as above but ignores level: used for time estimates, where level does not matter. */
    @Transaction
    @Query(
        """
        SELECT * FROM exercise_sessions
        WHERE exerciseId = :exerciseId AND completed = 1
        ORDER BY epochDay DESC LIMIT :limit
        """
    )
    suspend fun recentCompletedAnyLevel(exerciseId: String, limit: Int): List<SessionWithSets>

    @Insert suspend fun insertSession(session: ExerciseSessionEntity): Long
    @Update suspend fun updateSession(session: ExerciseSessionEntity)
    @Insert suspend fun insertSet(set: SetLogEntity): Long
    @Upsert suspend fun upsertSet(set: SetLogEntity)

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId AND setIndex = :setIndex")
    suspend fun deleteSet(sessionId: Long, setIndex: Int)

    @Query("SELECT COUNT(*) FROM exercise_sessions WHERE epochDay = :epochDay AND completed = 1")
    suspend fun completedCountForDay(epochDay: Long): Int

    @Query("DELETE FROM exercise_sessions") suspend fun clear()
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs")
    suspend fun getAll(): List<DailyLogEntity>

    @Upsert suspend fun upsert(log: DailyLogEntity)
    @Upsert suspend fun upsertAll(logs: List<DailyLogEntity>)

    @Query("DELETE FROM daily_logs") suspend fun clear()
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM goals WHERE id = 0")
    fun observeGoals(): Flow<GoalsEntity?>

    @Query("SELECT * FROM goals WHERE id = 0")
    suspend fun getGoals(): GoalsEntity?

    @Upsert suspend fun upsertGoals(goals: GoalsEntity)

    @Query("SELECT * FROM increments WHERE id = 0")
    fun observeIncrements(): Flow<IncrementsEntity?>

    @Query("SELECT * FROM increments WHERE id = 0")
    suspend fun getIncrements(): IncrementsEntity?

    @Upsert suspend fun upsertIncrements(increments: IncrementsEntity)
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM progression_suggestions WHERE status = 'PENDING' ORDER BY createdAtMs DESC")
    fun observePending(): Flow<List<ProgressionSuggestionEntity>>

    @Query("SELECT * FROM progression_suggestions WHERE exerciseId = :exerciseId AND status = 'PENDING' LIMIT 1")
    suspend fun pendingFor(exerciseId: String): ProgressionSuggestionEntity?

    @Insert suspend fun insert(suggestion: ProgressionSuggestionEntity): Long

    @Query("UPDATE progression_suggestions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SuggestionStatus)

    @Query("DELETE FROM progression_suggestions") suspend fun clear()
}

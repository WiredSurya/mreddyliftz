package com.mreddy.liftz.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mreddy.liftz.data.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExerciseEntity::class,
        LevelEntity::class,
        PlannedSetEntity::class,
        RoutineDayEntity::class,
        RoutineDayExerciseEntity::class,
        ExerciseSessionEntity::class,
        SetLogEntity::class,
        DailyLogEntity::class,
        GoalsEntity::class,
        IncrementsEntity::class,
        ProgressionSuggestionEntity::class
    ],
    // Single source of truth for the version lives in Migrations.SCHEMA_VERSION so that the
    // number here and the migration chain can never drift apart. See Migrations.kt.
    version = Migrations.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LiftzDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun levelDao(): LevelDao
    abstract fun plannedSetDao(): PlannedSetDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun configDao(): ConfigDao
    abstract fun suggestionDao(): SuggestionDao

    companion object {
        @Volatile private var INSTANCE: LiftzDatabase? = null

        fun get(context: Context): LiftzDatabase =
            INSTANCE ?: synchronized(this) { INSTANCE ?: build(context).also { INSTANCE = it } }

        private fun build(context: Context): LiftzDatabase {
            // Seeding runs on a background scope the first time the DB file is created.
            val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val builder = Room.databaseBuilder(
                context.applicationContext,
                LiftzDatabase::class.java,
                "mreddyliftz.db"
            )
                // Every schema change must ship a Migration. There is deliberately NO
                // fallbackToDestructiveMigration() here: this database is the only copy of the
                // training history that exists anywhere, so Room should throw on a missing
                // migration rather than quietly delete it. See Migrations.kt.
                .addMigrations(*Migrations.ALL)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // INSTANCE is assigned by get() before any DAO call can happen, and
                        // onCreate fires lazily on first real DB access, so this is safe.
                        seedScope.launch { INSTANCE?.let { SeedData.seed(it) } }
                    }
                })
            return builder.build()
        }
    }
}

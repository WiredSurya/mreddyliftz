package com.mreddy.liftz.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration history for `mreddyliftz.db`.
 *
 * WHY THIS EXISTS
 * ---------------
 * This is a sideloaded app with no backend and no cloud copy. The Room database on the phone is the
 * only place a workout history exists. If a schema change ever ships without a matching migration,
 * the usual "quick fix" is [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration],
 * which silently deletes every row. That would destroy the training log permanently.
 *
 * So: destructive fallback is deliberately NOT configured in [LiftzDatabase]. With no fallback and
 * no matching migration, Room throws IllegalStateException on open. A crash on launch is loud,
 * recoverable (reinstall, or import the last JSON export) and reversible. A silent wipe is not.
 *
 * HOW TO ADD A MIGRATION
 * ----------------------
 * Any change to an @Entity — new column, renamed column, new table, changed index — needs one.
 *
 *   1. Bump [SCHEMA_VERSION] by exactly 1. [LiftzDatabase] reads its `version` from this constant,
 *      so there is only one number to change and the two cannot drift apart.
 *   2. Write the MIGRATION_N_N+1 object below with the raw SQL.
 *   3. Append it to [ALL]. Order matters; never renumber or edit a migration that has already run
 *      on the phone.
 *   4. Build once. KSP writes `app/schemas/<db>/<version>.json`. COMMIT THAT FILE — it is the
 *      audit trail, and it is what a future migration test diffs against.
 *   5. Sanity-check the new SQL against the previous schema JSON before installing over live data.
 *      Exporting a JSON backup from Settings first costs ten seconds and is the real safety net.
 *
 * Room compares a hash of the expected schema against the file on disk, so a migration that
 * produces even a slightly different table shape (a missing DEFAULT, a dropped index) fails at
 * open. That check is a feature — it catches a bad migration before it corrupts anything.
 *
 * TEMPLATE — the most common case, adding a nullable column:
 *
 *     private val MIGRATION_2_3 = object : Migration(2, 3) {
 *         override fun migrate(db: SupportSQLiteDatabase) {
 *             db.execSQL("ALTER TABLE set_logs ADD COLUMN rpe REAL")
 *         }
 *     }
 *
 * A NOT NULL column needs a DEFAULT so existing rows stay valid:
 *
 *     db.execSQL("ALTER TABLE set_logs ADD COLUMN rpe REAL NOT NULL DEFAULT 0.0")
 *
 * SQLite cannot drop or retype a column in place. For those, do the create-copy-drop-rename dance
 * inside the single migrate() call: create the new table under a temp name, INSERT ... SELECT the
 * old rows across, DROP the old table, ALTER TABLE ... RENAME, then recreate the indices.
 */
object Migrations {

    /**
     * The current schema version. [LiftzDatabase] annotates itself with this exact constant, so
     * bumping it here is the only place a version number ever changes.
     *
     * Invariant, enforced by MigrationsTest: [ALL] holds a contiguous 1 -> 2 -> ... -> N chain
     * ending at this value.
     */
    const val SCHEMA_VERSION = 4

    /**
     * v1 -> v2: `set_logs.levelKey`.
     *
     * Sets used to inherit their rung from the parent session, which silently pooled the pull-up's
     * unassisted "standard" sets into its "band_assisted" history. Nullable with no default, so
     * every pre-existing row keeps NULL and the read path falls back to the session's level —
     * exactly the behaviour those rows were written under. No data is rewritten or lost.
     */
    private val MIGRATION_1_2 = migration(1, 2) { db ->
        db.execSQL("ALTER TABLE set_logs ADD COLUMN levelKey TEXT")
    }

    /**
     * v2 -> v3: fat tracking, and calories that compute themselves.
     *
     * Calories previously had to be typed in by hand, which is the pain this removes. They could
     * not be derived before because fat was not tracked at all, and protein+carbs alone account
     * for only about 60% of a normal calorie intake - deriving from those two would have
     * undercounted badly enough to make the calorie goal unreachable.
     *
     * All columns are NOT NULL with defaults, so existing rows stay valid and existing logged
     * days keep every number they already had. Days logged before this migration simply have
     * fatG = 0 until they are edited.
     */
    private val MIGRATION_2_3 = migration(2, 3) { db ->
        db.execSQL("ALTER TABLE daily_logs ADD COLUMN fatG INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE goals ADD COLUMN fatG INTEGER NOT NULL DEFAULT 115")
        db.execSQL("ALTER TABLE goals ADD COLUMN autoCalcCalories INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE increments ADD COLUMN fatG INTEGER NOT NULL DEFAULT 5")
    }

    /**
     * Per-set stopwatch timing.
     *
     * DEFAULT 0 means "not timed", which is exactly what every set logged before this version
     * was. Nothing that reads these columns may treat 0 as "a set that took no time" — a zero is
     * an absence of data, and the timing stats skip those rows rather than averaging them in and
     * quietly reporting that every old workout was infinitely fast.
     */
    private val MIGRATION_3_4 = migration(3, 4) { db ->
        db.execSQL("ALTER TABLE set_logs ADD COLUMN startedAtMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE set_logs ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
    }

    /**
     * Every migration, in ascending order. Append, never rewrite.
     *
     * NOTE: this must stay the LAST declaration in the object. Kotlin initialises an object's
     * properties top to bottom, so a migration referenced here before its own `val` has run would
     * be silently null at runtime.
     */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
    )
}

/**
 * Convenience for writing a migration as a lambda instead of an object expression:
 *
 *     private val MIGRATION_2_3 = migration(2, 3) { db ->
 *         db.execSQL("ALTER TABLE set_logs ADD COLUMN rpe REAL")
 *     }
 */
fun migration(from: Int, to: Int, body: (SupportSQLiteDatabase) -> Unit): Migration =
    object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) = body(db)
    }

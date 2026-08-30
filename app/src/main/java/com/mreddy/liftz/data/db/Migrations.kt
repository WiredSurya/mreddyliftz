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
 *     val MIGRATION_1_2 = object : Migration(1, 2) {
 *         override fun migrate(db: SupportSQLiteDatabase) {
 *             db.execSQL("ALTER TABLE set_logs ADD COLUMN weightKg REAL")
 *         }
 *     }
 *
 * A NOT NULL column needs a DEFAULT so existing rows stay valid:
 *
 *     db.execSQL("ALTER TABLE set_logs ADD COLUMN weightKg REAL NOT NULL DEFAULT 0.0")
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
    const val SCHEMA_VERSION = 1

    /**
     * Every migration, in ascending order. Empty at version 1 — there is nothing to migrate from
     * yet — and that is the correct state, not an omission. Append, never rewrite.
     */
    val ALL: Array<Migration> = arrayOf(
        // MIGRATION_1_2,
    )
}

/**
 * Convenience for writing a migration as a lambda instead of an object expression:
 *
 *     val MIGRATION_1_2 = migration(1, 2) { db ->
 *         db.execSQL("ALTER TABLE set_logs ADD COLUMN weightKg REAL")
 *     }
 */
fun migration(from: Int, to: Int, body: (SupportSQLiteDatabase) -> Unit): Migration =
    object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) = body(db)
    }

package com.mreddy.liftz

import com.mreddy.liftz.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the migration scaffold itself.
 *
 * These do not open a database — that needs a device, and there is none. They check the thing that
 * actually goes wrong in practice: the @Database version gets bumped and the matching Migration is
 * forgotten, or a migration is inserted out of order leaving a gap in the chain. Room only catches
 * that at runtime on a real phone holding real data, which is far too late.
 */
class MigrationsTest {

    @Test
    fun `schema version is at least 1`() {
        assertTrue(
            "Schema version must be a positive integer, was ${Migrations.SCHEMA_VERSION}",
            Migrations.SCHEMA_VERSION >= 1
        )
    }

    @Test
    fun `migration chain is contiguous and has no gaps`() {
        var expectedStart = 1
        Migrations.ALL.forEach { m ->
            assertEquals(
                "Migration chain has a gap: expected one starting at $expectedStart but found " +
                    "${m.startVersion} -> ${m.endVersion}. Migrations must run 1->2->3->...",
                expectedStart,
                m.startVersion
            )
            assertEquals(
                "Migration ${m.startVersion} -> ${m.endVersion} skips a version. Each migration " +
                    "must step exactly one version at a time.",
                expectedStart + 1,
                m.endVersion
            )
            expectedStart = m.endVersion
        }
    }

    @Test
    fun `migration chain ends at the declared schema version`() {
        val reachable = if (Migrations.ALL.isEmpty()) 1 else Migrations.ALL.last().endVersion
        assertEquals(
            "SCHEMA_VERSION is ${Migrations.SCHEMA_VERSION} but the migration chain only reaches " +
                "version $reachable. Bumping the version without appending a Migration means a " +
                "phone holding real data cannot upgrade — Room will throw on open. Add the " +
                "missing MIGRATION_${reachable}_${reachable + 1} to Migrations.ALL.",
            Migrations.SCHEMA_VERSION,
            reachable
        )
    }

    @Test
    fun `no duplicate migrations for the same version step`() {
        val steps = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(
            "Duplicate migration steps found in Migrations.ALL: $steps",
            steps.size,
            steps.toSet().size
        )
    }
}

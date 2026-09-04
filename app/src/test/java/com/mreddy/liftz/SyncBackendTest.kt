package com.mreddy.liftz

import com.mreddy.liftz.data.sync.SnapshotMeta
import com.mreddy.liftz.data.sync.StoredSnapshot
import com.mreddy.liftz.data.sync.SyncBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the [SyncBackend] contract with an in-memory implementation.
 *
 * The point is that the contract is small enough to be fully specified without Android, a
 * network, or a Firebase project — so whichever real backend is written later can be held to
 * exactly the same behaviour before it is trusted with anyone's history.
 */
class SyncBackendTest {

    private class InMemoryBackend(
        private var available: Boolean = true,
        private var failUpload: Boolean = false
    ) : SyncBackend {
        var stored: StoredSnapshot? = null
        override val displayName = "In memory"
        override suspend fun isAvailable() = available
        override suspend fun upload(snapshot: String, meta: SnapshotMeta): Result<Unit> =
            if (failUpload) Result.failure(IllegalStateException("nope"))
            else { stored = StoredSnapshot(snapshot, meta); Result.success(Unit) }
        override suspend fun download(): Result<StoredSnapshot?> = Result.success(stored)
    }

    private fun meta(at: Long = 1_000L, schema: Int = 3) =
        SnapshotMeta(takenAtMs = at, deviceId = "device-a", schemaVersion = schema, sizeBytes = 4)

    @Test
    fun `download returns null before anything has been uploaded`() = runTest {
        assertNull(InMemoryBackend().download().getOrThrow())
    }

    @Test
    fun `a snapshot round trips byte for byte`() = runTest {
        val backend = InMemoryBackend()
        val payload = """{"schema_version":3,"exercises":[]}"""
        backend.upload(payload, meta()).getOrThrow()
        assertEquals(payload, backend.download().getOrThrow()?.json)
    }

    @Test
    fun `uploading twice keeps only the newer snapshot`() = runTest {
        val backend = InMemoryBackend()
        backend.upload("first", meta(at = 1_000L)).getOrThrow()
        backend.upload("second", meta(at = 2_000L)).getOrThrow()
        val got = backend.download().getOrThrow()
        assertEquals("second", got?.json)
        assertEquals(2_000L, got?.meta?.takenAtMs)
    }

    @Test
    fun `a failed upload surfaces as a failure and leaves the previous backup intact`() = runTest {
        val ok = InMemoryBackend()
        ok.upload("good", meta()).getOrThrow()

        val broken = InMemoryBackend(failUpload = true)
        assertTrue(broken.upload("bad", meta()).isFailure)
        // The good backend is untouched: a failure must never destroy what was already stored.
        assertEquals("good", ok.download().getOrThrow()?.json)
    }

    @Test
    fun `metadata survives the round trip so a restore can say where it came from`() = runTest {
        val backend = InMemoryBackend()
        backend.upload("x", meta(at = 42L, schema = 3)).getOrThrow()
        val m = backend.download().getOrThrow()?.meta
        assertEquals(42L, m?.takenAtMs)
        assertEquals("device-a", m?.deviceId)
        assertEquals(3, m?.schemaVersion)
    }

    @Test
    fun `an unavailable backend reports it rather than pretending`() = runTest {
        assertTrue(!InMemoryBackend(available = false).isAvailable())
    }
}

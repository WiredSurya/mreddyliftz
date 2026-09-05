package com.mreddy.liftz

import com.mreddy.liftz.data.update.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `a higher patch is newer`() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
    }

    /*
     * The regression this exists for. A string comparison puts "1.10.0" BEFORE "1.9.0", so every
     * user would silently stop being offered updates the moment the minor version hit double
     * digits — and it would look like the update check had simply stopped working.
     */
    @Test
    fun `ten beats nine rather than sorting as a string`() {
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
    }

    @Test
    fun `a leading v has already been stripped but extra segments still compare`() {
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertTrue(isNewerVersion("1.1", "1.0.9"))
        assertFalse(isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun `suffixes are ignored rather than crashing the check`() {
        assertTrue(isNewerVersion("1.2.0-beta", "1.1.0"))
        assertFalse(isNewerVersion("garbage", "1.0.0"))
    }
}

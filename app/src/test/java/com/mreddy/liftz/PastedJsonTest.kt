package com.mreddy.liftz

import com.mreddy.liftz.data.json.PastedJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedJsonTest {

    private fun ok(raw: String) = PastedJson.extract(raw) as PastedJson.Result.Ok
    private fun bad(raw: String) = PastedJson.extract(raw) as PastedJson.Result.Problem

    @Test
    fun `bare json passes straight through`() {
        assertEquals("""{"a":1}""", ok("""{"a":1}""").json)
    }

    @Test
    fun `a fenced json block is unwrapped`() {
        val reply = """
            Sure! Here is your updated routine:

            ```json
            {"schema_version":3,"exercises":[]}
            ```

            Let me know if you want it harder.
        """.trimIndent()
        assertEquals("""{"schema_version":3,"exercises":[]}""", ok(reply).json)
    }

    @Test
    fun `a fence with no language tag also works`() {
        assertEquals("""{"a":1}""", ok("blah\n```\n{\"a\":1}\n```\nmore").json)
    }

    @Test
    fun `prose on both sides without a fence still yields the object`() {
        assertEquals("""{"a":1}""", ok("Here you go: {\"a\":1} — enjoy").json)
    }

    @Test
    fun `braces inside strings do not end the object early`() {
        // A brace in an exercise name must not truncate the document.
        val src = """{"name":"curl {weird}","b":2}"""
        assertEquals(src, ok("text $src text").json)
    }

    @Test
    fun `escaped quotes do not confuse the string tracking`() {
        val src = """{"name":"say \"hi\" {x}","b":2}"""
        assertEquals(src, ok(src).json)
    }

    @Test
    fun `nested objects return the whole outer object`() {
        val src = """{"a":{"b":{"c":1}},"d":2}"""
        assertEquals(src, ok(src).json)
    }

    @Test
    fun `empty paste is reported clearly`() {
        assertTrue(bad("   ").reason.contains("Nothing pasted", ignoreCase = true))
    }

    @Test
    fun `prose with no json at all is reported clearly`() {
        assertTrue(bad("I think you should squat more.").reason.contains("Could not find"))
    }

    @Test
    fun `a reply cut off mid block fails rather than importing a fragment`() {
        // Unbalanced: better a clear error than half a routine written over the real one.
        assertTrue(bad("""{"a":1,"b":{"c":2}""").reason.isNotBlank())
    }

    @Test
    fun `a json array is rejected because the schema is an object`() {
        assertTrue(bad("""[1,2,3]""").reason.isNotBlank())
    }
}

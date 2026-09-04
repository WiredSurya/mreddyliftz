package com.mreddy.liftz.data.json

/**
 * Pulls a usable JSON document out of text a person pasted in.
 *
 * This exists because of how the coach hand-off is actually used. Free LLM tiers generally will
 * not hand you a downloadable file — you get an answer with a fenced code block in the middle of
 * it. So what lands on the clipboard is almost never bare JSON: it is prose, then ```json, then
 * the document, then more prose. Requiring a clean file would make the round trip unusable for
 * exactly the people the bring-your-own-LLM flow was built for.
 *
 * Pure string handling, no Android, no serialization — so every case below is unit-tested.
 */
object PastedJson {

    sealed interface Result {
        data class Ok(val json: String) : Result
        data class Problem(val reason: String) : Result
    }

    /**
     * Best-effort extraction. In order:
     *  1. a fenced block (```json ... ``` or plain ``` ... ```)
     *  2. otherwise, the outermost balanced {...} span, ignoring braces inside strings
     *
     * Deliberately does NOT try to repair malformed JSON. Guessing at broken structure risks
     * importing something subtly wrong into the only copy of someone's training history; a clear
     * failure they can act on is far better than a silent half-import.
     */
    fun extract(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Problem("Nothing pasted yet.")

        val fenced = fencedBlock(text)
        val candidate = fenced ?: outermostObject(text)
            ?: return Result.Problem(
                "Could not find a JSON object in that. Paste the whole reply — the ```json " +
                    "block is found automatically — or just the part starting with { and " +
                    "ending with }."
            )

        if (!candidate.trimStart().startsWith("{")) {
            return Result.Problem("That looks like a fragment, not a whole file. It has to start with {.")
        }
        return Result.Ok(candidate)
    }

    /** Contents of the first fenced code block, if there is one. */
    private fun fencedBlock(text: String): String? {
        val open = Regex("```[a-zA-Z]*\\s*").find(text) ?: return null
        val afterOpen = open.range.last + 1
        val close = text.indexOf("```", startIndex = afterOpen)
        val body = if (close == -1) text.substring(afterOpen) else text.substring(afterOpen, close)
        return body.trim().ifEmpty { null }
    }

    /**
     * The span from the first `{` to its matching `}`.
     *
     * String-aware: a brace inside "..." must not change the depth, or a routine whose exercise
     * name happens to contain a brace would truncate the document at the wrong place.
     */
    private fun outermostObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null   // unbalanced: almost always a reply that got cut off mid-block
    }
}

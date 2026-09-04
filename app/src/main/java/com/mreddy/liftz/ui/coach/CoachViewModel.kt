package com.mreddy.liftz.ui.coach

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.data.json.PastedJson
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.Coach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoachUiState(
    val loading: Boolean = true,
    val insights: List<Coach.Insight> = emptyList(),
    val message: String? = null
)

class CoachViewModel(
    private val repo: LiftzRepository,
    private val db: LiftzDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        val s = repo.stats()
        _state.value = CoachUiState(
            loading = false,
            insights = Coach.insights(
                Coach.Input(
                    trackedDays = s.trackedDays,
                    workoutsPlanned = s.workoutsPlanned,
                    workoutsCompleted = s.workoutsCompleted,
                    currentStreak = s.currentStreak,
                    longestStreak = s.longestStreak,
                    crownDays = s.crownDays,
                    avgProteinG = s.avgProteinG,
                    goalProteinG = s.goals.proteinG,
                    avgWaterMl = s.avgWaterMl,
                    goalWaterMl = s.goals.waterMl,
                    avgCalories = s.avgCalories,
                    goalCalories = s.goals.calories,
                    exercises = s.exercises.map {
                        Coach.ExerciseState(
                            name = it.name,
                            levelLabel = it.levelLabel,
                            sessions = it.sessions,
                            qualifyingStreak = it.qualifyingStreak,
                            windowNeeded = it.windowNeeded,
                            readyToAdvance = it.readyToAdvance,
                            atTopOfLadder = it.atTopOfLadder,
                            personalRecord = it.personalRecord,
                            lastReps = it.lastReps
                        )
                    }
                )
            )
        )
    }

    /**
     * Writes an export plus a ready-to-paste briefing to a file the user picks. The briefing is
     * plain text at the top of the same file, so there is one thing to hand an LLM rather than a
     * file and a separately-remembered prompt.
     */
    fun exportForLlm(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        runCatching {
            val json = JsonPort.exportToString(db, includeHistory = true)
            val text = buildString {
                appendLine(LLM_BRIEFING)
                appendLine()
                appendLine("--- mreddyLiftz export below this line ---")
                appendLine()
                append(json)
            }
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                    ?: error("Could not open the file for writing")
            }
        }.onSuccess { _state.value = _state.value.copy(message = "Saved. Hand this to any LLM.") }
            .onFailure { _state.value = _state.value.copy(message = "Export failed: ${it.message}") }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    /**
     * Import from text pasted out of an LLM reply.
     *
     * The whole reply can be pasted — prose, fences and all — because [PastedJson] digs the
     * document out. This exists because free tiers hand back a code block, not a file, so
     * requiring a clean download would have made the coach round trip unusable for most people.
     */
    fun importPasted(raw: String, mode: JsonPort.ImportMode) = viewModelScope.launch {
        when (val extracted = PastedJson.extract(raw)) {
            is PastedJson.Result.Problem ->
                _state.value = _state.value.copy(message = extracted.reason)

            is PastedJson.Result.Ok -> runCatching {
                val parsed = JsonPort.parse(extracted.json)
                JsonPort.import(db, parsed, mode)
            }.onSuccess {
                reload()
                _state.value = _state.value.copy(
                    message = if (mode == JsonPort.ImportMode.OVERWRITE)
                        "Routine replaced from the pasted plan."
                    else "Pasted plan merged into your routine."
                )
            }.onFailure {
                // Surface the parser's own complaint: "missing field 'exercises'" tells the user
                // far more about what the model got wrong than a generic failure would.
                _state.value = _state.value.copy(
                    message = "That JSON did not fit the schema: ${it.message}"
                )
            }
        }
    }

    companion object {
        /**
         * The instructions that ride along with the export. Written so the model knows the
         * schema, respects the app's own rules, and returns something importable rather than
         * prose — the round trip only works if what comes back is valid against the same schema.
         */
        const val LLM_BRIEFING = """You are acting as my strength coach. Attached is a full export
from mreddyLiftz, a workout and macro tracker. It is JSON, and the "_readme" block inside it
documents every field.

What I want from you:
1. Read my actual logged history, not just the routine definition.
2. Tell me what is working, what has stalled, and why.
3. Suggest changes to my routine, then give the result back to me as a COMPLETE JSON file in the
   exact same schema, which I will import straight back into the app.

Rules you must follow when you write the JSON back:
- Keep "schema_version" as it is, and keep every top-level key that was present.
- Levels are ordered easiest-first. Progression means moving one step, never skipping.
- Personal records are tracked per (exercise, level) pair, so changing a level resets the
  comparison to that level's own history. Do not "helpfully" flatten this.
- "hypertrophy_range" is [min, max]. Hitting max on EVERY set for "rolling_window" consecutive
  sessions is what earns a level up.
- Rep increments are always 1. Do not change that.
- Do not invent history. Only edit the routine definition, never past "sessions" or "daily_logs".

Answer in plain language first, then give the JSON in one block at the end."""
    }
}

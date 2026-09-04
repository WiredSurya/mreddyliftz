package com.mreddy.liftz.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Editor state. Mirrors [LiftzRepository.ExerciseDraft] but with everything as loose UI values,
 * so a half-filled form is always representable and nothing has to be valid until Save.
 */
data class EditorState(
    val loading: Boolean = true,
    val existingId: String? = null,
    val name: String = "",
    val type: ExerciseType = ExerciseType.BODYWEIGHT_PROGRESSION,
    val setType: SetType = SetType.FIXED_REP,
    val plannedSets: Int = 3,
    val goalReps: Int = 8,
    val hypertrophyMin: Int = 8,
    val hypertrophyMax: Int = 12,
    val rollingWindow: Int = 6,
    val restSecondsPerSet: Int = 90,
    val levelNames: List<String> = listOf("Easier variation", "Standard"),
    val currentLevelIndex: Int = 0,
    val currentWeightKg: String = "10",
    val weightIncrementKg: String = "2",
    val formDescription: String = "",
    val daysOfWeek: Set<Int> = emptySet(),
    val saved: Boolean = false
) {
    /**
     * Why Save is disabled, or null when it is fine. Returning the REASON rather than a boolean
     * means the screen can say what is missing instead of just greying the button out.
     */
    val problem: String?
        get() = when {
            name.isBlank() -> "Give it a name"
            plannedSets < 1 -> "At least one set"
            hypertrophyMax < hypertrophyMin -> "Rep range is backwards"
            type == ExerciseType.BODYWEIGHT_PROGRESSION && levelNames.none { it.isNotBlank() } ->
                "Add at least one level, easiest first"
            type == ExerciseType.WEIGHTED && currentWeightKg.toDoubleOrNull() == null ->
                "Starting weight has to be a number"
            type == ExerciseType.WEIGHTED && weightIncrementKg.toDoubleOrNull() == null ->
                "Weight step has to be a number"
            daysOfWeek.isEmpty() -> "Pick at least one day of the week"
            else -> null
        }
}

class ExerciseEditorViewModel(
    private val repo: LiftzRepository,
    private val exerciseId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (exerciseId == null) {
            _state.value = _state.value.copy(loading = false)
        } else {
            viewModelScope.launch { load(exerciseId) }
        }
    }

    private suspend fun load(id: String) {
        val plan = repo.exerciseWithPlan(id)
        if (plan == null) { _state.value = _state.value.copy(loading = false); return }
        val e = plan.exercise
        val levels = plan.orderedLevels
        _state.value = EditorState(
            loading = false,
            existingId = e.id,
            name = e.name,
            type = e.type,
            setType = e.setType,
            plannedSets = e.plannedSets,
            goalReps = plan.orderedPlannedSets.firstOrNull()?.goalReps ?: 8,
            hypertrophyMin = e.hypertrophyMin,
            hypertrophyMax = e.hypertrophyMax,
            rollingWindow = e.rollingWindow,
            restSecondsPerSet = e.restSecondsPerSet,
            levelNames = levels.map { it.displayName }.ifEmpty { listOf("Standard") },
            currentLevelIndex = levels.indexOfFirst { it.levelKey == e.currentLevelKey }
                .coerceAtLeast(0),
            currentWeightKg = (e.currentWeightKg ?: 10.0).trimZeros(),
            weightIncrementKg = (e.weightIncrementKg ?: 2.0).trimZeros(),
            formDescription = e.formDescription,
            daysOfWeek = repo.daysForExercise(id)
        )
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun toggleDay(day: Int) = update { s ->
        s.copy(daysOfWeek = if (day in s.daysOfWeek) s.daysOfWeek - day else s.daysOfWeek + day)
    }

    fun addLevel() = update { it.copy(levelNames = it.levelNames + "") }

    fun setLevel(index: Int, value: String) = update { s ->
        s.copy(levelNames = s.levelNames.toMutableList().also { it[index] = value })
    }

    fun removeLevel(index: Int) = update { s ->
        val next = s.levelNames.toMutableList().also { it.removeAt(index) }
        s.copy(
            levelNames = next.ifEmpty { listOf("Standard") },
            currentLevelIndex = s.currentLevelIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
        )
    }

    fun save() = viewModelScope.launch {
        val s = _state.value
        if (s.problem != null) return@launch
        repo.saveExercise(
            LiftzRepository.ExerciseDraft(
                existingId = s.existingId,
                name = s.name,
                type = s.type,
                setType = s.setType,
                plannedSets = s.plannedSets,
                goalReps = s.goalReps,
                hypertrophyMin = s.hypertrophyMin,
                hypertrophyMax = s.hypertrophyMax,
                rollingWindow = s.rollingWindow,
                restSecondsPerSet = s.restSecondsPerSet,
                levelNames = s.levelNames.filter { it.isNotBlank() },
                currentLevelIndex = s.currentLevelIndex,
                currentWeightKg = s.currentWeightKg.toDoubleOrNull(),
                weightIncrementKg = s.weightIncrementKg.toDoubleOrNull(),
                formDescription = s.formDescription,
                daysOfWeek = s.daysOfWeek
            )
        )
        _state.value = _state.value.copy(saved = true)
    }

    fun delete() = viewModelScope.launch {
        _state.value.existingId?.let { repo.deleteExercise(it) }
        _state.value = _state.value.copy(saved = true)
    }
}

private fun Double.trimZeros(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

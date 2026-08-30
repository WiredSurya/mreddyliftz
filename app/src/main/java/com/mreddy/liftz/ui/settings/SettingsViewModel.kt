package com.mreddy.liftz.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.ExerciseWithPlan
import com.mreddy.liftz.data.db.GoalsEntity
import com.mreddy.liftz.data.db.IncrementsEntity
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val goals: GoalsEntity = GoalsEntity(),
    val increments: IncrementsEntity = IncrementsEntity(),
    val exercises: List<ExerciseWithPlan> = emptyList()
)

class SettingsViewModel(
    private val repo: LiftzRepository,
    private val db: LiftzDatabase
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repo.observeGoals(),
        repo.observeIncrements(),
        db.exerciseDao().observeAllWithPlan()
    ) { goals, increments, exercises ->
        SettingsUiState(goals, increments, exercises)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Transient banner text after an import/export, cleared by the UI. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun saveGoals(goals: GoalsEntity) = viewModelScope.launch { repo.saveGoals(goals) }

    fun saveIncrements(increments: IncrementsEntity) =
        viewModelScope.launch { repo.saveIncrements(increments) }

    fun setRollingWindow(exerciseId: String, window: Int) =
        viewModelScope.launch { repo.setRollingWindow(exerciseId, window) }

    /* -------------------------------- JSON import / export -------------------------------- */

    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = JsonPort.exportToString(db, includeHistory = true)
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                    ?: error("Could not open the file for writing")
            }
        }.onSuccess { _message.value = "Exported" }
            .onFailure { _message.value = "Export failed: ${it.message}" }
    }

    fun importFrom(resolver: ContentResolver, uri: Uri, mode: JsonPort.ImportMode) =
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open the file for reading")
                }
                val parsed = JsonPort.parse(text)
                JsonPort.import(db, parsed, mode)
                parsed
            }.onSuccess {
                _message.value =
                    "Imported ${it.exercises.size} exercises and ${it.coreExercises.size} core moves"
            }.onFailure { _message.value = "Import failed: ${it.message}" }
        }
}

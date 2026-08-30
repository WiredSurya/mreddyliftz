package com.mreddy.liftz.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.db.ProgressionSuggestionEntity
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SummaryUiState(
    val loading: Boolean = true,
    val summary: LiftzRepository.DaySummary? = null
)

/**
 * Backs the post-workout summary. Deliberately a one-shot load rather than a Flow: the summary is
 * a snapshot of a finished day, so it should not shuffle under the reader. Acting on a progression
 * prompt reloads it explicitly.
 */
class SummaryViewModel(
    private val repo: LiftzRepository,
    private val date: LocalDate
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        _state.value = SummaryUiState(loading = false, summary = repo.daySummary(date))
    }

    fun acceptSuggestion(s: ProgressionSuggestionEntity) = viewModelScope.launch {
        repo.acceptSuggestion(s)
        reload()
    }

    fun dismissSuggestion(s: ProgressionSuggestionEntity) = viewModelScope.launch {
        repo.dismissSuggestion(s)
        reload()
    }
}

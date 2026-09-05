package com.mreddy.liftz.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val stats: LiftzRepository.Stats? = null,
    val insights: LiftzRepository.TrainingInsights? = null
)

/** One-shot load, like the summary screen: a stats page is a snapshot, not a live feed. */
class StatsViewModel(private val repo: LiftzRepository) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        _state.value = StatsUiState(
            loading = false,
            stats = repo.stats(),
            insights = repo.insights()
        )
    }
}

package com.mreddy.liftz.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val stats: LiftzRepository.Stats? = null,
    val week: LiftzRepository.MuscleWeek? = null
)

class ProfileViewModel(private val repo: LiftzRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        // Cheap, idempotent, and it means the body map is never wrong because of a build someone
        // happened to install between a schema bump and its backfill.
        repo.tagKnownExerciseMuscles()
        _state.value = ProfileUiState(
            loading = false,
            stats = repo.stats(),
            week = repo.muscleWeek()
        )
    }
}

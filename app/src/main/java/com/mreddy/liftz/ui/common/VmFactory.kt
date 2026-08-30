package com.mreddy.liftz.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Tiny hand-rolled ViewModel factory. Avoids pulling in Hilt/Koin for a solo hobby app.
 *
 * Usage: `viewModel(factory = factoryOf { WorkoutViewModel(repo, date) })`
 */
@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> factoryOf(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

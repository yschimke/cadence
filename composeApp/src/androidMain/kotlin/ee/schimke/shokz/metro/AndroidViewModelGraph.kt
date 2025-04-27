// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package ee.schimke.shokz.metro

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Extends
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

@DependencyGraph(ViewModelScope::class)
interface AndroidViewModelGraph: ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Extends appGraph: AndroidAppGraph,
            @Provides creationExtras: CreationExtras,
        ): AndroidViewModelGraph
    }
}
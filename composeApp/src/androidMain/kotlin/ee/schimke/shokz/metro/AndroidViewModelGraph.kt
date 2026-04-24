// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package ee.schimke.shokz.metro

import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(ViewModelScope::class)
interface AndroidViewModelGraph: ViewModelGraph {
    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides creationExtras: CreationExtras): AndroidViewModelGraph
    }
}

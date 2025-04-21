@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.shokz.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.Browser
import ee.schimke.shokz.metro.ViewModelCreator
import ee.schimke.shokz.metro.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi

class BrowserViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val route = savedStateHandle.toRoute<Browser>()
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey(BrowserViewModel::class)
@Inject
class BrowserViewModelCreator(
) : ViewModelCreator {
    override fun create(extras: CreationExtras): BrowserViewModel =
        BrowserViewModel(
            extras.createSavedStateHandle(),
        )
}


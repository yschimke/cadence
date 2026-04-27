@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.cadence.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.Browser
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(BrowserViewModel::class)
@Inject
class BrowserViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
  val route = savedStateHandle.toRoute<Browser>()
}

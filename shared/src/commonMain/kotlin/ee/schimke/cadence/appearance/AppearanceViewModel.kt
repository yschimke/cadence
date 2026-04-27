package ee.schimke.cadence.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.data.AppearanceRepo
import ee.schimke.cadence.datastore.proto.ThemeMode
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(AppearanceViewModel::class)
@Inject
class AppearanceViewModel(private val repo: AppearanceRepo) : ViewModel() {

  val themeMode: StateFlow<ThemeMode> =
    repo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repo.setThemeMode(mode) }
  }
}

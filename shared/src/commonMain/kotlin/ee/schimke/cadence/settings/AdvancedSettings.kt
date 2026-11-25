package ee.schimke.cadence.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import ee.schimke.cadence.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Options most people leave alone, under Advanced in Settings and kept in the settings proto.
 * [headphoneControls] offers the headphones' Bluetooth audio controls on Home.
 */
class AdvancedSettings(
  val headphoneControls: Boolean,
  val setHeadphoneControls: (Boolean) -> Unit,
)

/** Everything off, as in previews; [ee.schimke.cadence.App] provides the stored choices. */
val LocalAdvancedSettings = compositionLocalOf {
  AdvancedSettings(headphoneControls = false, setHeadphoneControls = {})
}

@Inject
class AdvancedSettingsRepo(private val store: SettingsStore) {
  val headphoneControls: Flow<Boolean> =
    store.data.map { it.headphone_controls }.distinctUntilChanged()

  suspend fun setHeadphoneControls(enabled: Boolean) {
    store.updateData { it.copy(headphone_controls = enabled) }
  }
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey(AdvancedSettingsViewModel::class)
@Inject
class AdvancedSettingsViewModel(private val repo: AdvancedSettingsRepo) : ViewModel() {
  val headphoneControls: StateFlow<Boolean> =
    repo.headphoneControls.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  fun setHeadphoneControls(enabled: Boolean) {
    viewModelScope.launch { repo.setHeadphoneControls(enabled) }
  }
}

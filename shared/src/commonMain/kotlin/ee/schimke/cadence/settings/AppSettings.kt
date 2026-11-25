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
 * The choices made in Settings, kept in the settings proto. [headphoneControls] (under Advanced)
 * offers the headphones' Bluetooth audio controls on Home; [cardDesign] is the status card design
 * Home draws, a design id from the Livery catalog, or empty for the swimming pool.
 */
class AppSettings(
  val headphoneControls: Boolean,
  val setHeadphoneControls: (Boolean) -> Unit,
  val cardDesign: String = "",
  val setCardDesign: (String) -> Unit = {},
)

/** The defaults, as in previews; [ee.schimke.cadence.App] provides the stored choices. */
val LocalAppSettings = compositionLocalOf {
  AppSettings(headphoneControls = false, setHeadphoneControls = {})
}

@Inject
class AppSettingsRepo(private val store: SettingsStore) {
  val headphoneControls: Flow<Boolean> =
    store.data.map { it.headphone_controls }.distinctUntilChanged()

  val cardDesign: Flow<String> = store.data.map { it.card_design }.distinctUntilChanged()

  suspend fun setHeadphoneControls(enabled: Boolean) {
    store.updateData { it.copy(headphone_controls = enabled) }
  }

  suspend fun setCardDesign(id: String) {
    store.updateData { it.copy(card_design = id) }
  }
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey(AppSettingsViewModel::class)
@Inject
class AppSettingsViewModel(private val repo: AppSettingsRepo) : ViewModel() {
  val headphoneControls: StateFlow<Boolean> =
    repo.headphoneControls.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val cardDesign: StateFlow<String> =
    repo.cardDesign.stateIn(viewModelScope, SharingStarted.Eagerly, "")

  fun setHeadphoneControls(enabled: Boolean) {
    viewModelScope.launch { repo.setHeadphoneControls(enabled) }
  }

  fun setCardDesign(id: String) {
    viewModelScope.launch { repo.setCardDesign(id) }
  }
}

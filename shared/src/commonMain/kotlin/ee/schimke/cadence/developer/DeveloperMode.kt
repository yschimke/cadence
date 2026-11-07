package ee.schimke.cadence.developer

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.data.SettingsStore
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether this install shows the developer options: sync profiles, NFS shares, USB auto-sync,
 * diagnostics and raw device commands. Unlocked by tapping the version in Manage sources, and kept
 * in the settings proto so it survives restarts.
 */
class DeveloperMode(val enabled: Boolean, val setEnabled: (Boolean) -> Unit)

/** Off by default, as in previews; [ee.schimke.cadence.App] provides the stored choice. */
val LocalDeveloperMode = compositionLocalOf { DeveloperMode(enabled = false, setEnabled = {}) }

@Inject
class DeveloperModeRepo(private val store: SettingsStore) {
  val enabled: Flow<Boolean> = store.data.map { it.developer_mode }.distinctUntilChanged()

  suspend fun setEnabled(enabled: Boolean) {
    store.updateData { it.copy(developer_mode = enabled) }
  }
}

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(DeveloperModeViewModel::class)
@Inject
class DeveloperModeViewModel(private val repo: DeveloperModeRepo) : ViewModel() {
  val enabled: StateFlow<Boolean> =
    repo.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  fun setEnabled(enabled: Boolean) {
    viewModelScope.launch { repo.setEnabled(enabled) }
  }
}

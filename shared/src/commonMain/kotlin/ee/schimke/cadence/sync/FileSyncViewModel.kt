package ee.schimke.cadence.sync

import androidx.appstate.transform.transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.NetworkConstraint
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(FileSyncViewModel::class)
@Inject
class FileSyncViewModel(
  private val syncRepo: SyncRepo,
  private val devicesRepo: DevicesRepo,
  private val orchestrator: SyncOrchestrator,
  private val refreshOrchestrator: RefreshOrchestrator,
  private val refreshScheduler: RefreshScheduler,
  private val sourceSuggestions: SourceSuggestionsProvider,
) : ViewModel() {

  private val _suggestions = MutableStateFlow<List<SourceSuggestion>>(emptyList())
  val suggestions: StateFlow<List<SourceSuggestion>> = _suggestions.asStateFlow()

  // Built once, outside the transform: collectAsState keys on the Flow instance, so a flow created
  // inside the composable would be re-subscribed on every recomposition (e.g. each sync progress
  // tick).
  private val devices = devicesRepo.devices.map { it.devices }

  val state: State<UiState> =
    // Main, like the stateIn it replaced: transform defaults to Dispatchers.Default, which put the
    // first loaded frame on a background thread and left the screen blank until it landed.
    transform(
      initialValue = UiState(),
      scope = viewModelScope,
      dispatcher = Dispatchers.Main.immediate,
    ) {
      fileSyncUiState(
        sources = syncRepo.sources,
        profiles = syncRepo.profiles,
        devices = devices,
        preferences = syncRepo.preferences,
        refresh = refreshOrchestrator.progress,
        sync = orchestrator.progress,
      )
    }

  fun addLocalDirectory(name: String, treeUri: String) {
    viewModelScope.launch { syncRepo.addLocalDirectorySource(name, treeUri) }
  }

  /** One-tap first-run path: adds the folder to a Library profile and stages it. */
  fun addFolderToLibrary(name: String, treeUri: String) {
    viewModelScope.launch { refreshScheduler.runNow(syncRepo.addFolderToLibrary(name, treeUri)) }
  }

  fun addNfsShare(name: String, host: String, exportPath: String) {
    viewModelScope.launch { syncRepo.addNfsSource(name, host, exportPath) }
  }

  fun removeSource(id: String) {
    viewModelScope.launch { syncRepo.removeSource(id) }
  }

  fun addProfile(
    name: String,
    sourceIds: List<String>,
    refreshIntervalMinutes: Int,
    networkConstraint: NetworkConstraint,
  ) {
    viewModelScope.launch {
      val profile = syncRepo.addProfile(name, sourceIds, refreshIntervalMinutes, networkConstraint)
      // Run an initial refresh so the profile is materialised on disk.
      refreshScheduler.runNow(profile.id)
    }
  }

  fun deleteProfile(id: String) {
    viewModelScope.launch {
      refreshScheduler.cancel(id)
      syncRepo.removeProfile(id)
    }
  }

  fun toggleAutoRefresh(profile: SyncProfile, enabled: Boolean) {
    viewModelScope.launch { syncRepo.setAutoRefresh(profile.id, enabled) }
  }

  fun refreshNow(profileId: String) {
    refreshScheduler.runNow(profileId)
  }

  fun selectTargetDevice(deviceId: String) {
    viewModelScope.launch { syncRepo.updatePreferences { it.copy(target_device_id = deviceId) } }
  }

  fun setAutoSync(enabled: Boolean) {
    viewModelScope.launch { syncRepo.updatePreferences { it.copy(auto_sync_on_usb = enabled) } }
  }

  fun setUsbMatch(value: String) {
    viewModelScope.launch { syncRepo.updatePreferences { it.copy(usb_match = value) } }
  }

  fun startSync() {
    viewModelScope.launch { orchestrator.startSync(null) }
  }

  fun cancelSync() = orchestrator.cancel()

  fun loadSuggestions() {
    viewModelScope.launch { _suggestions.value = sourceSuggestions.list() }
  }

  fun openSuggestion(suggestion: SourceSuggestion) {
    sourceSuggestions.open(suggestion)
  }

  data class UiState(
    val sources: List<SyncSource> = emptyList(),
    val profiles: List<SyncProfile> = emptyList(),
    val devices: List<Device> = emptyList(),
    val preferences: SyncPreferences = SyncPreferences(),
    val refresh: RefreshProgress = RefreshProgress(),
    val sync: SyncProgress = SyncProgress(),
  )
}

/**
 * Presenter for [FileSyncViewModel.UiState], run headlessly by `transform`. Each upstream flow is
 * collected as Compose state, so the returned value is recomputed whenever any of them emits.
 */
@Composable
internal fun fileSyncUiState(
  sources: Flow<List<SyncSource>>,
  profiles: Flow<List<SyncProfile>>,
  devices: Flow<List<Device>>,
  preferences: Flow<SyncPreferences?>,
  refresh: Flow<RefreshProgress>,
  sync: Flow<SyncProgress>,
): FileSyncViewModel.UiState {
  val sourceList by sources.collectAsState(emptyList())
  val profileList by profiles.collectAsState(emptyList())
  val deviceList by devices.collectAsState(emptyList())
  val prefs by preferences.collectAsState(null)
  val refreshProgress by refresh.collectAsState(RefreshProgress())
  val syncProgress by sync.collectAsState(SyncProgress())
  return FileSyncViewModel.UiState(
    sources = sourceList,
    profiles = profileList,
    devices = deviceList,
    preferences = prefs ?: SyncPreferences(),
    refresh = refreshProgress,
    sync = syncProgress,
  )
}

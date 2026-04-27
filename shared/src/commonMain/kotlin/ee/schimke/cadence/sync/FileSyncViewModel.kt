package ee.schimke.cadence.sync

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

  private val baseFlow =
    combine(
      syncRepo.sources,
      syncRepo.profiles,
      devicesRepo.devices.map { it.devices },
      syncRepo.preferences,
    ) { sources, profiles, devices, prefs ->
      BasePart(sources, profiles, devices, prefs ?: SyncPreferences())
    }

  val state: StateFlow<UiState> =
    baseFlow
      .combine(refreshOrchestrator.progress) { base, refresh -> base to refresh }
      .combine(orchestrator.progress) { (base, refresh), syncProgress ->
        UiState(
          sources = base.sources,
          profiles = base.profiles,
          devices = base.devices,
          preferences = base.preferences,
          refresh = refresh,
          sync = syncProgress,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

  fun addLocalDirectory(name: String, treeUri: String) {
    viewModelScope.launch { syncRepo.addLocalDirectorySource(name, treeUri) }
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

  private data class BasePart(
    val sources: List<SyncSource>,
    val profiles: List<SyncProfile>,
    val devices: List<Device>,
    val preferences: SyncPreferences,
  )

  data class UiState(
    val sources: List<SyncSource> = emptyList(),
    val profiles: List<SyncProfile> = emptyList(),
    val devices: List<Device> = emptyList(),
    val preferences: SyncPreferences = SyncPreferences(),
    val refresh: RefreshProgress = RefreshProgress(),
    val sync: SyncProgress = SyncProgress(),
  )
}

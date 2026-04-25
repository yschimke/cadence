package ee.schimke.shokz.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.datastore.proto.StagedFile
import ee.schimke.shokz.datastore.proto.StagedStatus
import ee.schimke.shokz.datastore.proto.SyncPreferences
import ee.schimke.shokz.datastore.proto.SyncSource
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.metro.ViewModelScope
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
    private val sourceBrowser: SourceBrowser,
    private val orchestrator: SyncOrchestrator,
    private val sourceSuggestions: SourceSuggestionsProvider,
) : ViewModel() {

    private val _suggestions = MutableStateFlow<List<SourceSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SourceSuggestion>> = _suggestions.asStateFlow()

    val state: StateFlow<UiState> = combine(
        syncRepo.sources,
        syncRepo.stagedFiles,
        devicesRepo.devices.map { it.devices },
        syncRepo.preferences,
        orchestrator.progress,
    ) { sources, staged, devices, prefs, progress ->
        UiState(
            sources = sources,
            stagedFiles = staged,
            devices = devices,
            preferences = prefs ?: SyncPreferences(),
            progress = progress,
        )
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun addLocalDirectory(name: String, treeUri: String) {
        viewModelScope.launch { syncRepo.addLocalDirectorySource(name, treeUri) }
    }

    fun addNfsShare(name: String, host: String, exportPath: String) {
        viewModelScope.launch { syncRepo.addNfsSource(name, host, exportPath) }
    }

    fun removeSource(id: String) {
        viewModelScope.launch { syncRepo.removeSource(id) }
    }

    fun stageAllFromSource(sourceId: String) {
        viewModelScope.launch {
            val source = syncRepo.getSource(sourceId) ?: return@launch
            val items = sourceBrowser.browse(sourceId)
            val toStage = items.map { item ->
                StagedFile(
                    id = syncRepo.newStagedId(),
                    source_id = source.id,
                    source_uri = item.sourceUri,
                    display_name = item.displayName,
                    target_relative_path = item.relativePath,
                    size_bytes = item.sizeBytes,
                    status = StagedStatus.PENDING,
                )
            }
            if (toStage.isNotEmpty()) syncRepo.stageFiles(toStage)
        }
    }

    fun removeStaged(id: String) {
        viewModelScope.launch { syncRepo.removeStaged(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { syncRepo.clearCompleted() }
    }

    fun retryFailures() {
        viewModelScope.launch { syncRepo.resetAllToPending() }
    }

    fun selectTargetDevice(deviceId: String) {
        viewModelScope.launch {
            syncRepo.updatePreferences { it.copy(target_device_id = deviceId) }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            syncRepo.updatePreferences { it.copy(auto_sync_on_usb = enabled) }
        }
    }

    fun setUsbMatch(value: String) {
        viewModelScope.launch {
            syncRepo.updatePreferences { it.copy(usb_match = value) }
        }
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
        val stagedFiles: List<StagedFile> = emptyList(),
        val devices: List<Device> = emptyList(),
        val preferences: SyncPreferences = SyncPreferences(),
        val progress: SyncProgress = SyncProgress(),
    )
}

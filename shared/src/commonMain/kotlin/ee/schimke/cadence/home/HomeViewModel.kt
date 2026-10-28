package ee.schimke.cadence.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import ee.schimke.cadence.sync.BrowsedItem
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshProgress
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SourceBrowser
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.SourceSuggestionsProvider
import ee.schimke.cadence.sync.SyncRepo
import ee.schimke.cadence.usb.UsbDevice
import ee.schimke.cadence.usb.UsbManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
  private val syncRepo: SyncRepo,
  private val refreshScheduler: RefreshScheduler,
  refreshOrchestrator: RefreshOrchestrator,
  private val browser: SourceBrowser,
  private val suggestionsProvider: SourceSuggestionsProvider,
  bluetoothController: BluetoothController,
  usbManager: UsbManager,
) : ViewModel() {

  private val contents = MutableStateFlow<Map<String, SourceContents>>(emptyMap())
  private val _suggestions = MutableStateFlow<List<SourceSuggestion>>(emptyList())
  /** Apps that can provide files, for the "Files from an app" option. Empty off Android. */
  val suggestions: StateFlow<List<SourceSuggestion>> = _suggestions.asStateFlow()

  private val library =
    combine(syncRepo.profiles, syncRepo.sources, syncRepo.preferences, contents) {
      profiles,
      sources,
      prefs,
      contents ->
      Library(profiles, sources, prefs ?: SyncPreferences(), contents)
    }

  val state: StateFlow<UiState> =
    combine(
        library,
        refreshOrchestrator.progress,
        bluetoothController.state,
        usbManager.listDevices(),
      ) { library, refresh, bt, usbDevices ->
        UiState(
          profiles = library.profiles,
          sources = library.sources,
          preferences = library.preferences,
          contents = library.contents,
          refresh = refresh,
          bluetooth = bt,
          attachedDevice = usbDevices.firstMatchOrNull(library.preferences.usb_match),
          loaded = true,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

  init {
    // List each source when it's added; a refresh can change the files, so list again after one.
    viewModelScope.launch {
      syncRepo.sources
        .map { sources -> sources.map { it.id } }
        .distinctUntilChanged()
        .collect { ids ->
          contents.value = contents.value.filterKeys { it in ids }
          ids.filter { it !in contents.value }.forEach { list(it) }
        }
    }
    viewModelScope.launch {
      refreshOrchestrator.progress
        .map { it.running }
        .distinctUntilChanged()
        .collect { running -> if (!running) contents.value.keys.forEach { list(it) } }
    }
    viewModelScope.launch { _suggestions.value = suggestionsProvider.list() }
  }

  private fun list(sourceId: String) {
    viewModelScope.launch {
      contents.value +=
        sourceId to (contents.value[sourceId] ?: SourceContents()).copy(loading = true)
      contents.value +=
        sourceId to
          try {
            SourceContents(items = browser.browse(sourceId))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            SourceContents(error = "Can't read this folder. Add it again to restore access.")
          }
    }
  }

  fun addFolder(name: String, location: String) {
    viewModelScope.launch {
      // Stage it right away so it's ready for the next copy to the headphones.
      refreshScheduler.runNow(syncRepo.addFolderToLibrary(name, location))
    }
  }

  fun removeSource(id: String) {
    viewModelScope.launch { syncRepo.removeSource(id) }
  }

  fun refreshNow(profileId: String) {
    refreshScheduler.runNow(profileId)
  }

  fun openSuggestion(suggestion: SourceSuggestion) {
    suggestionsProvider.open(suggestion)
  }

  private data class Library(
    val profiles: List<SyncProfile>,
    val sources: List<SyncSource>,
    val preferences: SyncPreferences,
    val contents: Map<String, SourceContents>,
  )

  data class SourceContents(
    val items: List<BrowsedItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
  )

  data class UiState(
    val profiles: List<SyncProfile> = emptyList(),
    val sources: List<SyncSource> = emptyList(),
    val preferences: SyncPreferences = SyncPreferences(),
    val contents: Map<String, SourceContents> = emptyMap(),
    val refresh: RefreshProgress = RefreshProgress(),
    val bluetooth: BluetoothState = BluetoothState(),
    val attachedDevice: UsbDevice? = null,
    // False until saved sources have been read, so an existing library never flashes as empty.
    val loaded: Boolean = false,
  ) {
    /** The profile that stages [source] for the headphones, if any. */
    fun profileFor(source: SyncSource): SyncProfile? = profiles.firstOrNull {
      source.id in it.source_ids
    }
  }
}

private fun List<UsbDevice>.firstMatchOrNull(match: String): UsbDevice? {
  if (isEmpty()) return null
  if (match.isBlank()) return firstOrNull()
  val target = match.trim().uppercase()
  return firstOrNull { device -> device.vidPidHex().equals(target, ignoreCase = true) }
}

private fun UsbDevice.vidPidHex(): String = "${vendorId.toHex4()}:${productId.toHex4()}"

private fun Int.toHex4(): String = (this and 0xFFFF).toString(16).uppercase().padStart(4, '0')

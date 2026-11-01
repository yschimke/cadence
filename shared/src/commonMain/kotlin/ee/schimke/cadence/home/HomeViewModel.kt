package ee.schimke.cadence.home

import androidx.appstate.transform.transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.UseAppStateTransform
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.WorkingMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
  private val refreshOrchestrator: RefreshOrchestrator,
  private val browser: SourceBrowser,
  private val suggestionsProvider: SourceSuggestionsProvider,
  private val bluetoothController: BluetoothController,
  usbManager: UsbManager,
) : ViewModel() {

  private val contents = MutableStateFlow<Map<String, SourceContents>>(emptyMap())
  private val _suggestions = MutableStateFlow<List<SourceSuggestion>>(emptyList())
  /** Apps that can provide files, for the "Files from an app" option. Empty off Android. */
  val suggestions: StateFlow<List<SourceSuggestion>> = _suggestions.asStateFlow()

  // Called once, outside the transform: collectAsState keys on the Flow instance, and on Android
  // listDevices() builds a new callbackFlow (and USB receiver) per call.
  private val usbDevices = usbManager.listDevices()

  private val transformState: State<UiState>? =
    if (UseAppStateTransform) {
      // Main, like the stateIn it replaced: transform defaults to Dispatchers.Default.
      transform(
        initialValue = UiState(),
        scope = viewModelScope,
        dispatcher = Dispatchers.Main.immediate,
      ) {
        homeUiState(
          profiles = syncRepo.profiles,
          sources = syncRepo.sources,
          preferences = syncRepo.preferences,
          contents = contents.collectAsState().value,
          refresh = refreshOrchestrator.progress,
          bluetooth = bluetoothController.state,
          bluetoothSupported = bluetoothController.supported,
          usbDevices = usbDevices,
        )
      }
    } else {
      null
    }

  private val legacyState: StateFlow<UiState> by lazy {
    val library =
      combine(syncRepo.profiles, syncRepo.sources, syncRepo.preferences, contents) {
        profiles,
        sources,
        prefs,
        contents ->
        Library(profiles, sources, prefs ?: SyncPreferences(), contents)
      }
    combine(
        library,
        refreshOrchestrator.progress,
        bluetoothController.state,
        usbDevices,
      ) { library, refresh, bt, usbDevices ->
        UiState(
          profiles = library.profiles,
          sources = library.sources,
          preferences = library.preferences,
          contents = library.contents,
          refresh = refresh,
          bluetooth = bt,
          bluetoothSupported = bluetoothController.supported,
          attachedDevice = usbDevices.firstMatchOrNull(library.preferences.usb_match),
          loaded = true,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
  }

  /** The screen state, from the path [UseAppStateTransform] selects. */
  @Composable fun collectState(): State<UiState> = transformState ?: legacyState.collectAsState()

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

  fun previousTrack() = bluetoothController.previous()

  fun playPause() = bluetoothController.playPause()

  suspend fun switchToBluetoothAudio(): Boolean =
    bluetoothController.setWorkingMode(WorkingMode.Bluetooth) ==
      "OpenSwim Pro switched to Bluetooth Mode"

  fun nextTrack() = bluetoothController.next()

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
    val bluetoothSupported: Boolean = false,
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

/**
 * Presenter for [HomeViewModel.UiState], run headlessly by `transform`.
 * [HomeViewModel.UiState.loaded] stays false until the saved profiles and sources have been read.
 */
@Composable
internal fun homeUiState(
  profiles: Flow<List<SyncProfile>>,
  sources: Flow<List<SyncSource>>,
  preferences: Flow<SyncPreferences?>,
  contents: Map<String, HomeViewModel.SourceContents>,
  refresh: Flow<RefreshProgress>,
  bluetooth: Flow<BluetoothState>,
  bluetoothSupported: Boolean,
  usbDevices: Flow<List<UsbDevice>>,
): HomeViewModel.UiState {
  val profileList by profiles.collectAsState(null)
  val sourceList by sources.collectAsState(null)
  val prefs by preferences.collectAsState(null)
  val refreshProgress by refresh.collectAsState(RefreshProgress())
  val bluetoothState by bluetooth.collectAsState(BluetoothState())
  val devices by usbDevices.collectAsState(emptyList())
  val resolvedPrefs = prefs ?: SyncPreferences()
  return HomeViewModel.UiState(
    profiles = profileList.orEmpty(),
    sources = sourceList.orEmpty(),
    preferences = resolvedPrefs,
    contents = contents,
    refresh = refreshProgress,
    bluetooth = bluetoothState,
    bluetoothSupported = bluetoothSupported,
    attachedDevice = devices.firstMatchOrNull(resolvedPrefs.usb_match),
    loaded = profileList != null && sourceList != null,
  )
}

private fun List<UsbDevice>.firstMatchOrNull(match: String): UsbDevice? {
  if (isEmpty()) return null
  if (match.isBlank()) return firstOrNull()
  val target = match.trim().uppercase()
  return firstOrNull { device -> device.vidPidHex().equals(target, ignoreCase = true) }
}

private fun UsbDevice.vidPidHex(): String = "${vendorId.toHex4()}:${productId.toHex4()}"

private fun Int.toHex4(): String = (this and 0xFFFF).toString(16).uppercase().padStart(4, '0')

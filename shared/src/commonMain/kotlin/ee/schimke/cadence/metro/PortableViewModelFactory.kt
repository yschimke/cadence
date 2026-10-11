package ee.schimke.cadence.metro

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import ee.schimke.cadence.appearance.AppearanceViewModel
import ee.schimke.cadence.bluetooth.*
import ee.schimke.cadence.bookmarks.BookmarksViewModel
import ee.schimke.cadence.browser.BrowserViewModel
import ee.schimke.cadence.data.*
import ee.schimke.cadence.devices.DevicesViewModel
import ee.schimke.cadence.files.DeviceFilesViewModel
import ee.schimke.cadence.home.HomeViewModel
import ee.schimke.cadence.sync.*
import ee.schimke.cadence.usb.UsbManager
import kotlin.reflect.KClass
import okio.FileSystem

val LocalPortableFactory =
  staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("Cadence services were not installed")
  }

class PortableViewModelFactory(
  store: SettingsStore,
  private val usb: UsbManager,
  private val bluetooth: BluetoothController,
  private val fileSystem: FileSystem?,
  private val storage: StorageManager,
  private val refresh: RefreshOrchestrator,
  private val sync: SyncOrchestrator,
  private val scheduler: RefreshScheduler,
  private val suggestions: SourceSuggestionsProvider,
) : ViewModelProvider.Factory {
  private val devices = DevicesRepo(store)
  private val repo = SyncRepo(store)
  private val appearance = AppearanceRepo(store)
  val themeMode = appearance.themeMode

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
    when (modelClass) {
      HomeViewModel::class -> HomeViewModel(repo, scheduler, bluetooth, usb)
      DevicesViewModel::class -> DevicesViewModel(devices, usb)
      DeviceFilesViewModel::class ->
        DeviceFilesViewModel(extras.createSavedStateHandle(), devices, fileSystem, storage)
      BrowserViewModel::class -> BrowserViewModel(extras.createSavedStateHandle())
      BookmarksViewModel::class -> BookmarksViewModel()
      BluetoothControlsViewModel::class -> BluetoothControlsViewModel(bluetooth)
      AppearanceViewModel::class -> AppearanceViewModel(appearance)
      FileSyncViewModel::class ->
        FileSyncViewModel(repo, devices, sync, refresh, scheduler, suggestions)
      else -> error("Unknown ViewModel: $modelClass")
    }
      as T
}

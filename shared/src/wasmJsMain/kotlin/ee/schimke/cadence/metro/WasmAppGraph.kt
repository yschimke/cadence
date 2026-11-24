package ee.schimke.cadence.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.UnavailableBluetoothController
import ee.schimke.cadence.data.BrowserSettingsStore
import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.data.SettingsStore
import ee.schimke.cadence.data.StorageManager
import ee.schimke.cadence.data.Volume
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.sync.BrowserDirectoryBackend
import ee.schimke.cadence.sync.PortableRefresh
import ee.schimke.cadence.sync.PortableSourceBrowser
import ee.schimke.cadence.sync.PortableSync
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SourceBrowser
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.SourceSuggestionsProvider
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncRepo
import ee.schimke.cadence.usb.UsbManager
import ee.schimke.cadence.usb.WebUsbManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path

@DependencyGraph(AppScope::class)
interface WasmAppGraph : ViewModelGraph {

  @Provides
  @SingleIn(AppScope::class)
  fun provideSettingsStore(): SettingsStore = BrowserSettingsStore()

  @Provides
  @SingleIn(AppScope::class)
  fun provideBackend(): BrowserDirectoryBackend = BrowserDirectoryBackend()

  @Provides
  @SingleIn(AppScope::class)
  fun provideRefreshOrchestrator(
    repo: SyncRepo,
    backend: BrowserDirectoryBackend,
  ): RefreshOrchestrator = PortableRefresh(repo, backend)

  @Provides
  @SingleIn(AppScope::class)
  fun provideSyncOrchestrator(
    repo: SyncRepo,
    devicesRepo: DevicesRepo,
    backend: BrowserDirectoryBackend,
    scope: CoroutineScope,
  ): SyncOrchestrator = PortableSync(repo, devicesRepo, backend, scope)

  @Provides
  @SingleIn(AppScope::class)
  fun provideUsbManager(webUsbManager: WebUsbManager): UsbManager = webUsbManager.manager

  @Provides
  @SingleIn(AppScope::class)
  fun provideBluetoothController(): BluetoothController = UnavailableBluetoothController()

  @Provides fun provideOptionalFileSystem(): FileSystem? = null

  @Provides
  @SingleIn(AppScope::class)
  fun provideStorageManager(): StorageManager =
    object : StorageManager {
      override fun getVolume(path: Path) =
        Volume(
          description = "Browser directory handle (reselect if permission expires)",
          path = path,
        )
    }

  @Provides
  @SingleIn(AppScope::class)
  fun provideRefreshScheduler(
    scope: CoroutineScope,
    refresh: RefreshOrchestrator,
  ): RefreshScheduler =
    object : RefreshScheduler {
      override fun schedule(profile: SyncProfile) {}

      override fun cancel(profileId: String) {}

      override fun runNow(profileId: String) {
        scope.launch {
          try {
            refresh.refresh(profileId)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {}
        }
      }
    }

  @Provides
  @SingleIn(AppScope::class)
  fun provideSourceSuggestionsProvider(): SourceSuggestionsProvider =
    object : SourceSuggestionsProvider {
      override suspend fun list() = emptyList<SourceSuggestion>()

      override fun open(suggestion: SourceSuggestion) {}
    }

  @Provides
  @SingleIn(AppScope::class)
  fun provideSourceBrowser(repo: SyncRepo, backend: BrowserDirectoryBackend): SourceBrowser =
    PortableSourceBrowser(repo, backend)

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(
      @Provides scope: CoroutineScope,
      @Provides webUsbManager: WebUsbManager,
    ): WasmAppGraph
  }
}

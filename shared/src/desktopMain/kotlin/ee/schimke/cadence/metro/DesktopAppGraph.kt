package ee.schimke.cadence.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.UnavailableBluetoothController
import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.data.SettingsStore
import ee.schimke.cadence.data.StorageManager
import ee.schimke.cadence.data.Volume
import ee.schimke.cadence.data.createFilesDataStore
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.sync.DesktopDirectoryBackend
import ee.schimke.cadence.sync.FileManifestStore
import ee.schimke.cadence.sync.GoogleDriveManifestStore
import ee.schimke.cadence.sync.HostId
import ee.schimke.cadence.sync.ManifestStore
import ee.schimke.cadence.sync.PortableRefresh
import ee.schimke.cadence.sync.PortableSourceBrowser
import ee.schimke.cadence.sync.PortableSync
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SourceBrowser
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.SourceSuggestionsProvider
import ee.schimke.cadence.sync.SwitchingManifestStore
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncRepo
import ee.schimke.cadence.sync.google.DesktopGoogleOAuthFlow
import ee.schimke.cadence.sync.google.GoogleAccountRepo
import ee.schimke.cadence.sync.google.GoogleAccountStore
import ee.schimke.cadence.sync.google.GoogleOAuthConfigLoader
import ee.schimke.cadence.sync.google.GoogleSyncController
import ee.schimke.cadence.sync.google.OAuthConfig
import ee.schimke.cadence.sync.google.PortableGoogleSyncController
import ee.schimke.cadence.usb.DesktopUsbManager
import ee.schimke.cadence.usb.UsbManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

data class BundledDesktopOAuth(val config: OAuthConfig?)

@DependencyGraph(AppScope::class)
interface DesktopAppGraph : ViewModelGraph {
  val syncRepo: SyncRepo
  val refreshScheduler: RefreshScheduler
  val usbManager: UsbManager
  val syncOrchestrator: SyncOrchestrator

  @Provides
  @SingleIn(AppScope::class)
  fun provideRootDir(): Path =
    (System.getProperty("user.home") + "/.cadence").toPath().also {
      FileSystem.SYSTEM.createDirectories(it)
    }

  @Provides
  @SingleIn(AppScope::class)
  fun provideSettingsStore(root: Path): SettingsStore = createFilesDataStore {
    root / "devices-4.pb"
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideBackend(root: Path): DesktopDirectoryBackend =
    DesktopDirectoryBackend((root / "sync-staging").toString())

  @Provides
  @SingleIn(AppScope::class)
  fun provideRefreshOrchestrator(
    repo: SyncRepo,
    backend: DesktopDirectoryBackend,
  ): RefreshOrchestrator = PortableRefresh(repo, backend)

  @Provides
  @SingleIn(AppScope::class)
  fun provideGoogleAccountRepo(
    root: Path,
    bundledOAuth: BundledDesktopOAuth,
    scope: CoroutineScope,
  ): GoogleAccountRepo {
    val oauthConfigLoader =
      GoogleOAuthConfigLoader(
        configFile = root / "google-oauth.json",
        bundled = bundledOAuth.config,
      )
    val googleAccountStore = GoogleAccountStore(root / "google-auth.json")
    return GoogleAccountRepo(
      store = googleAccountStore,
      flowFactory = { DesktopGoogleOAuthFlow(it) },
      configProvider = { oauthConfigLoader.load() },
      scope = scope,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideGoogleSyncController(
    googleAccounts: GoogleAccountRepo,
    repo: SyncRepo,
    scope: CoroutineScope,
  ): GoogleSyncController = PortableGoogleSyncController(googleAccounts, repo, scope)

  @Provides
  @SingleIn(AppScope::class)
  fun provideManifestStore(
    root: Path,
    googleAccounts: GoogleAccountRepo,
    googleSync: GoogleSyncController,
    devicesRepo: DevicesRepo,
  ): ManifestStore {
    val localManifest = FileManifestStore(root / "manifests")
    val driveManifest =
      GoogleDriveManifestStore(
        local = localManifest,
        accounts = googleAccounts,
        // Share the manifest by device name (slugified) when it's non-blank, so two hosts that both
        // auto-detect the same Cadence stick as "Cadence" land on the same Drive file; otherwise
        // fall back to the local id (per-host unique, so each host writes its own file).
        sharedKeyFor = { id ->
          devicesRepo.getDevice(id)?.name?.slugify()?.takeIf { it.isNotBlank() } ?: id
        },
      )
    return SwitchingManifestStore {
      // Hot-check prefs on every call so the user's toggle takes effect immediately.
      if (googleSync.state.value.enabled && googleSync.state.value.signedIn) driveManifest
      else localManifest
    }
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideSyncOrchestrator(
    store: SettingsStore,
    repo: SyncRepo,
    devicesRepo: DevicesRepo,
    backend: DesktopDirectoryBackend,
    scope: CoroutineScope,
    manifest: ManifestStore,
  ): SyncOrchestrator = PortableSync(repo, devicesRepo, backend, scope, manifest, HostId(store))

  @Provides
  @SingleIn(AppScope::class)
  fun provideRefreshScheduler(
    scope: CoroutineScope,
    refresh: RefreshOrchestrator,
  ): RefreshScheduler =
    object : RefreshScheduler {
      private val jobs = mutableMapOf<String, Job>()
      private val configurations = mutableMapOf<String, Pair<Boolean, Int>>()

      override fun schedule(profile: SyncProfile) =
        synchronized(this) {
          val config = profile.auto_refresh to profile.refresh_interval_minutes
          if (configurations[profile.id] == config) return@synchronized
          cancel(profile.id)
          configurations[profile.id] = config
          if (profile.auto_refresh && profile.refresh_interval_minutes > 0)
            jobs[profile.id] = scope.launch {
              while (isActive) {
                delay(profile.refresh_interval_minutes.toLong() * 60_000)
                runNow(profile.id)
              }
            }
        }

      override fun cancel(profileId: String) =
        synchronized(this) {
          jobs.remove(profileId)?.cancel()
          configurations.remove(profileId)
          Unit
        }

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
  fun provideUsbManager(scope: CoroutineScope): UsbManager = DesktopUsbManager(scope)

  @Provides
  @SingleIn(AppScope::class)
  fun provideBluetoothController(): BluetoothController = UnavailableBluetoothController()

  @Provides fun provideFileSystem(): FileSystem = FileSystem.SYSTEM

  @Provides fun provideOptionalFileSystem(fileSystem: FileSystem): FileSystem? = fileSystem

  @Provides
  @SingleIn(AppScope::class)
  fun provideStorageManager(): StorageManager =
    object : StorageManager {
      override fun getVolume(path: Path) = Volume(description = "OS mounted directory", path = path)
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
  fun provideSourceBrowser(repo: SyncRepo, backend: DesktopDirectoryBackend): SourceBrowser =
    PortableSourceBrowser(repo, backend)

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(
      @Provides scope: CoroutineScope,
      @Provides bundledOAuth: BundledDesktopOAuth,
    ): DesktopAppGraph
  }
}

private fun String.slugify(): String =
  lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')

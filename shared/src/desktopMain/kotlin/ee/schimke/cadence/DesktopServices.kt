package ee.schimke.cadence

import ee.schimke.cadence.bluetooth.UnavailableBluetoothController
import ee.schimke.cadence.data.*
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.metro.PortableViewModelFactory
import ee.schimke.cadence.sync.*
import ee.schimke.cadence.usb.DesktopUsbManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class DesktopServices : AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val root = (System.getProperty("user.home") + "/.cadence").toPath()

  init {
    FileSystem.SYSTEM.createDirectories(root)
  }

  private val store = createFilesDataStore { root / "devices-4.pb" }
  private val repo = SyncRepo(store)
  private val backend = DesktopDirectoryBackend((root / "sync-staging").toString())
  private val refresh = PortableRefresh(repo, backend)
  private val sync = PortableSync(repo, DevicesRepo(store), backend, scope)
  private val scheduler =
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

  init {
    scope.launch {
      var previous = emptySet<String>()
      repo.profiles.collectLatest { profiles ->
        val current = profiles.map { it.id }.toSet()
        (previous - current).forEach(scheduler::cancel)
        profiles.forEach(scheduler::schedule)
        previous = current
      }
    }
  }

  private val usb = DesktopUsbManager(scope)

  init {
    scope.launch {
      var previous = emptySet<Int>()
      usb.listDevices().collect { devices ->
        val prefs = repo.preferences.first()
        val added = devices.filter { it.id !in previous }
        previous = devices.map { it.id }.toSet()
        if (
          prefs?.auto_sync_on_usb == true &&
            added.any { device ->
              val match =
                "${device.vendorId.toString(16).padStart(4, '0')}:${device.productId.toString(16).padStart(4, '0')}"
              prefs.usb_match.isBlank() || prefs.usb_match.equals(match, ignoreCase = true)
            }
        )
          sync.startSync(prefs.target_device_id)
      }
    }
  }

  val factory =
    PortableViewModelFactory(
      store,
      usb,
      UnavailableBluetoothController(),
      FileSystem.SYSTEM,
      object : StorageManager {
        override fun getVolume(path: Path) =
          Volume(description = "OS mounted directory", path = path)
      },
      refresh,
      sync,
      scheduler,
      object : SourceSuggestionsProvider {
        override suspend fun list() = emptyList<SourceSuggestion>()

        override fun open(suggestion: SourceSuggestion) {}
      },
    )

  override fun close() {
    scope.cancel()
  }
}

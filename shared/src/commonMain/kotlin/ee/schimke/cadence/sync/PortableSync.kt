@file:OptIn(kotlin.time.ExperimentalTime::class)

package ee.schimke.cadence.sync

import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.datastore.proto.SourceKind
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Same refresh/stage/copy workflow on desktop and the browser, using platform directory access. */
class PortableRefresh(private val repo: SyncRepo, private val backend: DirectoryBackend) :
  RefreshOrchestrator {
  private val lock = Mutex()
  override val progress = MutableStateFlow(RefreshProgress())

  override suspend fun refresh(profileId: String) = lock.withLock {
    val profile = requireNotNull(repo.getProfile(profileId)) { "Profile not found" }
    progress.value =
      RefreshProgress(running = true, profileId = profile.id, profileName = profile.name)
    try {
      val files =
        profile.source_ids.flatMap { id ->
          val source = requireNotNull(repo.getSource(id)) { "Source not found" }
          require(source.kind == SourceKind.LOCAL_DIRECTORY) {
            "Mount the NFS share and add it as a local directory on this platform."
          }
          backend.files(
            if (source.subpath.isBlank()) source.location
            else joinDirectory(source.location, source.subpath)
          )
        }
      require(files.map { it.relativePath }.distinct().size == files.size) {
        "Sources contain duplicate relative filenames. Use separate profiles."
      }
      val target = joinDirectory(backend.stagingRoot, profile.staging_subpath)
      backend.clear(target)
      progress.value = progress.value.copy(filesTotal = files.size)
      files.forEachIndexed { index, file ->
        currentCoroutineContext().ensureActive()
        progress.value = progress.value.copy(currentFileName = file.relativePath)
        backend.copy(file.location, joinDirectory(target, file.relativePath))
        progress.value = progress.value.copy(filesCompleted = index + 1)
      }
      repo.recordRefreshSuccess(profile.id, Clock.System.now().toString())
      progress.value = progress.value.copy(running = false, currentFileName = null)
    } catch (e: CancellationException) {
      progress.value = progress.value.copy(running = false)
      throw e
    } catch (e: Exception) {
      val message = e.message ?: "Refresh failed"
      progress.value = progress.value.copy(running = false, lastError = message)
      repo.recordRefreshFailure(profile.id, message)
      throw e
    }
  }
}

class PortableSync(
  private val repo: SyncRepo,
  private val devices: DevicesRepo,
  private val backend: DirectoryBackend,
  private val scope: CoroutineScope,
  private val manifest: ManifestStore = NoOpManifestStore(),
  private val hostId: HostId? = null,
) : SyncOrchestrator {
  override val progress = MutableStateFlow(SyncProgress())
  private var job: Job? = null

  override suspend fun startSync(targetDeviceId: String?) {
    if (job?.isActive == true) return
    job = scope.launch {
      progress.value = SyncProgress(running = true)
      try {
        val id =
          targetDeviceId?.takeIf { it.isNotBlank() } ?: repo.preferences.first()?.target_device_id
        val device =
          requireNotNull(id?.let { devices.getDevice(it) }) {
            "Select a mounted target directory in Devices, then choose it in Manage sync."
          }
        val plan =
          repo.listProfiles().flatMap { profile ->
            backend.files(joinDirectory(backend.stagingRoot, profile.staging_subpath)).map {
              Triple(profile, it, joinDirectory(device.path, profile.staging_subpath))
            }
          }
        require(plan.isNotEmpty()) { "Staging is empty — refresh a profile first" }
        progress.value = progress.value.copy(filesTotal = plan.size)
        val touchedProfileIds = mutableSetOf<String>()
        // Collect manifest writes in memory and flush once per run so a large library doesn't
        // rewrite the sidecar on every file (O(N) IO instead of O(N²)).
        val pendingManifest = mutableMapOf<String, ManifestEntry>()
        val resolvedHostId = hostId?.get() ?: ""
        plan.forEachIndexed { index, (profile, file, target) ->
          ensureActive()
          val targetPath = joinDirectory(target, file.relativePath)
          progress.value =
            progress.value.copy(
              currentProfileName = profile.name,
              currentFileName = file.relativePath,
              currentBytes = 0,
              currentTotal = file.size,
            )
          val existing = backend.metadata(targetPath)
          val unchanged =
            existing != null &&
              existing.size == file.size &&
              file.lastModifiedMillis > 0 &&
              existing.lastModifiedMillis >= file.lastModifiedMillis
          if (unchanged) {
            progress.value =
              progress.value.copy(
                filesCompleted = index + 1,
                filesSkipped = progress.value.filesSkipped + 1,
                currentBytes = file.size,
              )
          } else {
            backend.copy(file.location, targetPath)
            pendingManifest[joinDirectory(profile.staging_subpath, file.relativePath)] =
              ManifestEntry(
                size = file.size,
                lastModifiedMillis = file.lastModifiedMillis,
                hostId = resolvedHostId,
                syncedAt = Clock.System.now().toString(),
              )
            progress.value =
              progress.value.copy(filesCompleted = index + 1, currentBytes = file.size)
          }
          touchedProfileIds += profile.id
        }
        if (pendingManifest.isNotEmpty()) manifest.recordAll(device.id, pendingManifest)
        val syncedAt = Clock.System.now().toString()
        touchedProfileIds.forEach { repo.recordSyncSuccess(it, syncedAt) }
        progress.value = progress.value.copy(running = false, currentFileName = null)
      } catch (e: CancellationException) {
        progress.value = progress.value.copy(running = false, lastError = "Sync cancelled")
        throw e
      } catch (e: Exception) {
        progress.value =
          progress.value.copy(running = false, lastError = e.message ?: "Sync failed")
      }
    }
  }

  override fun cancel() {
    job?.cancel()
  }
}

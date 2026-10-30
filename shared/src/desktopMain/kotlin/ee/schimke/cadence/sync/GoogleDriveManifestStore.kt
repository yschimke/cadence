package ee.schimke.cadence.sync

import ee.schimke.cadence.sync.google.GoogleAccountRepo
import ee.schimke.cadence.sync.google.GoogleDriveClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Drive-backed [ManifestStore]. Each managed device gets one JSON object in the signed-in account's
 * Drive appDataFolder, named cadence-manifest-{sharedKey}.json. Keeps [local] as a mirror so writes
 * survive going offline and reads don't round-trip Google on the hot path.
 *
 * [sharedKeyFor] turns a local [Device.id] (random per-host UUID) into a stable key two hosts
 * registering the same target can agree on — otherwise each host would write its own private
 * manifest file and the cross-host story collapses. The desktop wiring resolves it from the
 * device's user-visible name (slugified) so the "Cadence" default auto-detect matches across hosts.
 *
 * Writes mark the device dirty; a failed Drive upload leaves it dirty so [syncRemote] at the next
 * end-of-sync retries, even when every file that run was skip-if-same.
 *
 * Never deletes; reconciliation of multi-host entries is a Phase 3 concern.
 */
class GoogleDriveManifestStore(
  private val local: ManifestStore,
  private val accounts: GoogleAccountRepo,
  private val sharedKeyFor: suspend (deviceId: String) -> String,
  private val drive: GoogleDriveClient =
    GoogleDriveClient(tokenProvider = { accounts.accessToken() }),
) : ManifestStore {
  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }
  private val writeLock = Mutex()
  // deviceId → true means the remote copy may be behind the local one.
  private val dirty = mutableSetOf<String>()

  override suspend fun load(deviceId: String): SyncManifest {
    val remote = runCatching {
      drive.downloadByName(fileName(deviceId))
    }.getOrNull()?.let { runCatching { json.decodeFromString<SyncManifest>(it) }.getOrNull() }
    if (remote != null) return remote
    return local.load(deviceId)
  }

  override suspend fun recordAll(deviceId: String, entries: Map<String, ManifestEntry>) {
    if (entries.isEmpty()) return
    writeLock.withLock {
      local.recordAll(deviceId, entries)
      synchronized(dirty) { dirty += deviceId }
      flushLocked(deviceId)
    }
  }

  override suspend fun syncRemote(deviceId: String) {
    val needsFlush = synchronized(dirty) { deviceId in dirty }
    if (!needsFlush) return
    writeLock.withLock { flushLocked(deviceId) }
  }

  /** Caller must hold [writeLock]. Clears the dirty flag only when the upload succeeds. */
  private suspend fun flushLocked(deviceId: String) {
    val merged = local.load(deviceId)
    val result = runCatching {
      drive.uploadOrReplace(fileName(deviceId), json.encodeToString(merged))
    }
    if (result.isSuccess) synchronized(dirty) { dirty -= deviceId }
  }

  private suspend fun fileName(deviceId: String) = "cadence-manifest-${sharedKeyFor(deviceId)}.json"
}

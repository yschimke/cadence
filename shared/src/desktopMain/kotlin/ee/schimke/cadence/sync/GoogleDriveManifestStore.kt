package ee.schimke.cadence.sync

import ee.schimke.cadence.sync.google.GoogleAccountRepo
import ee.schimke.cadence.sync.google.GoogleDriveClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Drive-backed [ManifestStore]. Each managed device gets one JSON object in the signed-in account's
 * Drive appDataFolder, named cadence-manifest-{deviceId}.json. Keeps [local] as a mirror so writes
 * survive going offline and reads don't round-trip Google on the hot path.
 *
 * This store never deletes; manifests accumulate per device. Reconciliation of multi-host entries
 * is a Phase 3 concern.
 */
class GoogleDriveManifestStore(
  private val local: ManifestStore,
  private val accounts: GoogleAccountRepo,
  private val drive: GoogleDriveClient =
    GoogleDriveClient(tokenProvider = { accounts.accessToken() }),
) : ManifestStore {
  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }
  private val writeLock = Mutex()

  override suspend fun load(deviceId: String): SyncManifest {
    val remote = runCatching {
      drive.downloadByName(fileName(deviceId))
    }.getOrNull()?.let { runCatching { json.decodeFromString<SyncManifest>(it) }.getOrNull() }
    if (remote != null) return remote
    return local.load(deviceId)
  }

  override suspend fun record(deviceId: String, relPath: String, entry: ManifestEntry) {
    writeLock.withLock {
      local.record(deviceId, relPath, entry)
      runCatching {
        val merged = local.load(deviceId)
        drive.uploadOrReplace(fileName(deviceId), json.encodeToString(merged))
      }
    }
  }

  private fun fileName(deviceId: String) = "cadence-manifest-$deviceId.json"
}

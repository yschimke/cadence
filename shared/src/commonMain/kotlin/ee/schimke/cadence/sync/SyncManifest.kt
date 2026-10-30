package ee.schimke.cadence.sync

import kotlinx.serialization.Serializable

/**
 * What a given host has written to a given managed device, keyed by relative path.
 *
 * This is the local half of the cross-host sync story: each sync run appends to the manifest for
 * the target device. Phase 2 will mirror it through a shared backend (Google Drive AppData) so
 * other hosts can see what's already there before they decide what to copy.
 */
@Serializable
data class SyncManifest(val deviceId: String, val entries: Map<String, ManifestEntry> = emptyMap())

/** One file's record on one device: enough to recognise an unchanged copy and attribute it. */
@Serializable
data class ManifestEntry(
  val size: Long,
  val lastModifiedMillis: Long,
  val hostId: String,
  val syncedAt: String,
)

/** Where sync records what it wrote. Default is a no-op; platforms replace it with real storage. */
interface ManifestStore {
  suspend fun load(deviceId: String): SyncManifest

  suspend fun record(deviceId: String, relPath: String, entry: ManifestEntry)
}

/** The default used anywhere a real backend isn't wired up yet (wasm/android for now). */
class NoOpManifestStore : ManifestStore {
  override suspend fun load(deviceId: String) = SyncManifest(deviceId)

  override suspend fun record(deviceId: String, relPath: String, entry: ManifestEntry) {}
}

package ee.schimke.cadence.sync

import kotlinx.coroutines.flow.Flow

interface SyncOrchestrator {
  val progress: Flow<SyncProgress>

  /**
   * Trigger a sync run targeting [targetDeviceId] (a [Device.id] from [DevicesRepo]). Iterates
   * every profile and copies its staging directory onto the device under the profile's
   * staging_subpath.
   */
  suspend fun startSync(targetDeviceId: String?)

  fun cancel()
}

data class SyncProgress(
  val running: Boolean = false,
  val currentProfileName: String? = null,
  val currentFileName: String? = null,
  val currentBytes: Long = 0,
  val currentTotal: Long = 0,
  val filesCompleted: Int = 0,
  val filesTotal: Int = 0,
  val lastError: String? = null,
)

interface SourceBrowser {
  /**
   * Enumerate files immediately under [source] (and within its subpath). Returns [BrowsedItem]s
   * suitable for staging.
   */
  suspend fun browse(sourceId: String): List<BrowsedItem>
}

data class BrowsedItem(
  val sourceId: String,
  val displayName: String,
  val sourceUri: String,
  val sizeBytes: Long,
  val relativePath: String,
)

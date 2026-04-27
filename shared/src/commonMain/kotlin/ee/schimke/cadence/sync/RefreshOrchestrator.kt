package ee.schimke.cadence.sync

import kotlinx.coroutines.flow.Flow

interface RefreshOrchestrator {
  val progress: Flow<RefreshProgress>

  /**
   * Refreshes the given profile by mirroring its sources into the local staging directory. Returns
   * when finished, or throws on failure (so WorkManager can apply its retry policy).
   */
  suspend fun refresh(profileId: String)
}

data class RefreshProgress(
  val running: Boolean = false,
  val profileId: String? = null,
  val profileName: String? = null,
  val currentFileName: String? = null,
  val filesCompleted: Int = 0,
  val filesTotal: Int = 0,
  val lastError: String? = null,
)

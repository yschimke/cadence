package ee.schimke.cadence.sync.google

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-device sync state, driven from [GoogleAccountRepo]. The desktop sync reads it to choose
 * between the local and the Drive-backed manifest.
 */
interface GoogleSyncController {
  val state: StateFlow<GoogleSyncState>

  suspend fun signIn()

  suspend fun signOut()

  suspend fun setEnabled(enabled: Boolean)

  suspend fun refreshConfig()
}

/** Snapshot shown to the UI. */
data class GoogleSyncState(
  val available: Boolean = false,
  val configured: Boolean = false,
  val signedInEmail: String? = null,
  val enabled: Boolean = false,
  val busy: Boolean = false,
  val lastError: String? = null,
) {
  val signedIn: Boolean
    get() = signedInEmail != null
}

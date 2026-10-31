package ee.schimke.cadence.sync.google

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common UI-side surface for cross-device sync. The desktop implementation drives it from
 * [GoogleAccountRepo]; other platforms get the no-op until their own account story lands.
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

/** Default: hide the Google card. Any platform without a wired controller gets this. */
object NoOpGoogleSyncController : GoogleSyncController {
  override val state = MutableStateFlow(GoogleSyncState(available = false))

  override suspend fun signIn() {}

  override suspend fun signOut() {}

  override suspend fun setEnabled(enabled: Boolean) {}

  override suspend fun refreshConfig() {}
}

/** Composition local; swap on the desktop entry point to inject the real controller. */
val LocalGoogleSyncController =
  staticCompositionLocalOf<GoogleSyncController> { NoOpGoogleSyncController }

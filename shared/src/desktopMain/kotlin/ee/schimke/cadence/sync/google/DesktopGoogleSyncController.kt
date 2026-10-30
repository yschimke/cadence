package ee.schimke.cadence.sync.google

import ee.schimke.cadence.sync.SyncRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DesktopGoogleSyncController(
  private val accounts: GoogleAccountRepo,
  private val syncRepo: SyncRepo,
  scope: CoroutineScope,
) : GoogleSyncController {
  private val _state = MutableStateFlow(GoogleSyncState(available = true))
  override val state: StateFlow<GoogleSyncState> = _state

  init {
    val sources = combine(accounts.account, accounts.config, syncRepo.preferences, ::projectState)
    scope.launch { sources.collect { _state.value = it } }
  }

  private fun projectState(
    account: GoogleAccount?,
    config: OAuthConfig?,
    prefs: ee.schimke.cadence.datastore.proto.SyncPreferences?,
  ): GoogleSyncState =
    _state.value.copy(
      configured = config != null,
      signedInEmail = account?.email,
      enabled = prefs?.cross_device_sync_enabled == true,
    )

  override suspend fun signIn() {
    runBusy {
      accounts.signIn()
    }
  }

  override suspend fun signOut() {
    runBusy {
      // Flip the flag off so sync falls back to the local-only store immediately.
      syncRepo.updatePreferences { it.copy(cross_device_sync_enabled = false) }
      accounts.signOut()
    }
  }

  override suspend fun setEnabled(enabled: Boolean) {
    runBusy {
      if (enabled && accounts.account.first() == null) {
        error("Sign in to Google before enabling cross-device sync")
      }
      syncRepo.updatePreferences { it.copy(cross_device_sync_enabled = enabled) }
    }
  }

  override suspend fun refreshConfig() {
    accounts.reloadConfig()
  }

  private suspend inline fun runBusy(crossinline block: suspend () -> Unit) {
    _state.value = _state.value.copy(busy = true, lastError = null)
    try {
      block()
    } catch (e: Throwable) {
      _state.value = _state.value.copy(lastError = e.message ?: e::class.simpleName)
    } finally {
      _state.value = _state.value.copy(busy = false)
    }
  }
}

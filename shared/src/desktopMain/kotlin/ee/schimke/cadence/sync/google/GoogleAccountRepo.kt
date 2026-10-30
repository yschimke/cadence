package ee.schimke.cadence.sync.google

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the signed-in [GoogleAccount] state and refreshes access tokens on demand. Suspends behind
 * [refreshLock] so overlapping Drive calls share one network round-trip when a token's expired.
 */
class GoogleAccountRepo(
  private val store: GoogleAccountStore,
  private val flowFactory: (OAuthConfig) -> GoogleOAuthFlow,
  private val configProvider: () -> OAuthConfig?,
  scope: CoroutineScope,
) {
  private val _account = MutableStateFlow<GoogleAccount?>(null)
  val account: StateFlow<GoogleAccount?> = _account.asStateFlow()
  private val _config = MutableStateFlow(configProvider())
  val config: StateFlow<OAuthConfig?> = _config.asStateFlow()
  private val refreshLock = Mutex()

  init {
    scope.launch {
      _account.value = store.load()
      _config.value = configProvider()
    }
  }

  /** Reload the OAuth config from disk (user may have just dropped the json file). */
  suspend fun reloadConfig() {
    _config.value = configProvider()
  }

  /** Run the browser consent flow. Replaces any existing account. */
  suspend fun signIn(): GoogleAccount {
    val cfg = _config.value ?: error("Google OAuth is not configured")
    val fresh = flowFactory(cfg).authorize()
    store.save(fresh)
    _account.value = fresh
    return fresh
  }

  suspend fun signOut() {
    store.clear()
    _account.value = null
  }

  /** Returns a usable access token, refreshing it when it's within a minute of expiring. */
  suspend fun accessToken(nowMillis: Long = System.currentTimeMillis()): String {
    val current = _account.value ?: error("Not signed in to Google")
    if (!current.needsRefresh(nowMillis)) return current.accessToken
    return refreshLock.withLock {
      // Another caller may have refreshed while we waited for the lock.
      val latest = _account.value ?: error("Not signed in to Google")
      if (!latest.needsRefresh(nowMillis)) return@withLock latest.accessToken
      val cfg = _config.value ?: error("Google OAuth is not configured")
      val refreshed = flowFactory(cfg).refresh(latest)
      store.save(refreshed)
      _account.value = refreshed
      refreshed.accessToken
    }
  }
}

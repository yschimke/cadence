package ee.schimke.cadence.sync.google

import android.content.Context
import kotlinx.coroutines.CompletableDeferred

/**
 * Android OAuth flow. The actual sign-in UI runs inside [GoogleOAuthActivity] using AppAuth and
 * Chrome Custom Tabs; this class is the suspend-fn facade that fires the activity and awaits its
 * result on behalf of [GoogleAccountRepo].
 */
class AndroidGoogleOAuthFlow(
  private val appContext: Context,
  private val config: OAuthConfig,
) : GoogleOAuthFlow {
  override suspend fun authorize(): GoogleAccount {
    val deferred = CompletableDeferred<GoogleAccount>()
    GoogleOAuthActivity.launch(appContext, config, deferred)
    return deferred.await()
  }

  override suspend fun refresh(account: GoogleAccount): GoogleAccount =
    refreshGoogleAccessToken(config, account)
}

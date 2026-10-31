package ee.schimke.cadence.sync.google

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Hosts the AppAuth-driven consent flow. Started by [AndroidGoogleOAuthFlow]; signals its result
 * back through a shared [CompletableDeferred] slot so the suspend facade can await it.
 *
 * One slot at a time: the Sign in button is disabled while sign-in is in progress, so parallel
 * requests shouldn't happen in practice; if one does, the second call fails fast.
 */
internal class GoogleOAuthActivity : ComponentActivity() {
  private lateinit var authService: AuthorizationService
  private lateinit var deferred: CompletableDeferred<GoogleAccount>
  private lateinit var config: OAuthConfig

  private val authLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      handleAuthResult(result.data)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val pending = pendingSlot.getAndSet(null)
    if (pending == null) {
      finish()
      return
    }
    config = pending.first
    deferred = pending.second
    authService = AuthorizationService(this)
    val serviceConfig =
      AuthorizationServiceConfiguration(
        Uri.parse(GOOGLE_AUTH_ENDPOINT),
        Uri.parse(GOOGLE_TOKEN_ENDPOINT),
      )
    val request =
      AuthorizationRequest.Builder(
          serviceConfig,
          config.clientId,
          ResponseTypeValues.CODE,
          Uri.parse(reversedClientRedirectUri(config.clientId)),
        )
        .setScopes(GOOGLE_DRIVE_APPDATA_SCOPE, GOOGLE_OPENID_EMAIL_SCOPE)
        .setPrompt("consent")
        .build()
    authLauncher.launch(authService.getAuthorizationRequestIntent(request))
  }

  private fun handleAuthResult(data: Intent?) {
    val response = data?.let { AuthorizationResponse.fromIntent(it) }
    val ex = data?.let { AuthorizationException.fromIntent(it) }
    when {
      response != null ->
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokens, tokenEx ->
          lifecycleScope.launch {
            try {
              val accessToken =
                tokens?.accessToken
                  ?: throw IllegalStateException(
                    "Token exchange returned no access token (${tokenEx?.message ?: "unknown error"})"
                  )
              val email = fetchGoogleUserEmail(accessToken)
              deferred.complete(
                GoogleAccount(
                  email = email,
                  refreshToken = tokens.refreshToken.orEmpty(),
                  accessToken = accessToken,
                  accessTokenExpiresAtMillis =
                    tokens.accessTokenExpirationTime
                      ?: (System.currentTimeMillis() + 3_600_000L),
                )
              )
            } catch (e: Throwable) {
              deferred.completeExceptionally(e)
            } finally {
              finish()
            }
          }
        }
      ex != null -> {
        deferred.completeExceptionally(GoogleOAuthCancelled())
        finish()
      }
      else -> {
        deferred.completeExceptionally(IllegalStateException("No authorization result"))
        finish()
      }
    }
  }

  override fun onDestroy() {
    if (::authService.isInitialized) authService.dispose()
    super.onDestroy()
  }

  companion object {
    private const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /**
     * The reversed-client-id custom scheme Google autogenerates for every Android OAuth client.
     * Both the Authorization request and the manifest's RedirectUriReceiverActivity use this.
     */
    private fun reversedClientRedirectUri(clientId: String): String {
      val reversed =
        "com.googleusercontent.apps." +
          clientId.substringBefore(".apps.googleusercontent.com")
      return "$reversed:/oauth2redirect"
    }

    private val pendingSlot =
      AtomicReference<Pair<OAuthConfig, CompletableDeferred<GoogleAccount>>?>()

    fun launch(context: Context, config: OAuthConfig, deferred: CompletableDeferred<GoogleAccount>) {
      if (!pendingSlot.compareAndSet(null, config to deferred)) {
        deferred.completeExceptionally(
          IllegalStateException("Another Google sign-in is already in progress")
        )
        return
      }
      context.startActivity(
        Intent(context, GoogleOAuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
  }
}

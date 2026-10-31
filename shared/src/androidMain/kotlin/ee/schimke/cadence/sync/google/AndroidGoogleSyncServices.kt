package ee.schimke.cadence.sync.google

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.cadence.sync.SyncRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

/**
 * Wires the Google sync pieces for Android: builds the OAuth config loader, account store, repo
 * and controller as one aggregate so the Metro graph can bind a single [GoogleSyncController]
 * without having to resolve the intermediate factories separately.
 */
@ContributesBinding(AppScope::class, binding = binding<GoogleSyncController>())
@SingleIn(AppScope::class)
@Inject
class AndroidGoogleSyncServices(
  context: Context,
  syncRepo: SyncRepo,
  bundledOAuth: BundledAndroidOAuth,
) : GoogleSyncController by build(context, syncRepo, bundledOAuth.config) {
  companion object {
    private fun build(
      context: Context,
      syncRepo: SyncRepo,
      bundled: OAuthConfig?,
    ): GoogleSyncController {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      val configFile = context.filesDir.resolve("google-oauth.json").toOkioPath()
      val configLoader = GoogleOAuthConfigLoader(configFile = configFile, bundled = bundled)
      val accountStore =
        GoogleAccountStore(context.filesDir.resolve("google-auth.json").toOkioPath())
      val accounts =
        GoogleAccountRepo(
          store = accountStore,
          flowFactory = { AndroidGoogleOAuthFlow(context.applicationContext, it) },
          configProvider = { configLoader.load() },
          scope = scope,
        )
      return PortableGoogleSyncController(accounts, syncRepo, scope)
    }
  }
}

/**
 * Carries the compile-time bundled OAuth credentials across the Metro graph boundary. The concrete
 * value comes from [CadenceAppGraph], which can read `BuildConfig`; `shared` cannot.
 */
data class BundledAndroidOAuth(val config: OAuthConfig?)

package ee.schimke.cadence.metro

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ee.schimke.cadence.BuildConfig
import ee.schimke.cadence.sync.google.BundledAndroidOAuth
import ee.schimke.cadence.sync.google.OAuthConfig

@DependencyGraph(AppScope::class)
interface CadenceAppGraph : AndroidAppGraph {
  @Provides
  @SingleIn(AppScope::class)
  fun provideBundledOAuth(): BundledAndroidOAuth =
    BundledAndroidOAuth(
      BuildConfig.GOOGLE_ANDROID_CLIENT_ID.takeIf { it.isNotBlank() }
        ?.let { OAuthConfig(clientId = it, clientSecret = "") }
    )

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): CadenceAppGraph
  }
}

package ee.schimke.cadence.widgets

import android.content.Context
import ee.schimke.livery.client.AndroidBundled
import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.EventSink
import ee.schimke.livery.client.FetchResult
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.JavaCrypto
import ee.schimke.livery.client.KtorFetcher
import ee.schimke.livery.client.MemoryBlobStore
import ee.schimke.livery.client.MemoryKeyValueStore
import ee.schimke.livery.client.SlotPlanner
import ee.schimke.livery.client.StaticSource
import ee.schimke.livery.client.TemplateRepository
import ee.schimke.livery.client.TrustPolicy
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider

/**
 * Cadence's widget templates: the catalog the APK ships (`composeApp/livery`, built into the assets
 * by the Livery Gradle plugin), with nothing downloaded yet. A catalog Cadence publishes later
 * becomes this repository's source; until then the bundled one is all it plays.
 */
internal class WidgetLivery(context: Context) {
  private val clock = Clock { System.currentTimeMillis() }

  val repository =
    TemplateRepository(
      // Never refreshed: no catalog is published yet, and rendering never waits on one.
      source = StaticSource("https://cadence.invalid/livery/main"),
      fetcher = { _, _ -> FetchResult.Failed(0, "no published catalog yet") },
      crypto = JavaCrypto,
      // Nothing downloaded is trusted until Cadence has a signing key; the bundled catalog is the
      // APK's own.
      trust = TrustPolicy.Signed(emptyList()),
      blobs = MemoryBlobStore(),
      kv = MemoryKeyValueStore(),
      clock = clock,
      targets =
        listOf(
          PlayerCapabilities(
            Surfaces.LAUNCHER_WIDGET,
            maxRcApiLevel = 7,
            profiles = setOf("widgets"),
            livePush = false,
            maxBytes = LiveryLauncherWidgetProvider.DEFAULT_TEMPLATE_MAX_BYTES,
          )
        ),
      bundled = AndroidBundled.fromAssets(context),
    )

  val planner = SlotPlanner(repository, EventSink {}, clock)

  /** The headphone templates draw no images; the resolver is here for the provider's contract. */
  val images by lazy { ImageResolver(repository, KtorFetcher(), JavaCrypto) }
}

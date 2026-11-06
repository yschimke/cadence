package ee.schimke.cadence.widgets

import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.client.TemplateQuery
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The widget's catalog wiring: with nothing downloaded (no network in a test, and no catalog has
 * been fetched), the planner draws the catalog the APK ships, through the same [WidgetLivery] the
 * widget uses, experiments and on-disk stores included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLiveryTest {
  private val catalogDir =
    File(requireNotNull(System.getProperty("cadence.livery.dir")) { "no cadence.livery.dir" })
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun drawsTheBundledCatalogBeforeAnyDownload() = runBlocking {
    val livery = WidgetLivery(RuntimeEnvironment.getApplication())
    val manifest = livery.repository.current()
    assertTrue(manifest != null, "the APK's catalog is in the assets")

    val contract =
      json.decodeFromString(
        Contract.serializer(),
        File(catalogDir, "contracts/headphone-controls.json").readText(),
      )
    val caps =
      PlayerCapabilities(
        Surfaces.LAUNCHER_WIDGET,
        maxRcApiLevel = 7,
        profiles = setOf("widgets"),
        livePush = false,
        maxBytes = LiveryLauncherWidgetProvider.DEFAULT_TEMPLATE_MAX_BYTES,
      )
    for ((name, example) in contract.examples) {
      val result =
        livery.planner.forAppData(
          TemplateQuery(
            HeadphoneControlsTemplate.CONTRACT,
            caps,
            widthDp = 240,
            heightDp = 160,
            minor = HeadphoneControlsTemplate.CONTRACT_MINOR,
          ),
          example,
        )
      val plan = assertIs<PlanResult.Ready>(result, "example '$name'").plan
      assertEquals("headphone-default", plan.template.design?.id, "example '$name'")
      assertEquals(emptyMap(), plan.experiments, "no season runs in the bundled catalog")
    }
  }

  @Test
  fun trustsOnlyTheCatalogKey() {
    // Blank until the key is generated; once set it must be a raw Ed25519 key.
    for (key in WidgetLivery.trustedKeys) {
      assertEquals(WidgetLivery.CATALOG_KEY_ID, key.keyId)
      assertEquals(32, key.publicKey.size)
    }
  }
}

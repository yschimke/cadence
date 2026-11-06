package ee.schimke.cadence.widgets

import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.client.TemplateQuery
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider
import java.io.File
import java.time.Instant
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

  private val contract =
    json.decodeFromString(
      Contract.serializer(),
      File(catalogDir, "contracts/headphone-controls.json").readText(),
    )
  private val caps =
    PlayerCapabilities(
      Surfaces.LAUNCHER_WIDGET,
      maxRcApiLevel = 7,
      profiles = setOf("widgets"),
      livePush = false,
      maxBytes = LiveryLauncherWidgetProvider.DEFAULT_TEMPLATE_MAX_BYTES,
    )

  @Test
  fun drawsTheBundledCatalogBeforeAnyDownload() = runBlocking {
    val livery =
      WidgetLivery(
        RuntimeEnvironment.getApplication(),
        Clock { Instant.parse("2026-11-20T00:00:00Z").toEpochMilli() },
      )
    assertTrue(livery.repository.current() != null, "the APK's catalog is in the assets")
    for ((name, plan) in plans(livery)) {
      assertEquals("headphone-default", plan.template.design?.id, "example '$name'")
      assertEquals(emptyMap(), plan.experiments, "example '$name': no season runs yet")
    }
  }

  @Test
  fun drawsEachSeasonsDesignOnlyInItsSeason() = runBlocking {
    val app = RuntimeEnvironment.getApplication()
    // At -> (design, the season on the plan, or null for none).
    val calendar =
      listOf(
        "2026-11-20T00:00:00Z" to ("headphone-default" to null),
        "2026-12-01T00:00:00Z" to ("headphone-winter" to "winter_2026"),
        "2026-12-19T12:00:00Z" to ("headphone-winter" to "winter_2026"),
        "2026-12-20T00:00:00Z" to ("headphone-christmas" to "christmas_2026"),
        "2026-12-25T00:00:00Z" to ("headphone-christmas" to "christmas_2026"),
        "2026-12-28T00:00:00Z" to ("headphone-new-year" to "new_year_2027"),
        "2026-12-31T23:00:00Z" to ("headphone-new-year" to "new_year_2027"),
        "2027-01-04T00:00:00Z" to ("headphone-winter" to "winter_2027"),
        "2027-01-20T00:00:00Z" to ("headphone-winter" to "winter_2027"),
        "2027-02-01T00:00:00Z" to ("headphone-default" to null),
        "2027-10-31T00:00:00Z" to ("headphone-halloween" to "halloween_2027"),
        "2027-11-02T00:00:00Z" to ("headphone-default" to null),
      )
    for ((at, expected) in calendar) {
      val (design, season) = expected
      val now = Instant.parse(at).toEpochMilli()
      for ((name, plan) in plans(WidgetLivery(app, Clock { now }))) {
        assertEquals(design, plan.template.design?.id, "example '$name' at $at")
        assertEquals(setOfNotNull(season), plan.experiments.keys, "the season on the plan at $at")
      }
    }
  }

  /** The widget's plan for every contract example, as the launcher widget provider asks for it. */
  private suspend fun plans(livery: WidgetLivery) =
    contract.examples.map { (name, example) ->
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
      name to assertIs<PlanResult.Ready>(result, "example '$name'").plan
    }

  @Test
  fun trustsOnlyTheCatalogKey() {
    val key = WidgetLivery.trustedKeys.single()
    assertEquals(WidgetLivery.CATALOG_KEY_ID, key.keyId)
    assertEquals(32, key.publicKey.size, "a raw Ed25519 public key")
    assertTrue(key.contractPrefixes.isEmpty(), "Cadence's own key signs every contract")
  }
}

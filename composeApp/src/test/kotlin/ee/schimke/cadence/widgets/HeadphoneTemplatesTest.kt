@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.livery.capture.DesignFolder
import ee.schimke.livery.capture.LiveryCapture
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.SizeRange
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.Target
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Captures the headphone widget's Livery templates (one per [HeadphoneControlsTemplate.Tier]) and
 * checks they are the ones committed in `composeApp/livery/designs/headphone-default/`. Run with
 * `-Pcadence.livery.regenerate=true` to write them instead, after changing the widget's drawing.
 *
 * Livery's `livery-capture` captures each tier and writes or checks the design folder: each
 * sidecar's reads and emits come from the captured document itself, so they can't drift from what
 * the template really does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
class HeadphoneTemplatesTest {
  private val design =
    File(
      requireNotNull(System.getProperty("cadence.livery.dir")) { "no cadence.livery.dir" },
      "designs/headphone-default",
    )
  private val regenerate = System.getProperty("cadence.livery.regenerate") == "true"

  @Test
  fun templatesAreCurrent() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val templates =
      HeadphoneControlsTemplate.tiers().associate { tier ->
        tier.name to
          LiveryCapture.capture(
            context,
            target(tier),
            tier.widthDp,
            tier.heightDp,
            source = mapOf("composable" to "HeadphoneControlsTemplateContent"),
          ) {
            HeadphoneControlsTemplateContent(
              tier,
              onOpen =
                rcWidgetHostAction(
                  name = HeadphoneControlsTemplate.OPEN,
                  payload = HeadphoneControlsTemplate.OPEN,
                ),
              onAction = { rcWidgetHostAction(name = it.wireName, payload = it.wireName) },
            )
          }
      }
    val folder = DesignFolder(design)
    if (regenerate) {
      folder.write(templates)
      println("Livery: wrote ${templates.size} headphone templates to $design")
    } else {
      assertEquals(emptyList(), folder.check(templates), STALE)
    }
  }

  /**
   * What the launcher is handed: each committed template with each of the contract's examples,
   * bound and instantiated as LiveryLauncherWidgetProvider does. With every state's branch still in
   * it, it must fit the widget's document budget.
   */
  @Test
  fun everyExampleInstantiatesInsideTheLauncherBudget() = runBlocking {
    val json = Json { ignoreUnknownKeys = true }
    val contract =
      json.decodeFromString(
        Contract.serializer(),
        File(design.parentFile.parentFile, "contracts/headphone-controls.json").readText(),
      )
    val instantiator = TemplateInstantiator { _, _, _ -> null }
    for (rc in design.listFiles().orEmpty().filter { it.extension == "rc" }) {
      for ((name, example) in contract.examples) {
        val bindings = (ContractBinder.bind(contract, example) as BindResult.Ok).bindings
        val out =
          instantiator.instantiate(
            rc.readBytes(),
            bindings,
            600,
            400,
            maxBytes = HeadphoneControlsWidget.DOCUMENT_MAX_BYTES,
            numericActions = true,
          )
        assertTrue(out is InstantiateResult.Ok, "${rc.name} with '$name': $out")
      }
    }
  }

  /** The launcher target a tier's template is for: its size range, Livery's widgets profile. */
  private fun target(tier: HeadphoneControlsTemplate.Tier) =
    Target(
      Surfaces.LAUNCHER_WIDGET,
      rcApiLevel = 7,
      profiles = listOf("widgets"),
      size = SizeRange(tier.minWidthDp, tier.maxWidthDp, tier.minHeightDp, tier.maxHeightDp),
    )

  private companion object {
    const val STALE =
      "the committed headphone templates are stale: run ./gradlew :composeApp:testDebugUnitTest " +
        "--tests '*HeadphoneTemplatesTest*' -Pcadence.livery.regenerate=true"
  }
}

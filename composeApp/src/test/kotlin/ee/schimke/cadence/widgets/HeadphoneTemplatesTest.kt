@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import ee.schimke.composeai.rcwidget.creation.androidx.rcWidgetHostAction
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
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the headphone widget's Livery templates (one per [HeadphoneControlsTemplate.Tier] and
 * [HeadphoneLook]) and checks they are the ones committed in `composeApp/livery/designs/<look>/`.
 * Run with `-Pcadence.livery.regenerate=true` to write them instead, after changing the widget's
 * drawing.
 *
 * Livery's `livery-capture` captures each tier and writes or checks the design folder: each
 * sidecar's reads and emits come from the captured document itself, so they can't drift from what
 * the template really does.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics: icons are drawn as paths read from an android.graphics.Path, which the legacy
// mode stubs out, so every icon was captured empty.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class HeadphoneTemplatesTest {
  private val catalog =
    File(requireNotNull(System.getProperty("cadence.livery.dir")) { "no cadence.livery.dir" })
  private val regenerate = System.getProperty("cadence.livery.regenerate") == "true"

  @Test
  fun templatesAreCurrent() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    for (look in HeadphoneLook.entries) {
      val design = File(catalog, "designs/${look.designId}")
      val templates =
        HeadphoneControlsTemplate.tiers().associate { tier ->
          tier.name to
            LiveryCapture.capture(
              context,
              target(tier),
              tier.widthDp,
              tier.heightDp,
              source =
                mapOf("composable" to "HeadphoneControlsTemplateContent") +
                  (if (look == HeadphoneLook.Default) emptyMap() else mapOf("look" to look.name)),
            ) {
              HeadphoneControlsTemplateContent(
                tier,
                onOpen =
                  rcWidgetHostAction(
                    name = HeadphoneControlsTemplate.OPEN,
                    payload = HeadphoneControlsTemplate.OPEN,
                  ),
                onAction = { rcWidgetHostAction(name = it.wireName, payload = it.wireName) },
                look = look,
              )
            }
        }
      val folder = DesignFolder(design)
      if (regenerate) {
        folder.write(templates)
        println("Livery: wrote ${templates.size} ${look.designId} templates to $design")
      } else {
        assertEquals(emptyList(), folder.check(templates), "${look.designId}: $STALE")
      }
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
        File(catalog, "contracts/headphone-controls.json").readText(),
      )
    val instantiator = TemplateInstantiator { _, _, _ -> null }
    val templates =
      HeadphoneLook.entries.flatMap { look ->
        File(catalog, "designs/${look.designId}").listFiles().orEmpty().filter {
          it.extension == "rc"
        }
      }
    assertTrue(templates.isNotEmpty(), "no captured templates")
    for (rc in templates) {
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
        assertTrue(
          out is InstantiateResult.Ok,
          "${rc.parentFile.name}/${rc.name} with '$name': $out",
        )
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

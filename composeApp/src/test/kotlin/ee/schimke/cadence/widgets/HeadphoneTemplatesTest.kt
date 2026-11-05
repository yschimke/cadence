@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.widgetsProfile
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInspector
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.protocol.Contract
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
 * Each sidecar's reads and emits come from the captured document itself (Livery's
 * TemplateInspector), so they can't drift from what the template really does.
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
    val generated = linkedMapOf<String, ByteArray>()
    for (tier in HeadphoneControlsTemplate.tiers()) {
      val bytes =
        captureSingleRemoteDocument(
            context = context,
            creationDisplayInfo = RemoteCreationDisplayInfo(tier.widthDp, tier.heightDp, 160),
            remoteDensity = RemoteDensity.Host,
            profile = widgetsProfile,
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
          .bytes
      val facts = requireNotNull(TemplateInspector.facts(bytes)) { "${tier.name} isn't a document" }
      generated["${tier.name}.rc"] = bytes
      generated["${tier.name}.json"] =
        sidecar(tier, facts.declaredReads, facts.emits).encodeToByteArray()
    }
    val committed =
      design
        .listFiles()
        .orEmpty()
        .filter { it.name != "design.json" }
        .associate { it.name to it.readBytes() }
    if (regenerate) {
      (committed.keys - generated.keys).forEach { File(design, it).delete() }
      generated.forEach { (name, bytes) -> File(design, name).writeBytes(bytes) }
      println("Livery: wrote ${generated.size / 2} headphone templates to $design")
      return@runBlocking
    }
    assertEquals(generated.keys.sorted(), committed.keys.sorted(), STALE)
    for ((name, bytes) in generated) {
      assertTrue(bytes.contentEquals(committed.getValue(name)), "$name: $STALE")
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

  private fun sidecar(
    tier: HeadphoneControlsTemplate.Tier,
    reads: Set<String>,
    emits: Set<String>,
  ): String {
    fun list(values: Collection<String>) = values.sorted().joinToString(", ") { "\"$it\"" }
    val size = buildList {
      add("\"minWidthDp\": ${tier.minWidthDp}")
      tier.maxWidthDp?.let { add("\"maxWidthDp\": $it") }
      add("\"minHeightDp\": ${tier.minHeightDp}")
      tier.maxHeightDp?.let { add("\"maxHeightDp\": $it") }
    }
    return """
      |{
      |  "target": { "surface": "launcher-widget", "rcApiLevel": 7, "profiles": ["widgets"], "size": { ${size.joinToString(", ")} } },
      |  "reads": [${list(reads)}],
      |  "emits": [${list(emits)}],
      |  "source": { "tool": "cadence-capture", "composable": "HeadphoneControlsTemplateContent" }
      |}
      |"""
      .trimMargin()
  }

  private companion object {
    const val STALE =
      "the committed headphone templates are stale: run ./gradlew :composeApp:testDebugUnitTest " +
        "--tests '*HeadphoneTemplatesTest*' -Pcadence.livery.regenerate=true"
  }
}

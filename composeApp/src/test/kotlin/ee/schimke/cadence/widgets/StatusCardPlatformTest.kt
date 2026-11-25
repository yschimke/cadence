@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import ee.schimke.cadence.podcasts.StatusCardButton
import ee.schimke.cadence.podcasts.StatusCardExtras
import ee.schimke.cadence.podcasts.StatusCardPool
import ee.schimke.cadence.podcasts.StatusCardTemplate
import ee.schimke.cadence.podcasts.StatusCardValues
import ee.schimke.cadence.testing.PlatformPlayerRule
import ee.schimke.cadence.testing.PlatformPlayerRule.Companion.ANDROID_17
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home's status card designs, instantiated with each example, on the platform's own player. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ANDROID_17], qualifiers = "mdpi")
class StatusCardPlatformTest {
  @get:Rule val player = PlatformPlayerRule()

  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))

  @Test
  fun everyExampleDraws() = runBlocking {
    val contract = Json {
      ignoreUnknownKeys = true
    }
      .decodeFromString(
        Contract.serializer(),
        File(catalog, "contracts/status-card.json").readText(),
      )
    val template =
      File(catalog, "designs/${StatusCardTemplate.SWIMMING_DESIGN}/in-app.rc")
        .readBytes()
        .toByteString()
    for ((name, example) in contract.examples) {
      val bindings = (ContractBinder.bind(contract, example) as BindResult.Ok).bindings
      val out = TemplateInstantiator { _, _, _ ->
        null
      }
        .instantiate(template, bindings, 360, 198, maxBytes = 200_000, numericActions = true)
      assertTrue(out is InstantiateResult.Ok, "'$name': $out")
      player.widget(out.bytes.toByteArray(), 360, 198, name).assertDraws("'$name'")
    }
  }

  /** What Home binds, built from its own values, fits the contract and draws. */
  @Test
  fun homesValuesBindAndDraw() = runBlocking {
    val contract = Json {
      ignoreUnknownKeys = true
    }
      .decodeFromString(
        Contract.serializer(),
        File(catalog, "contracts/status-card.json").readText(),
      )
    val template =
      File(catalog, "designs/${StatusCardTemplate.SWIMMING_DESIGN}/in-app.rc")
        .readBytes()
        .toByteString()
    val values =
      StatusCardValues(
        headline = "Ready to sync",
        attention = true,
        copied = "Last copy: 3 eps · 169m · 1 marked",
        available = "Ready: 3 eps · 139m",
        ready = true,
        battery = "50% · ~3h MP3 · ~4h 30m BT",
        source = null,
        extras =
          StatusCardExtras(
            buttons =
              listOf(
                StatusCardButton("sync", "Sync", primary = true),
                StatusCardButton("download", "Download"),
              ),
            changes = "2 new",
            changeLines = listOf("+ Tides of the Pacific · 31m", "+ Lighthouses · 40m"),
            refresh = true,
          ),
        pool = StatusCardPool(swimming = false, standing = true, progress = -1f),
        colors = WidgetColors.Light,
      )
    val bound = ContractBinder.bind(contract, values.data())
    assertTrue(bound is BindResult.Ok, "Home's values don't fit the contract: $bound")
    val out = TemplateInstantiator { _, _, _ ->
      null
    }
      .instantiate(template, bound.bindings, 360, 250, maxBytes = 200_000, numericActions = true)
    assertTrue(out is InstantiateResult.Ok, "$out")
    player.widget(out.bytes.toByteArray(), 360, 250, "home").assertDraws("Home's card")
    assertTrue(values.buttonFor("button_2")?.id == "download", "slot 2 is the second button")
  }

  /**
   * The card as a launcher widget: each example at a 4×2, 4×3 and 5×3 footprint, published as a
   * widget is, inside the launcher's budgets, with its taps as the numbered actions a launcher
   * reports and each one back to the contract's action name.
   */
  @Test
  fun everyExampleDrawsAsALauncherWidget() = runBlocking {
    val contract = Json {
      ignoreUnknownKeys = true
    }
      .decodeFromString(
        Contract.serializer(),
        File(catalog, "contracts/status-card.json").readText(),
      )
    val template =
      File(catalog, "designs/${StatusCardTemplate.SWIMMING_DESIGN}/in-app.rc")
        .readBytes()
        .toByteString()
    assertTrue(
      template.size <= LiveryLauncherWidgetProvider.DEFAULT_TEMPLATE_MAX_BYTES,
      "the template is ${template.size} bytes",
    )
    for ((name, example) in contract.examples) {
      for ((width, height) in listOf(312 to 152, 312 to 220, 420 to 260)) {
        val bindings = (ContractBinder.bind(contract, example) as BindResult.Ok).bindings
        val out = TemplateInstantiator { _, _, _ ->
          null
        }
          .instantiate(
            template,
            bindings,
            width,
            height,
            maxBytes = HeadphoneControlsWidget.DOCUMENT_MAX_BYTES,
            numericActions = true,
          )
        assertTrue(out is InstantiateResult.Ok, "'$name' at ${width}x$height: $out")
        assertTrue(
          out.actions.values.all { it in contract.actions },
          "'$name' taps ${out.actions.values}",
        )
        player
          .widget(out.bytes.toByteArray(), width, height, "launcher-$name-${width}x$height")
          .assertDraws("'$name' as a ${width}x$height launcher widget")
      }
    }
  }
}

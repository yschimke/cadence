@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import android.app.Activity
import android.graphics.Rect
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.ui.graphics.toArgb
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.cadence.testing.PlatformPlayerRule
import ee.schimke.cadence.testing.PlatformPlayerRule.Companion.ANDROID_16
import ee.schimke.cadence.testing.PlatformPlayerRule.Companion.ANDROID_17
import ee.schimke.composeai.rcwidget.launcher.widgetsProfile
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.notifications.LiveryNotifications
import ee.schimke.livery.protocol.Contract
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cadence's notification body as the platform's own player draws it on Android 16 and 17
 * ([PlatformPlayerRule]): each of the contract's examples, instantiated from the bundled template
 * as [NotificationLivery] instantiates it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ANDROID_16, ANDROID_17], qualifiers = "mdpi")
class PlatformPlayerTest {
  @get:Rule val player = PlatformPlayerRule()

  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))
  private val contract: Contract = Json {
    ignoreUnknownKeys = true
  }
    .decodeFromString(
      Contract.serializer(),
      File(catalog, "contracts/notification.json").readText(),
    )

  /** A notification body is blank when the player can't lay it out: every example must draw. */
  @Test
  fun everyExampleDrawsAsANotification() {
    assertTrue(contract.examples.isNotEmpty(), "the contract has no examples")
    for (example in contract.examples.keys) {
      player.notification(body(example), WIDTH, HEIGHT, example).assertDraws("'$example'")
    }
  }

  /** The same document published as a launcher widget is, without the notification's wrapper. */
  @Test
  fun everyExampleDrawsAsAWidget() {
    for (example in contract.examples.keys) {
      player.widget(body(example), WIDTH, HEIGHT, example).assertDraws("'$example'")
    }
  }

  /** `downloading` has a progress bar and Cancel: both are drawn, below the title and text. */
  @Test
  fun aBodyWithProgressAndCancelShowsBoth() {
    val played = player.notification(body("downloading"), WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the icon, title and text")
    played.assertDraws(BELOW_HEADER, "the progress bar and Cancel")
    played.assertDraws(CANCEL, "Cancel")
  }

  /** `syncing` has a progress bar and no Cancel. */
  @Test
  fun aBodyWithProgressOnlyHasNoCancel() {
    val played = player.notification(body("syncing"), WIDTH, HEIGHT)
    played.assertDraws(BELOW_HEADER, "the progress bar")
    played.assertBlank(CANCEL, "Cancel")
  }

  /** `ready` has neither: nothing is drawn below the title and text. */
  @Test
  fun aBodyWithNeitherShowsNeither() {
    val played = player.notification(body("ready"), WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the icon, title and text")
    played.assertBlank(BELOW_HEADER, "a progress bar or Cancel")
  }

  /**
   * Why bodies were blank before rc-players 2.31.1: a row or column whose spacing is a variable is
   * laid out as nothing, and the whole document with it. A capture at host density, as Livery's
   * templates are, writes every `spacedBy(n.dp)` as an expression over the display's density.
   * `RcLauncherWidgets.remoteViews` now recovers it.
   */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aLayoutSpacedByAVariableDrawsNothingAsItIs() {
    assertEquals(0, player.asIs(spacedColumn(), WIDTH, HEIGHT).ink)
  }

  /** The same document through the publishing path, which makes the spacing a literal. */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aLayoutSpacedByAVariableIsRecovered() {
    player.widget(spacedColumn(), WIDTH, HEIGHT).assertDraws()
  }

  /** [example] of the contract, written into the bundled template as the app writes a post. */
  private fun body(example: String): ByteArray = runBlocking {
    val template =
      File(catalog, "designs/notification-default/notification-w200-h64.rc").readBytes()
    // The app sends its colours with every post (NotificationTemplate.data); the examples don't.
    val colours = buildJsonObject {
      NotificationTemplate.COLORS.forEach { (role, of) ->
        put(role, "#%08X".format(of(WidgetColors.Light).toArgb()))
      }
    }
    val data =
      JsonObject(requireNotNull(contract.examples[example]) { "no example '$example'" } + colours)
    val bindings = (ContractBinder.bind(contract, data) as BindResult.Ok).bindings
    val result = TemplateInstantiator { _, _, _ ->
      null
    }
      .instantiate(
        template,
        bindings,
        WIDTH,
        HEIGHT,
        maxBytes = LiveryNotifications.DEFAULT_MAX_BYTES,
        numericActions = true,
      )
    (result as InstantiateResult.Ok).bytes
  }

  /** The notification drawing in a column spaced by 10dp, captured at host density. */
  private fun spacedColumn(): ByteArray = runBlocking {
    val context = Robolectric.buildActivity(Activity::class.java).setup().get()
    captureSingleRemoteDocument(
        context = context,
        creationDisplayInfo = RemoteCreationDisplayInfo(WIDTH, HEIGHT, 160),
        remoteDensity = RemoteDensity.Host,
        profile = widgetsProfile,
      ) {
        RemoteColumn(verticalArrangement = RemoteArrangement.spacedBy(10.rdp)) {
          NotificationTemplateContent(
            null,
            null,
            NotificationBody("Processing", "Trimming silence…", NotificationIcon.Process),
            colors = WidgetColors.Light,
          )
        }
      }
      .bytes
  }

  private companion object {
    const val WIDTH = 320
    const val HEIGHT = 150

    /** The icon, title and text: the first row, inside the body's padding. */
    val HEADER = Rect(0, 0, WIDTH, 60)

    /** Where the progress bar and Cancel go, when a body has them. */
    val BELOW_HEADER = Rect(0, 64, WIDTH, HEIGHT)

    /** The Cancel button: right-aligned under the lane. */
    val CANCEL = Rect(WIDTH / 2, 100, WIDTH, HEIGHT)
  }
}

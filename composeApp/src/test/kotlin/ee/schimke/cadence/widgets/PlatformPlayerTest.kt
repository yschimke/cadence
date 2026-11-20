@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.cadence.testing.PlatformPlayerRule
import ee.schimke.cadence.testing.PlatformPlayerRule.Companion.ANDROID_16
import ee.schimke.cadence.testing.PlatformPlayerRule.Companion.ANDROID_17
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.livery.capture.cmp.LiveryCapture
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.notifications.LiveryNotifications
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.Target
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.runner.RunWith
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
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun everyExampleDrawsAsANotification() {
    assertTrue(contract.examples.isNotEmpty(), "the contract has no examples")
    for (example in contract.examples.keys) {
      player.notification(body(example), WIDTH, HEIGHT, example).assertDraws("'$example'")
    }
  }

  /** The same document published as a launcher widget is, without the notification's wrapper. */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun everyExampleDrawsAsAWidget() {
    for (example in contract.examples.keys) {
      player.widget(body(example), WIDTH, HEIGHT, example).assertDraws("'$example'")
    }
  }

  /**
   * Android 17's player gives a body every pixel SystemUI offers (up to 256dp) unless the document
   * asks for less: a root that filled its height made every notification as tall as the shade
   * allows, mostly empty. The body asks for its content, so a text-only post is the shortest.
   */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aBodyIsOnlyAsTallAsWhatItShows() {
    val heights =
      contract.examples.keys.associateWith { player.notificationHeight(body(it), WIDTH, 256) }
    heights.forEach { (example, height) ->
      assertTrue(height in 32..120, "'$example' asks for ${height}dp of a 256dp shade")
    }
    assertTrue(heights.getValue("ready") < heights.getValue("syncing"), "$heights")
    assertTrue(heights.getValue("ready") < heights.getValue("downloading"), "$heights")
  }

  /** `downloading` has a progress bar and no in-body Cancel (Android renders Cancel natively). */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aBodyWithProgressShowsTheBarAndNoInBodyCancel() {
    val played = player.notification(body("downloading"), WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the title and text")
    played.assertDraws(PROGRESS, "the progress bar")
    played.assertBlank(BELOW_PROGRESS, "no in-body Cancel button")
  }

  /** `syncing` has a progress bar and nothing below it (runs on both Android 16 and 17). */
  @Test
  fun aBodyWithProgressOnlyHasNoCancel() {
    val played = player.notification(body("syncing"), WIDTH, HEIGHT)
    played.assertDraws(PROGRESS, "the progress bar")
    played.assertBlank(BELOW_PROGRESS, "no in-body Cancel button")
  }

  /** `ready` has no progress bar: nothing is drawn below the title and text. */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aBodyWithNeitherShowsNeither() {
    val played = player.notification(body("ready"), WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the title and text")
    played.assertBlank(PROGRESS, "a progress bar")
  }

  /**
   * Why bodies broke before rc-players 2.31.1: a row or column whose spacing is a variable drops
   * subsequent children when laid out as-is. A capture at host density, as Livery's templates are,
   * writes every `spacedBy(n.dp)` as an expression over the display's density.
   * `RcLauncherWidgets.remoteViews` recovers it.
   */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aLayoutSpacedByAVariableDropsSubsequentChildrenAsItIs() {
    player.asIs(spacedColumn(), WIDTH, HEIGHT).assertBlank(PROGRESS, "second child dropped as-is")
  }

  /** The same document through the publishing path, which makes the spacing a literal. */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun aLayoutSpacedByAVariableIsRecovered() {
    player.widget(spacedColumn(), WIDTH, HEIGHT).assertDraws(PROGRESS, "second child recovered")
  }

  /**
   * The template captured with a body's values, as a preview captures it, played without being
   * instantiated: nothing collapses its layouts. A conditional layout for the bar was laid out over
   * the title here, which is what `RemoteContentPreview` showed.
   *
   * Android 17 only: its player, like AndroidX's that previews use, sizes by a value the host could
   * change. Android 16's needs literals, which only instantiation writes in.
   */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun anUninstantiatedBodyLaysTheBarOutBelowTheTitle() {
    val captured =
      capture(
        NotificationBody(
          "Downloading podcasts",
          "Lighthouses of the North Sea · 42%",
          NotificationIcon.Download,
          progress = 0.42f,
        ),
        cancel = true,
      )
    val played = player.widget(captured, WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the title and text")
    played.assertDraws(PROGRESS, "the progress bar")
    played.assertBlank(BELOW_PROGRESS, "no in-body Cancel button")
  }

  /** Without a bar, nothing is drawn below the title, uninstantiated too. */
  @Test
  @Config(sdk = [ANDROID_17], qualifiers = "mdpi")
  fun anUninstantiatedBodyWithNeitherShowsNeither() {
    val captured =
      capture(
        NotificationBody("3 episodes ready", "Plug in your headphones", NotificationIcon.Done),
      )
    val played = player.widget(captured, WIDTH, HEIGHT)
    played.assertDraws(HEADER, "the title and text")
    played.assertBlank(PROGRESS, "a progress bar")
  }

  /** [example] of the contract, written into the bundled template as the app writes a post. */
  private fun body(example: String): ByteArray = runBlocking {
    val t = NotificationTemplate
    val template =
      File(
          catalog,
          "designs/notification-default/notification-w${t.MIN_WIDTH_DP}-h${t.MIN_HEIGHT_DP}.rc",
        )
        .readBytes()
        .toByteString()
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
    (result as InstantiateResult.Ok).bytes.toByteArray()
  }

  /** The notification drawing in a column spaced by 10dp, captured at host density. */
  private fun spacedColumn(): ByteArray = captureDocument {
    RemoteColumn(verticalArrangement = RemoteArrangement.spacedBy(10.rdp)) {
      NotificationTemplateContent(
        null,
        null,
        NotificationBody("Processing", "Trimming silence…", NotificationIcon.Process),
        colors = WidgetColors.Light,
      )
      NotificationTemplateContent(
        null,
        null,
        NotificationBody("3 episodes ready", "Plug in your headphones", NotificationIcon.Done),
        colors = WidgetColors.Light,
      )
    }
  }

  /** The template with [body]'s values as its defaults, captured as a preview captures it. */
  private fun capture(body: NotificationBody, cancel: Boolean = false): ByteArray =
    captureDocument {
      NotificationTemplateContent(null, null, body, cancel, colors = WidgetColors.Light)
    }

  /**
   * Captured as the template is (NotificationTemplatesTest): a document for a launcher's player.
   */
  private fun captureDocument(content: @Composable @RemoteComposable () -> Unit): ByteArray =
    runBlocking {
      LiveryCapture.capture(
          Target(Surfaces.NOTIFICATION, rcApiLevel = 7, profiles = listOf("widgets")),
          WIDTH,
          HEIGHT,
          content = content,
        )
        .bytes
        .toByteArray()
    }

  private companion object {
    const val WIDTH = 320
    const val HEIGHT = 104

    /** The title and text inside the body's top padding. */
    val HEADER = Rect(0, 0, WIDTH, 46)

    /** Where the progress bar goes when a body has one. */
    val PROGRESS = Rect(0, 48, WIDTH, 82)

    /** Below the progress bar: always blank since Cancel uses Android's native action. */
    val BELOW_PROGRESS = Rect(0, 84, WIDTH, HEIGHT)
  }
}

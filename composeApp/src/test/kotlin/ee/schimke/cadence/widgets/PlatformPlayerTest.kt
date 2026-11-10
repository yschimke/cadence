@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.composeai.rcplayer.edit.foldConstantExpressions
import ee.schimke.composeai.rcplayer.edit.hardcodeDensity
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.platformWidgetDocument
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cadence's Remote Compose documents as the platform's own player draws them: the player behind
 * `RemoteViews` draw instructions, which a launcher and SystemUI's notifications use. Robolectric
 * runs the real one, for Android 16 and 17, so a document that draws nothing shows here rather than
 * on a device.
 *
 * The player has no error for a document it can't lay out: it draws nothing. So these count what
 * was drawn. PNGs of each play land in `build/platform-player`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlatformPlayerTest {
  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))
  private val sample =
    NotificationBody("Processing", "Trimming silence…", NotificationIcon.Process, progress = 0.7f)

  /**
   * The cause of the blank notification bodies: a row or column whose spacing is a variable is laid
   * out as nothing, and the whole document with it. A capture at host density, as Livery's
   * templates are, writes every `spacedBy(n.dp)` as an expression over the display's density.
   */
  @Test
  @Config(sdk = [37], qualifiers = "mdpi")
  fun aLayoutSpacedByAVariableDrawsNothing() {
    val hostDensity = capture(RemoteDensity.Host)
    assertEquals(0, ink("host-density", bare(hostDensity)), "a host-density capture draws")
    assertTrue(ink("host-density-literal-spacing", bare(literalSpacing(hostDensity))) > MIN_INK)
    // The same drawing with its sizes written as numbers, as the podcast widget's capture is.
    assertTrue(ink("default-density", bare(capture(null))) > MIN_INK)
  }

  @Test
  @Config(sdk = [36], qualifiers = "mdpi")
  fun theNotificationBodyOnAndroid16() = everyNotificationExample()

  @Test
  @Config(sdk = [37], qualifiers = "mdpi")
  fun theNotificationBodyOnAndroid17() = everyNotificationExample()

  /**
   * Each of the notification contract's examples, instantiated from the bundled template and made
   * into `RemoteViews` as [NotificationLivery] does. Blank before rc-players 2.31.1, whose
   * `RcLauncherWidgets.remoteViews` recovers such a document for the device's display.
   */
  private fun everyNotificationExample() = runBlocking {
    val json = Json { ignoreUnknownKeys = true }
    val contract =
      json.decodeFromString(
        Contract.serializer(),
        File(catalog, "contracts/notification.json").readText(),
      )
    val template =
      File(catalog, "designs/notification-default/notification-w200-h64.rc").readBytes()
    val instantiator = TemplateInstantiator { _, _, _ -> null }
    assertTrue(contract.examples.isNotEmpty(), "the contract has no examples")
    for ((name, example) in contract.examples) {
      val bindings = (ContractBinder.bind(contract, example) as BindResult.Ok).bindings
      val document =
        instantiator.instantiate(
          template,
          bindings,
          WIDTH,
          HEIGHT,
          maxBytes = LiveryNotifications.DEFAULT_MAX_BYTES,
          numericActions = true,
        ) as InstantiateResult.Ok
      val drawn = ink("notification-$name", RcLauncherWidgets.remoteViews(document.bytes))
      assertTrue(drawn > MIN_INK, "'$name' is blank on Android ${Build.VERSION.SDK_INT}")
    }
  }

  /** The notification drawing with sample values, captured as Livery does or at 160dpi. */
  private fun capture(density: RemoteDensity?): ByteArray = runBlocking {
    val context = Robolectric.buildActivity(Activity::class.java).setup().get()
    val display = RemoteCreationDisplayInfo(WIDTH, HEIGHT, 160)
    val content =
      @androidx.compose.runtime.Composable {
        NotificationTemplateContent(null, null, sample, colors = WidgetColors.Light)
      }
    (if (density == null)
        captureSingleRemoteDocument(
          context = context,
          creationDisplayInfo = display,
          profile = widgetsProfile,
          content = content,
        )
      else
        captureSingleRemoteDocument(
          context = context,
          creationDisplayInfo = display,
          remoteDensity = density,
          profile = widgetsProfile,
          content = content,
        ))
      .bytes
  }

  /** [bytes] with only its rows' and columns' spacing written as the numbers they are here. */
  private fun literalSpacing(bytes: ByteArray): ByteArray {
    val document = RcDocumentCodec.decode(bytes, RcWireLimits())
    val constants =
      document
        .hardcodeDensity(1f)
        .foldConstantExpressions()
        .operations
        .filterIsInstance<RcFloatConstant>()
        .associate { it.id to it.value }
    fun RcFloatWord.literal() = referencedId?.let(constants::get) ?: this
    return RcDocumentCodec.encode(
      document.copy(
        operations =
          document.operations.map { operation ->
            when (operation) {
              is RcColumnLayout -> operation.copy(spacedBy = operation.spacedBy.literal())
              is RcRowLayout -> operation.copy(spacedBy = operation.spacedBy.literal())
              else -> operation
            }
          },
      ),
    )
  }

  /** [bytes] handed to the player as they are: none of `RcLauncherWidgets.remoteViews`' care. */
  private fun bare(bytes: ByteArray): RemoteViews =
    RemoteViews(
      RemoteViews.DrawInstructions.Builder(
          listOf(platformWidgetDocument(bytes, RcLauncherWidgets.targetFor())),
        )
        .build(),
    )

  /** How many pixels [views] drew over a white [WIDTH] × [HEIGHT] background. */
  private fun ink(name: String, views: RemoteViews): Int {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val frame = FrameLayout(activity)
    frame.setBackgroundColor(Color.WHITE)
    frame.addView(views.apply(activity, frame))
    activity.setContentView(frame, FrameLayout.LayoutParams(WIDTH, HEIGHT))
    shadowOf(Looper.getMainLooper()).idle()
    frame.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
    )
    frame.layout(0, 0, WIDTH, HEIGHT)
    shadowOf(Looper.getMainLooper()).idle()
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    frame.draw(Canvas(bitmap))
    val out = File("build/platform-player/sdk${Build.VERSION.SDK_INT}").apply { mkdirs() }
    File(out, "$name.png").outputStream().use {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val pixels = IntArray(WIDTH * HEIGHT)
    bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
    return pixels.count { it != Color.WHITE }
  }

  private companion object {
    const val WIDTH = 320
    const val HEIGHT = 110

    /** An icon alone is over a thousand pixels; antialiasing noise is nowhere near. */
    const val MIN_INK = 500
  }
}

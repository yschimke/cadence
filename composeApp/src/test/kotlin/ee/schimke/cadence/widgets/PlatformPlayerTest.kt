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
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.collapseConstantStateLayouts
import ee.schimke.composeai.rcplayer.edit.hardcodeDensity
import ee.schimke.composeai.rcplayer.edit.hardcodeUnboundNamedValues
import ee.schimke.composeai.rcplayer.edit.optimize
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetDocuments
import ee.schimke.composeai.rcwidget.RcWidgetTarget
import ee.schimke.composeai.rcwidget.RcWidgetTargets
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import ee.schimke.composeai.rcwidget.launcher.widgetsProfile
import ee.schimke.livery.capture.LiveryCapture
import ee.schimke.livery.protocol.SizeRange
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.Target
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Diagnostic: documents played by the platform's own Remote Compose player (`RemoteViews` draw
 * instructions) under Robolectric, to see whether a blank notification body shows without an
 * emulator. Prints how much each drew and saves PNGs under `build/platform-player`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlatformPlayerTest {
  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))
  private val bundled =
    File(catalog, "designs/notification-default/notification-w200-h64.rc").readBytes()
  private val sample =
    NotificationBody("Processing", "Trimming silence…", NotificationIcon.Process, progress = 0.7f)

  @Test @Config(sdk = [36], qualifiers = "mdpi") fun android16() = play()

  @Test @Config(sdk = [37], qualifiers = "mdpi") fun android17() = play()

  private fun static(document: RcDocument, density: Float, target: RcWidgetTarget): RcDocument {
    val adapted =
      document
        .hardcodeUnboundNamedValues()
        .collapseConstantStateLayouts()
        .hardcodeDensity(density)
        .adaptTo(target.profile, target.approximations, RcDisplay(density))
    println(
      "PLATFORM-PLAYER static ${target.profile.apiLevel} violations=${adapted.violations.map { it.message }}",
    )
    return adapted.document
  }

  /**
   * Prototype edit: references to constants in spacing (1), clip radii (2), draws (4) as literals.
   */
  private fun literal(source: RcDocument, density: Float, what: Int): RcDocument {
    val document = source.optimize()
    val constants =
      document.operations.filterIsInstance<RcFloatConstant>().associate { it.id to it.value }
    @Suppress("UNUSED_PARAMETER")
    fun RcFloatWord.at(index: Int): RcFloatWord {
      val id = referencedId ?: return this
      if (id == RcSystemVariables.DENSITY) return RcFloatWord.literal(density)
      return constants[id] ?: this
    }
    return document.copy(
      operations =
        document.operations.mapIndexed { i, op ->
          when {
            op is RcColumnLayout && what and 1 != 0 -> op.copy(spacedBy = op.spacedBy.at(i))
            op is RcRowLayout && what and 1 != 0 -> op.copy(spacedBy = op.spacedBy.at(i))
            op is RcRoundedClipRectModifier && what and 2 != 0 ->
              op.copy(
                topStart = op.topStart.at(i),
                topEnd = op.topEnd.at(i),
                bottomStart = op.bottomStart.at(i),
                bottomEnd = op.bottomEnd.at(i),
              )
            op is RcDraw4 && what and 4 != 0 ->
              op.copy(
                first = op.first.at(i),
                second = op.second.at(i),
                third = op.third.at(i),
                fourth = op.fourth.at(i),
              )
            else -> op
          }
        },
    )
  }

  private fun play() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    fun capture(android16: Boolean) = runBlocking {
      (if (android16)
          captureLauncherWidget(activity, RcWidgetCaptureSize(320, 110), profile = widgetsProfile) {
            NotificationTemplateContent(null, null, sample, colors = WidgetColors.Light)
          }
        else
          captureLauncherWidget(activity, RcWidgetCaptureSize(320, 110)) {
            NotificationTemplateContent(null, null, sample, colors = WidgetColors.Light)
          })
        .bytes
    }
    val target =
      Target(
        Surfaces.NOTIFICATION,
        rcApiLevel = 7,
        profiles = listOf("widgets"),
        size = SizeRange(200, null, 64, 256),
      )
    fun livery(values: Boolean, actions: Boolean) = runBlocking {
      LiveryCapture.capture(activity, target, 360, 128) {
          NotificationTemplateContent(
            if (actions) rcWidgetHostAction(name = "open", payload = "open") else null,
            if (actions) rcWidgetHostAction(name = "cancel", payload = "cancel") else null,
            if (values) sample else null,
            colors = WidgetColors.Light,
          )
        }
        .bytes
    }
    fun raw(host: Boolean, values: Boolean) = runBlocking {
      val display = RemoteCreationDisplayInfo(360, 128, 160)
      (if (host)
          captureSingleRemoteDocument(
            context = activity,
            creationDisplayInfo = display,
            remoteDensity = RemoteDensity.Host,
            profile = widgetsProfile,
          ) {
            NotificationTemplateContent(
              null,
              null,
              if (values) sample else null,
              colors = WidgetColors.Light,
            )
          }
        else
          captureSingleRemoteDocument(
            context = activity,
            creationDisplayInfo = display,
            profile = widgetsProfile,
          ) {
            NotificationTemplateContent(
              null,
              null,
              if (values) sample else null,
              colors = WidgetColors.Light,
            )
          })
        .bytes
    }
    val density = activity.resources.displayMetrics.density
    fun edit(bytes: ByteArray, change: (RcDocument) -> RcDocument): ByteArray =
      RcDocumentCodec.encode(change(RcDocumentCodec.decode(bytes, RcWireLimits())))
    val docs: List<Triple<String, () -> ByteArray, Boolean>> =
      listOf(
        Triple("livery-blank-actions", { livery(false, true) }, false),
        Triple("livery-values-actions", { livery(true, true) }, false),
        Triple("livery-values-noactions", { livery(true, false) }, false),
        Triple("livery-blank-noactions", { livery(false, false) }, false),
        Triple("raw-hostdensity-values", { raw(true, true) }, false),
        Triple("raw-defaultdensity-values", { raw(false, true) }, false),
        Triple("raw-hostdensity-blank", { raw(true, false) }, false),
        Triple(
          "launcher-blank",
          {
            runBlocking {
              captureLauncherWidget(activity, RcWidgetCaptureSize(320, 110)) {
                  NotificationTemplateContent(null, null, null, colors = WidgetColors.Light)
                }
                .bytes
            }
          },
          false,
        ),
        Triple(
          "bundled-hardcode-density",
          {
            RcDocumentCodec.encode(
              RcDocumentCodec.decode(bundled, RcWireLimits())
                .hardcodeDensity(activity.resources.displayMetrics.density),
            )
          },
          false,
        ),
        Triple(
          "bundled-forDisplay",
          {
            RcDocumentCodec.encode(
              RcWidgetDocuments.forDisplay(
                RcDocumentCodec.decode(bundled, RcWireLimits()),
                RcDisplay(activity.resources.displayMetrics.density),
              ),
            )
          },
          false,
        ),
        Triple(
          "host-values-hardcode",
          { edit(raw(true, true)) { it.hardcodeDensity(density) } },
          false,
        ),
        Triple(
          "host-values-forDisplay",
          { edit(raw(true, true)) { RcWidgetDocuments.forDisplay(it, RcDisplay(density)) } },
          false,
        ),
        Triple(
          "host-values-serve",
          {
            edit(raw(true, true)) {
              RcWidgetDocuments.serve(it, RcLauncherWidgets.widgetTargetFor(), RcDisplay(density))
                .also { a -> println("PLATFORM-PLAYER serve violations=${a.violations}") }
                .document
            }
          },
          false,
        ),
        Triple(
          "host-values-serve16",
          {
            edit(raw(true, true)) {
              RcWidgetDocuments.serve(it, RcWidgetTargets.LauncherAndroid16, RcDisplay(density))
                .also { a -> println("PLATFORM-PLAYER serve16 violations=${a.violations}") }
                .document
            }
          },
          true,
        ),
        Triple(
          "bundled-serve16",
          {
            edit(bundled) {
              RcWidgetDocuments.serve(it, RcWidgetTargets.LauncherAndroid16, RcDisplay(density))
                .document
            }
          },
          true,
        ),
        Triple(
          "bundled-serve",
          {
            edit(bundled) {
              RcWidgetDocuments.serve(it, RcLauncherWidgets.widgetTargetFor(), RcDisplay(density))
                .document
            }
          },
          false,
        ),
        Triple(
          "static6-as6",
          { edit(raw(true, true)) { static(it, density, RcWidgetTargets.LauncherAndroid16) } },
          true,
        ),
        Triple(
          "static6",
          { edit(raw(true, true)) { static(it, density, RcWidgetTargets.LauncherAndroid16) } },
          false,
        ),
        Triple(
          "static7",
          { edit(raw(true, true)) { static(it, density, RcWidgetTargets.Launcher) } },
          false,
        ),
        Triple(
          "static-noadapt",
          {
            edit(raw(true, true)) {
              it
                .hardcodeUnboundNamedValues()
                .collapseConstantStateLayouts()
                .hardcodeDensity(density)
                .optimize()
            }
          },
          false,
        ),
        Triple(
          "static6-default-as6",
          { edit(raw(false, true)) { static(it, density, RcWidgetTargets.LauncherAndroid16) } },
          true,
        ),
        Triple(
          "literal-all",
          {
            edit(raw(true, true)) {
              literal(static(it, density, RcWidgetTargets.LauncherAndroid16), density, 7)
            }
          },
          true,
        ),
        Triple(
          "literal-all-as7",
          {
            edit(raw(true, true)) {
              literal(static(it, density, RcWidgetTargets.Launcher), density, 7)
            }
          },
          false,
        ),
        Triple(
          "literal-spacing",
          {
            edit(raw(true, true)) {
              literal(static(it, density, RcWidgetTargets.LauncherAndroid16), density, 1)
            }
          },
          true,
        ),
        Triple(
          "literal-clip",
          {
            edit(raw(true, true)) {
              literal(static(it, density, RcWidgetTargets.LauncherAndroid16), density, 2)
            }
          },
          true,
        ),
        Triple(
          "literal-draw",
          {
            edit(raw(true, true)) {
              literal(static(it, density, RcWidgetTargets.LauncherAndroid16), density, 4)
            }
          },
          true,
        ),
        Triple(
          "literal-nostatic",
          { edit(raw(true, true)) { literal(it.hardcodeDensity(density), density, 7) } },
          false,
        ),
        Triple("bundled", { bundled }, false),
        Triple("bundled-as6", { bundled }, true),
        Triple("capture-device-profile", { capture(false) }, false),
        Triple("capture-android16-profile", { capture(true) }, false),
        Triple("capture-android16-profile-as6", { capture(true) }, true),
      )
    val out = File("build/platform-player/sdk${Build.VERSION.SDK_INT}").apply { mkdirs() }
    for ((name, bytes) in listOf("host" to raw(true, true), "default" to raw(false, true))) {
      val document =
        static(RcDocumentCodec.decode(bytes, RcWireLimits()), 1f, RcWidgetTargets.LauncherAndroid16)
      File(out, "ops-$name.txt")
        .writeText(
          "${document.header}\n" +
            document.operations.joinToString("\n") { it.toString().take(300) },
        )
    }
    for ((name, make, as6) in docs) {
      val result = runCatching {
        val bytes = make()
        val views =
          if (as6) RcLauncherWidgets.remoteViews(bytes, emptyMap(), RcTargetProfiles.WIDGETS_V6)
          else RcLauncherWidgets.remoteViews(bytes)
        val frame = FrameLayout(activity)
        frame.setBackgroundColor(Color.WHITE)
        frame.addView(views.apply(activity, frame))
        activity.setContentView(frame, FrameLayout.LayoutParams(320, 110))
        shadowOf(Looper.getMainLooper()).idle()
        frame.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(110, View.MeasureSpec.EXACTLY),
        )
        frame.layout(0, 0, 320, 110)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(320, 110, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixels = IntArray(320 * 110)
        bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 110)
        "ink=${pixels.count { it != Color.WHITE }} bytes=${bytes.size}"
      }
        .getOrElse { "FAILED ${it::class.simpleName}: ${it.message}" }
      println("PLATFORM-PLAYER sdk=${Build.VERSION.SDK_INT} $name: $result")
    }
  }
}

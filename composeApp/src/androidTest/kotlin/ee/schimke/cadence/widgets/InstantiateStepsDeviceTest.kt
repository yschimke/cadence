@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ee.schimke.cadence.MainActivity
import ee.schimke.cadence.notifications.CadenceNotifications
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import ee.schimke.composeai.rcwidget.launcher.widgetsProfile
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.notifications.LiveryNotifications
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic: Livery's steps one at a time, on the bundled notification template and on the same
 * drawing captured on this device, each played bare by this process's player. Saves `steps-N.png`
 * (four labelled panels each), `steps.txt` and `steps-logcat.txt`.
 */
@RunWith(AndroidJUnit4::class)
class InstantiateStepsDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test
  fun steps() {
    assumeTrue(Build.VERSION.SDK_INT >= 36)
    val livery = WidgetLivery.get(context)
    val body =
      NotificationBody(
        "Processing",
        "Trimming silence…",
        NotificationIcon.Process,
        progress = 0.7f,
        contentIntent = CadenceNotifications.openApp(context),
      )
    val colors = context.systemWidgetColors()
    val plan =
      runBlocking {
        LiveryNotifications(images = livery.images)
          .render(
            context,
            7200,
            heightDp = 110,
            plan = { slot ->
              livery.planner.forAppData(
                slot.query(
                  NotificationTemplate.CONTRACT,
                  minor = NotificationTemplate.CONTRACT_MINOR,
                ),
                NotificationTemplate.data(body, colors),
              )
            },
            tapIntent = { _, _ -> body.contentIntent!! },
          )
      }
        .plan!!
    val bundled = plan.template.bytes
    val captured = runBlocking {
      captureLauncherWidget(context, RcWidgetCaptureSize(320, 110)) {
          NotificationTemplateContent(null, null, body, colors = colors)
        }
        .bytes
    }
    val instantiator = TemplateInstantiator(livery.images)
    val notes = StringBuilder()
    fun instantiate(template: ByteArray, numeric: Boolean): ByteArray? =
      when (
        val result = runBlocking {
          instantiator.instantiate(template, plan.bindings, 320, 110, numericActions = numeric)
        }
      ) {
        is InstantiateResult.Ok -> result.bytes
        is InstantiateResult.Failed -> null.also { notes.appendLine("failed: ${result.reason}") }
      }
    fun recode(bytes: ByteArray): ByteArray =
      RcDocumentCodec.encode(RcDocumentCodec.decode(bytes, RcWireLimits()))

    val captured16 = runBlocking {
      captureLauncherWidget(context, RcWidgetCaptureSize(320, 110), profile = widgetsProfile) {
          NotificationTemplateContent(null, null, body, colors = colors)
        }
        .bytes
    }
    // Suffix " @6": handed to the player as a level 6 (Android 16) document, not this device's.
    val docs: List<Pair<String, () -> ByteArray?>> =
      listOf(
        "I bundled, instantiated @6" to { instantiate(bundled, true) },
        "J bundled, raw @6" to { bundled },
        "K device capture, Android 16 profile" to { captured16 },
        "L device capture, Android 16 profile @6" to { captured16 },
        "A bundled, instantiated, numeric taps" to { instantiate(bundled, true) },
        "B bundled, instantiated, named taps" to { instantiate(bundled, false) },
        "C bundled, raw (blank values)" to { bundled },
        "D bundled, re-encoded only" to { recode(bundled) },
        "E device capture, sample values" to { captured },
        "F device capture, re-encoded only" to { recode(captured) },
        "G device capture, instantiated, numeric" to { instantiate(captured, true) },
        "H device capture, instantiated, named" to { instantiate(captured, false) },
      )
    val views = docs.map { (name, make) ->
      val bytes =
        runCatching(make)
          .onFailure { notes.appendLine("$name: ${it.stackTraceToString()}") }
          .getOrNull()
      notes.appendLine("$name: ${bytes?.size} bytes")
      name to
        bytes?.let {
          runCatching {
            if (name.endsWith("@6"))
              RcLauncherWidgets.remoteViews(it, emptyMap(), RcTargetProfiles.WIDGETS_V6)
            else RcLauncherWidgets.remoteViews(it)
          }
            .onFailure { e -> notes.appendLine("$name views: ${e.stackTraceToString()}") }
            .getOrNull()
        }
    }
    File(outputDir(), "steps.txt").writeText(notes.toString())
    shell("logcat -c")
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      var holder: FrameLayout? = null
      scenario.onActivity { activity ->
        holder = FrameLayout(activity).also { it.setBackgroundColor(AndroidColor.WHITE) }
        activity.addContentView(
          holder,
          FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          ),
        )
      }
      views.chunked(4).forEachIndexed { page, chunk ->
        scenario.onActivity { activity ->
          val density = activity.resources.displayMetrics.density
          val column = LinearLayout(activity)
          column.orientation = LinearLayout.VERTICAL
          for ((name, remote) in chunk) {
            val label = TextView(activity)
            label.text = if (remote == null) "$name: FAILED" else name
            label.setTextColor(AndroidColor.RED)
            column.addView(label)
            val frame = FrameLayout(activity)
            frame.setBackgroundColor(0xFFE8F0FF.toInt())
            remote?.let { frame.addView(it.apply(activity, frame)) }
            column.addView(
              frame,
              LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (110 * density).toInt(),
              ),
            )
          }
          holder!!.removeAllViews()
          holder!!.addView(column)
        }
        Thread.sleep(2_500)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
          File(outputDir(), "steps-${page + 1}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
          }
        }
      }
      File(outputDir(), "steps-logcat.txt").writeText(shell("logcat -d"))
    }
  }

  private fun shell(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
      )
      .bufferedReader()
      .use { it.readText() }

  private fun outputDir(): File =
    (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: File(context.getExternalFilesDir(null), "notification-test"))
      .apply { mkdirs() }
}

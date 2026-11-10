@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ee.schimke.cadence.MainActivity
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic: documents from one constant text up to the whole notification template, each captured
 * on the device and played bare by this process's player, to find the piece Android 17's player
 * stops drawing at. Saves `simple-N.png` (four labelled panels each) and `simple-logcat.txt`.
 */
@RunWith(AndroidJUnit4::class)
class SimpleDocumentsDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val user = RemoteState.Domain.User

  @Test
  fun simplerDocuments() {
    assumeTrue(Build.VERSION.SDK_INT >= 36)
    val sample =
      NotificationBody("Processing", "Trimming silence…", NotificationIcon.Process, progress = 0.7f)
    val docs: List<Pair<String, @Composable () -> Unit>> =
      listOf(
        "1 constant text" to { Panel { RemoteText("Constant text".rs, color = Color.Black.rc) } },
        "2 named string, constant colour" to
          {
            Panel {
              RemoteText(
                RemoteString.createNamedRemoteString("title", "Named string", user),
                color = Color.Black.rc,
              )
            }
          },
        "3 constant text, named colour" to
          {
            Panel {
              RemoteText(
                "Named colour".rs,
                color = RemoteColor.createNamedRemoteColor("theme.x", AndroidColor.BLACK, user),
              )
            }
          },
        "4 state layout" to
          {
            Panel {
              val which = RemoteInt.createNamedRemoteInt("icon", 1, user)
              RemoteStateLayout(which, 0, 1, 2) { index: Int ->
                RemoteText("State $index".rs, color = Color.Black.rc)
              }
            }
          },
        "5 canvas, named float" to
          {
            Panel {
              val progress = RemoteFloat.createNamedRemoteFloat("progress", 0.7f, user)
              RemoteCanvas(RemoteModifier.fillMaxWidth().height(8.rdp)) {
                drawRect(
                  RemotePaint { color = Color.Blue.rc },
                  RemoteOffset(0f, 0f),
                  RemoteSize(width * progress, height),
                )
              }
            }
          },
        "6 visibility by named boolean (shown)" to
          {
            Panel {
              val shown = RemoteBoolean.createNamedRemoteBoolean("has", true, user)
              RemoteRow(RemoteModifier.visibility(shown.toRemoteInt()).fillMaxWidth()) {
                RemoteText("Visible row".rs, color = Color.Black.rc)
              }
            }
          },
        "7 text after a hidden row" to
          {
            Panel {
              val shown = RemoteBoolean.createNamedRemoteBoolean("has", false, user)
              RemoteRow(RemoteModifier.visibility(shown.toRemoteInt()).fillMaxWidth()) {
                RemoteText("Hidden row".rs, color = Color.Black.rc)
              }
              RemoteText("After hidden row".rs, color = Color.Black.rc)
            }
          },
        "8 whole template, sample values, light" to
          {
            NotificationTemplateContent(null, null, sample, colors = WidgetColors.Light)
          },
      )
    val views: List<Pair<String, RemoteViews?>> = docs.map { (name, content) ->
      name to
        runCatching {
          runBlocking {
            RcLauncherWidgets.remoteViews(
              captureLauncherWidget(context, RcWidgetCaptureSize(320, 110), content = content)
                .bytes,
            )
          }
        }
          .onFailure {
            File(outputDir(), "simple-error-${name.take(1)}.txt").writeText(it.stackTraceToString())
          }
          .getOrNull()
    }
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
            label.text = if (remote == null) "$name: CAPTURE FAILED" else name
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
          File(outputDir(), "simple-${page + 1}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
          }
        }
      }
      File(outputDir(), "simple-logcat.txt").writeText(shell("logcat -d"))
    }
  }

  @Composable
  private fun Panel(content: @Composable () -> Unit) {
    RemoteBox(RemoteModifier.fillMaxSize().background(Color(0xFFFFF4C0).rc)) {
      RemoteColumn(RemoteModifier.fillMaxSize().padding(8.rdp)) { content() }
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

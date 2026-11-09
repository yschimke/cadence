package ee.schimke.cadence.widgets

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ee.schimke.cadence.MainActivity
import ee.schimke.cadence.notifications.CadenceNotifications
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.livery.notifications.LiveryNotifications
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic: the notification template's document played by this process's own player, bare (top)
 * and in the landscape/portrait wrapper notifications need on Android 17 (bottom), to tell a
 * document the player can't draw from a wrapper that hides it. Saves `document-inapp.png`.
 */
@RunWith(AndroidJUnit4::class)
class NotificationDocumentDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test
  fun bareAndWrapped() {
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
    val open = body.contentIntent!!
    val render = runBlocking {
      LiveryNotifications(images = livery.images)
        .render(
          context,
          7100,
          heightDp = 104,
          plan = { slot ->
            livery.planner.forAppData(
              slot.query(
                NotificationTemplate.CONTRACT,
                minor = NotificationTemplate.CONTRACT_MINOR,
              ),
              NotificationTemplate.data(body, context.systemWidgetColors()),
            )
          },
          tapIntent = { _, _ -> open },
        )
    }
    File(outputDir(), "document-render.txt")
      .writeText("reason=${render.reason}\nplan=${render.plan}\nviews=${render.views}\n")
    val bare: RemoteViews = render.views ?: return assertNotNull(render.reason, render.views)
    val wrapped = RcLauncherWidgets.forNotification(bare.clone())

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val density = activity.resources.displayMetrics.density
        val column = LinearLayout(activity)
        column.orientation = LinearLayout.VERTICAL
        column.setBackgroundColor(Color.WHITE)
        for ((views, tint) in listOf(bare to 0xFFFFF0F0.toInt(), wrapped to 0xFFF0F0FF.toInt())) {
          val frame = FrameLayout(activity)
          frame.setBackgroundColor(tint)
          frame.addView(views.apply(activity, frame))
          column.addView(
            frame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (160 * density).toInt()),
          )
        }
        activity.addContentView(
          column,
          FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
          ),
        )
      }
      Thread.sleep(3_000)
      instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
        File(outputDir(), "document-inapp.png").outputStream().use {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
      }
    }
  }

  private fun outputDir(): File =
    (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: File(context.getExternalFilesDir(null), "notification-test"))
      .apply { mkdirs() }
}

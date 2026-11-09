package ee.schimke.cadence.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Posts each shape of Cadence notification through [CadenceNotifications.build], as the workers do,
 * and checks SystemUI keeps it: on Android 17 SystemUI cancels a notification whose Remote Compose
 * body it can't inflate ("Cannot add a nested view that has DrawInstructions") within a second.
 *
 * A screenshot of the open shade for each shape, and the inflation lines from logcat, land in the
 * test's additional output (`build/outputs/connected_android_test_additional_output`).
 */
@RunWith(AndroidJUnit4::class)
class NotificationBodyDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context: Context = instrumentation.targetContext
  private val manager = context.getSystemService(NotificationManager::class.java)

  @Before
  fun setUp() {
    // Remote Compose bodies need Android 16; earlier versions post plain notifications.
    assumeTrue(Build.VERSION.SDK_INT >= 36)
    shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    shell("logcat -c")
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL, "Device test", NotificationManager.IMPORTANCE_DEFAULT),
    )
  }

  @After
  fun tearDown() {
    shell("cmd statusbar collapse")
    manager.cancelAll()
  }

  @Test
  fun textOnly() =
    check("text", 7001, body("3 episodes ready", "Plug in your headphones to copy them"))

  @Test
  fun withProgress() =
    check("progress", 7002, body("Downloading", "Episode 2 of 5").copy(progress = 0.4f))

  @Test
  fun withProgressAndCancel() =
    check(
      "progress-cancel",
      7003,
      body("Processing", "Trimming silence…")
        .copy(
          progress = 0.7f,
          cancelIntent =
            PendingIntent.getBroadcast(
              context,
              1,
              Intent("ee.schimke.cadence.test.CANCEL").setPackage(context.packageName),
              PendingIntent.FLAG_IMMUTABLE,
            ),
        ),
    )

  private fun body(title: String, text: String) =
    NotificationBody(
      title,
      text,
      NotificationIcon.Download,
      contentIntent = CadenceNotifications.openApp(context),
    )

  private fun check(name: String, id: Int, body: NotificationBody) {
    val renderer = CadenceNotifications.renderer
    assertNotNull("the app sets a notification renderer at startup", renderer)
    val notification = runBlocking { CadenceNotifications.build(context, CHANNEL, id, body) }
    assertNotNull("$name: a Remote Compose body was drawn", notification.bigContentView)
    manager.notify(id, notification)

    shell("cmd statusbar expand-notifications")
    // SystemUI inflates off the main thread and cancels a failure within about half a second.
    Thread.sleep(4_000)
    screenshot(name)
    val log =
      shell("logcat -d -s NotifContentInflater:* NotifInflation:* RemoteViews:* AndroidRuntime:E")
    File(outputDir(), "$name-logcat.txt").writeText(log)
    File(outputDir(), "$name-logcat-all.txt").writeText(shell("logcat -d"))
    File(outputDir(), "$name-dumpsys.txt").writeText(shell("dumpsys notification --noredact"))

    inApp(name, notification.bigContentView)

    assertTrue(
      "$name: SystemUI dropped the notification\n$log",
      manager.activeNotifications.any { it.id == id },
    )
    assertTrue("$name: SystemUI couldn't inflate the body\n$log", "DrawInstructions" !in log)
  }

  /** The same views played by this process's own player, to tell the document from SystemUI. */
  private fun inApp(name: String, views: android.widget.RemoteViews) {
    shell("cmd statusbar collapse")
    try {
      androidx.test.core.app.ActivityScenario.launch(ee.schimke.cadence.MainActivity::class.java)
        .use { scenario ->
          scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            val frame = android.widget.FrameLayout(activity)
            frame.setBackgroundColor(android.graphics.Color.WHITE)
            frame.addView(views.apply(activity, frame))
            activity.addContentView(
              frame,
              android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (200 * density).toInt(),
                android.view.Gravity.CENTER,
              ),
            )
          }
          Thread.sleep(3_000)
          screenshot("$name-inapp")
        }
    } catch (e: Throwable) {
      File(outputDir(), "$name-inapp-error.txt").writeText(e.stackTraceToString())
    }
  }

  private fun screenshot(name: String) {
    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
    File(outputDir(), "$name.png").outputStream().use {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  private fun outputDir(): File =
    (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: File(context.getExternalFilesDir(null), "notification-test"))
      .apply { mkdirs() }

  private fun shell(command: String): String =
    ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
      )
      .bufferedReader()
      .use { it.readText() }

  private companion object {
    const val CHANNEL = "device-test"
  }
}

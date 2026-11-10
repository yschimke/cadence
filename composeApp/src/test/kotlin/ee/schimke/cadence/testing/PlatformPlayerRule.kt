package ee.schimke.cadence.testing

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.platformWidgetDocument
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Plays Remote Compose documents on the platform's own player, the one behind `RemoteViews` draw
 * instructions that a launcher and SystemUI's notifications use, and says what it drew. That player
 * has no error for a document it can't lay out: it draws nothing, so a test has to look.
 *
 * Robolectric runs the real player for Android 16 and 17. Put the SDKs on the class and every test
 * runs once on each:
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [PlatformPlayerRule.ANDROID_16, PlatformPlayerRule.ANDROID_17], qualifiers = "mdpi")
 * class MyWidgetTest {
 *   @get:Rule val player = PlatformPlayerRule()
 *
 *   @Test fun draws() {
 *     player.widget(document, widthPx = 320, heightPx = 110).assertDraws()
 *   }
 * }
 * ```
 *
 * [widget] and [notification] publish the document as the app does; [asIs] hands the player the
 * bytes untouched, for showing what the publishing path recovers. Each play is saved as a PNG under
 * `build/platform-player/sdk<N>/`, named for the test.
 *
 * Play one animated document per test: a second in the same JVM can run it out of memory.
 */
class PlatformPlayerRule : TestWatcher() {
  private var test = "test"
  private var plays = 0

  override fun starting(description: Description) {
    test = "${description.testClass.simpleName}.${description.methodName.substringBefore('[')}"
    plays = 0
  }

  /** [document] as a launcher widget: `RcLauncherWidgets.remoteViews`. */
  fun widget(document: ByteArray, widthPx: Int, heightPx: Int, label: String = ""): Played =
    play(RcLauncherWidgets.remoteViews(document), widthPx, heightPx, label)

  /** [document] as a notification's custom body: `RcLauncherWidgets.notificationRemoteViews`. */
  fun notification(document: ByteArray, widthPx: Int, heightPx: Int, label: String = ""): Played =
    play(RcLauncherWidgets.notificationRemoteViews(document), widthPx, heightPx, label)

  /** [document] handed to the player as it is, with none of the publishing path's recovery. */
  fun asIs(document: ByteArray, widthPx: Int, heightPx: Int, label: String = ""): Played =
    play(
      RemoteViews(
        RemoteViews.DrawInstructions.Builder(
            listOf(platformWidgetDocument(document, RcLauncherWidgets.targetFor())),
          )
          .build(),
      ),
      widthPx,
      heightPx,
      label,
    )

  /** [views] inflated and drawn at [widthPx] × [heightPx] over white. */
  fun play(views: RemoteViews, widthPx: Int, heightPx: Int, label: String = ""): Played {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val frame = FrameLayout(activity)
    frame.setBackgroundColor(Color.WHITE)
    frame.addView(views.apply(activity, frame))
    activity.setContentView(frame, FrameLayout.LayoutParams(widthPx, heightPx))
    runMainThread()
    frame.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
    )
    frame.layout(0, 0, widthPx, heightPx)
    runMainThread()
    val image = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    // The first frame lays out and paints; the second draws what the first asked to repaint.
    repeat(2) { frame.draw(Canvas(image)) }
    val name =
      listOf(test, label.ifEmpty { if (plays == 0) "" else "$plays" }).filter { it.isNotEmpty() }
    plays++
    val file =
      File("build/platform-player/sdk${Build.VERSION.SDK_INT}", "${name.joinToString("-")}.png")
    file.parentFile.mkdirs()
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Played(image, file)
  }

  /**
   * Runs what is waiting on the main thread, up to a limit: a document that repaints every frame
   * posts the next frame from each one, so running "until idle" would never return.
   */
  private fun runMainThread() {
    val looper = shadowOf(Looper.getMainLooper())
    repeat(MAIN_THREAD_TASKS) { looper.runOneTask() }
  }

  /** What a play drew, over the white it was drawn on. */
  class Played(val image: Bitmap, val file: File) {
    /** How many pixels were drawn. */
    val ink: Int
      get() = ink(Rect(0, 0, image.width, image.height))

    /** How many pixels were drawn inside [region]. */
    fun ink(region: Rect): Int {
      val pixels = IntArray(region.width() * region.height())
      image.getPixels(
        pixels,
        0,
        region.width(),
        region.left,
        region.top,
        region.width(),
        region.height(),
      )
      return pixels.count { it != Color.WHITE }
    }

    /**
     * Fails when the player drew nothing worth the name: what a blank widget or body looks like.
     */
    fun assertDraws(what: String = "the document") {
      check(ink > MIN_INK) {
        "$what is blank on Android API ${Build.VERSION.SDK_INT} ($ink pixels drawn): $file"
      }
    }

    /** Fails when the player drew anything inside [region]. */
    fun assertBlank(region: Rect, what: String) {
      val drawn = ink(region)
      check(drawn <= NOISE) {
        "$what is drawn on Android API ${Build.VERSION.SDK_INT} ($drawn pixels in $region): $file"
      }
    }

    /** Fails when the player drew nothing inside [region]. */
    fun assertDraws(region: Rect, what: String) {
      val drawn = ink(region)
      check(drawn > NOISE) {
        "$what is missing on Android API ${Build.VERSION.SDK_INT} ($drawn pixels in $region): $file"
      }
    }
  }

  companion object {
    /** Robolectric SDK levels with a platform Remote Compose player. */
    const val ANDROID_16 = 36
    const val ANDROID_17 = 37

    /** An icon alone is over a thousand pixels; antialiasing noise is nowhere near. */
    const val MIN_INK = 500

    /** Stray antialiased pixels at a region's edge. */
    const val NOISE = 8

    /** More than inflating and laying out a document posts; a few frames of one that animates. */
    private const val MAIN_THREAD_TASKS = 64
  }
}

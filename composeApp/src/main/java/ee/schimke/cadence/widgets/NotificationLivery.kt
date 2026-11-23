package ee.schimke.cadence.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Parcel
import android.util.TypedValue
import android.widget.RemoteViews
import ee.schimke.cadence.R
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationBodyRenderer
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.livery.notifications.LiveryNotifications
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Every Cadence notification's expanded body, drawn from the `cadence/notification` Livery template
 * (`composeApp/livery/designs/notification-default`) and played by SystemUI as Remote Compose on
 * Android 16 and later. Earlier versions, or a template that can't be drawn, get no body, and the
 * notification is posted plain.
 *
 * Taps need no receiver: the body's Open and Cancel run the notification's own `PendingIntent`s.
 */
internal class NotificationLivery(private val livery: WidgetLivery) : NotificationBodyRenderer {
  private val notifications = LiveryNotifications(images = livery.images)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override suspend fun render(
    context: Context,
    notificationId: Int,
    body: NotificationBody,
  ): RemoteViews? {
    val open = body.contentIntent ?: return null
    val render =
      notifications.render(
        context,
        notificationId,
        heightDp = heightDp(body, context.resources.configuration),
        plan = { slot ->
          livery.planner
            .forAppData(
              slot.query(
                NotificationTemplate.CONTRACT,
                minor = NotificationTemplate.CONTRACT_MINOR,
              ),
              NotificationTemplate.data(body, context.systemWidgetColors()),
            )
            .also(livery::drawn)
        },
        tapIntent = { _, action ->
          if (action == NotificationTemplate.CANCEL) body.cancelIntent ?: open else open
        },
      )
    // The bundled catalog holds the template, so this is only a newer download; the next post
    // (progress updates are frequent) draws it.
    if (render.hasMisses) scope.launch { render.fetchMissed() }
    // Already wrapped for SystemUI's template (RcLauncherWidgets.notificationRemoteViews): Android
    // 17 refuses a nested view that has DrawInstructions, crashing a foreground service. The
    // wrapper doesn't carry that flag, so it nests in a plain layout as it does in SystemUI's.
    val views = render.views ?: return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return views
    // SystemUI's player gives the body every dp it is offered (up to 256), whatever height the
    // document asks for, which left a short download note a screen tall. A container of the
    // body's own height is all the player is offered.
    val wrapped =
      RemoteViews(context.packageName, R.layout.notification_body).apply {
        // An update can be reapplied to the shown views, where addView appends: clear first, so
        // progress posts don't stack bodies.
        removeAllViews(R.id.notification_body)
        addView(R.id.notification_body, views)
        setViewLayoutHeight(
          R.id.notification_body,
          heightDp(body, context.resources.configuration).toFloat(),
          TypedValue.COMPLEX_UNIT_DIP,
        )
      }
    // Some Android 17 builds refuse a Remote Compose body nested in a notification ("Nested
    // RemoteViews cannot contain DrawInstructions"): NotificationManager.notify throws and a
    // foreground service's parcelled notification crashes the app. Posting is where it is read
    // back, so read it back here and post plain where the platform won't take it.
    return wrapped.takeIf { it.survivesParcel() }
  }

  /** Whether this Android accepts these views once a notification carries them across a parcel. */
  private fun RemoteViews.survivesParcel(): Boolean {
    val parcel = Parcel.obtain()
    return try {
      writeToParcel(parcel, 0)
      parcel.setDataPosition(0)
      RemoteViews.CREATOR.createFromParcel(parcel)
      true
    } catch (_: IllegalArgumentException) {
      false
    } finally {
      parcel.recycle()
    }
  }

  /**
   * Room for what this body shows: title, the text's wrapped lines (at most three) and the optional
   * swim lane, at the shade's width and the user's font scale.
   */
  internal fun heightDp(body: NotificationBody, configuration: Configuration): Int =
    notificationBodyHeightDp(body, configuration.screenWidthDp, configuration.fontScale)
}

internal fun notificationBodyHeightDp(
  body: NotificationBody,
  screenWidthDp: Int,
  fontScale: Float,
): Int {
  val scale = fontScale.coerceAtLeast(1f)
  // The shade's insets and the app icon column take about 120dp; a 13sp line holds about one
  // character per 6.5sp.
  val textWidthDp = (screenWidthDp - 120).coerceAtLeast(NotificationTemplate.MIN_WIDTH_DP - 40)
  val perLine = (textWidthDp / (6.5f * scale)).toInt().coerceAtLeast(12)
  val textLines =
    if (body.text.isBlank()) 0 else ((body.text.length + perLine - 1) / perLine).coerceIn(1, 3)
  // Padding, a 15sp title, the 2dp gap, the 13sp lines, and a little slack for descenders.
  val textHeight = 8 + 21 * scale + 2 + textLines * 18 * scale + 4
  val progressHeight = if (body.progress != null) 8 + NotificationTemplate.LANE_HEIGHT_DP else 0
  return ceil(textHeight + progressHeight)
    .toInt()
    .coerceIn(NotificationTemplate.MIN_HEIGHT_DP, NotificationTemplate.MAX_HEIGHT_DP)
}

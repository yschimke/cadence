package ee.schimke.cadence.widgets

import android.content.Context
import android.widget.RemoteViews
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationBodyRenderer
import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.livery.notifications.LiveryNotifications
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
        heightDp = heightDp(body),
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
    // SystemUI nests every custom view in its template, and Android 17 refuses a nested view that
    // has DrawInstructions ("Couldn't inflate contentViews"), crashing a foreground service. The
    // landscape/portrait wrapper carries no DrawInstructions flag of its own, so it nests.
    return render.views?.let(RcLauncherWidgets::forNotification)
  }

  /** Room for what this body shows: the text, then the bar and the Cancel button. */
  private fun heightDp(body: NotificationBody): Int =
    88 + (if (body.progress != null) 16 else 0) + (if (body.cancelIntent != null) 46 else 0)
}

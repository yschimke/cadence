package ee.schimke.cadence.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What one notification says. Every Cadence notification is built from one of these, so the same
 * values fill the Remote Compose body ([NotificationTemplate]) and the plain notification that
 * the collapsed view, the lock screen, a watch and older Android show.
 */
data class NotificationBody(
  val title: String,
  val text: String,
  val icon: NotificationIcon,
  /** 0..1 for a progress bar; null for none. */
  val progress: Float? = null,
  /** Busy with no known fraction: the collapsed view's bar spins; the body shows no bar. */
  val indeterminate: Boolean = false,
  val contentIntent: PendingIntent? = null,
  /** Offers Cancel, in the body and as a notification action. */
  val cancelIntent: PendingIntent? = null,
)

/** Draws [NotificationBody] as Remote Compose `RemoteViews`, or null when it can't (pre-16). */
fun interface NotificationBodyRenderer {
  suspend fun render(context: Context, notificationId: Int, body: NotificationBody): RemoteViews?
}

object CadenceNotifications {
  /** Set by the app at startup (Livery's notification bodies); null draws plain notifications. */
  @Volatile var renderer: NotificationBodyRenderer? = null

  /**
   * [body] as a notification on [channel] with the Remote Compose body when the renderer can draw
   * one. [configure] adds what only the notification knows (ongoing, auto-cancel, category).
   */
  suspend fun build(
    context: Context,
    channel: String,
    notificationId: Int,
    body: NotificationBody,
    configure: NotificationCompat.Builder.() -> Unit = {},
  ): Notification {
    val views =
      try {
        // Off the caller's thread: planning reads the catalog from disk.
        renderer?.let { withContext(Dispatchers.Default) { it.render(context, notificationId, body) } }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }
    return builder(context, channel, body, views).apply(configure).build()
  }

  /** [build] without the Remote Compose body, for a foreground service's synchronous first post. */
  fun buildPlain(
    context: Context,
    channel: String,
    body: NotificationBody,
    configure: NotificationCompat.Builder.() -> Unit = {},
  ): Notification = builder(context, channel, body, null).apply(configure).build()

  private fun builder(
    context: Context,
    channel: String,
    body: NotificationBody,
    views: RemoteViews?,
  ): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channel)
      .setSmallIcon(body.icon.smallIcon)
      .setContentTitle(body.title)
      .setContentText(body.text)
      .setContentIntent(body.contentIntent)
      .setOnlyAlertOnce(true)
      .apply {
        if (body.progress != null || body.indeterminate)
          setProgress(100, ((body.progress ?: 0f) * 100).toInt(), body.progress == null)
        body.cancelIntent?.let {
          addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", it)
        }
        // No style alongside the custom body. SystemUI nests it in a template either way, so the
        // renderer must hand back views that nest (see `RcLauncherWidgets.forNotification`).
        if (views != null) setCustomBigContentView(views)
        else setStyle(NotificationCompat.BigTextStyle().bigText(body.text))
      }

  private val NotificationIcon.smallIcon: Int
    get() =
      when (this) {
        NotificationIcon.Download -> android.R.drawable.stat_sys_download
        NotificationIcon.Process -> android.R.drawable.stat_sys_download
        NotificationIcon.Sync -> android.R.drawable.stat_sys_upload
        NotificationIcon.Done -> android.R.drawable.stat_sys_download_done
        NotificationIcon.Error -> android.R.drawable.stat_notify_error
      }

  /** Opens Cadence, for a notification's body and its taps. */
  fun openApp(context: Context): PendingIntent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
      PendingIntent.getActivity(
        context,
        0,
        it,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }
}

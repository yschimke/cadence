package ee.schimke.cadence.devices

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Ejects the plugged-in USB drive by pressing Eject on the system's own "drive" notification.
 *
 * Android reserves programmatic unmounting for system apps, but the notification SystemUI posts
 * for a mounted drive carries an Eject action, and an app with notification access may read active
 * notifications and fire their actions. The listener lives in its own process
 * ([ee.schimke.cadence.bluetooth.NowPlayingNotificationListener]), so the app asks it over an
 * app-private ordered broadcast and learns from the result code whether it found one.
 */
object UsbEject {
  private const val TAG = "CadenceUsbEject"
  private const val ACTION = "ee.schimke.cadence.action.EJECT_USB"

  /** SystemUI's storage notification channel (`NotificationChannels.STORAGE`). */
  private const val STORAGE_CHANNEL = "STO"

  /** Storage notifications are tagged with the volume id; a removable drive is `public:major,minor`. */
  private const val PUBLIC_VOLUME_TAG_PREFIX = "public:"

  private const val RESULT_NOT_FOUND = 0
  private const val RESULT_EJECTED = 1

  /** Whether [channelId] or [tag] mark a notification as the system's removable-drive one. */
  fun isUsbStorageNotification(channelId: String?, tag: String?): Boolean =
    channelId == STORAGE_CHANNEL || tag?.startsWith(PUBLIC_VOLUME_TAG_PREFIX) == true

  /**
   * The Eject action among [actions]: the one titled [label] (the system's own translation), else
   * "Eject". Null when there isn't one, so nothing else on the notification is ever pressed.
   */
  fun findEjectAction(actions: Array<Notification.Action>?, label: String?): Notification.Action? =
    actions?.firstOrNull { action ->
      val title = action.title?.toString().orEmpty()
      title.equals("Eject", ignoreCase = true) || (label != null && title.equals(label, true))
    }

  /** The system's translated "Eject", when this Android exposes it. */
  private fun systemEjectLabel(): String? =
    runCatching {
        val resources = android.content.res.Resources.getSystem()
        val id = resources.getIdentifier("ext_media_unmount_action", "string", "android")
        if (id != 0) resources.getString(id) else null
      }
      .getOrNull()

  /** Fires Eject on the first removable-drive notification in [active]; false if there's none. */
  fun ejectFrom(active: Array<StatusBarNotification>): Boolean {
    val label = systemEjectLabel()
    for (sbn in active) {
      val notification = sbn.notification ?: continue
      if (!isUsbStorageNotification(notification.channelId, sbn.tag)) continue
      val action = findEjectAction(notification.actions, label) ?: continue
      val intent = action.actionIntent ?: continue
      return try {
        intent.send()
        true
      } catch (e: android.app.PendingIntent.CanceledException) {
        Log.w(TAG, "Eject action was cancelled", e)
        false
      }
    }
    return false
  }

  /**
   * Called from the notification listener once it is connected: answers [requestEject] while it
   * lives. Returns the receiver to pass to [unregisterListenerReceiver].
   */
  fun registerListenerReceiver(listener: NotificationListenerService): BroadcastReceiver {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val ejected =
            runCatching { ejectFrom(listener.activeNotifications) }
              .onFailure { Log.w(TAG, "Couldn't read notifications", it) }
              .getOrDefault(false)
          resultCode = if (ejected) RESULT_EJECTED else RESULT_NOT_FOUND
        }
      }
    listener.registerReceiver(
      receiver,
      IntentFilter(ACTION),
      Context.RECEIVER_NOT_EXPORTED,
    )
    return receiver
  }

  fun unregisterListenerReceiver(listener: NotificationListenerService, receiver: BroadcastReceiver) {
    runCatching { listener.unregisterReceiver(receiver) }
  }

  /**
   * Asks the notification listener to press Eject. [onNotFound] runs on the main thread when it
   * couldn't: no notification access (nothing is listening), or no drive notification.
   */
  fun requestEject(context: Context, onNotFound: () -> Unit) {
    val result =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (resultCode != RESULT_EJECTED) onNotFound()
        }
      }
    context.sendOrderedBroadcast(
      Intent(ACTION).setPackage(context.packageName),
      null,
      result,
      Handler(Looper.getMainLooper()),
      RESULT_NOT_FOUND,
      null,
      null,
    )
  }
}

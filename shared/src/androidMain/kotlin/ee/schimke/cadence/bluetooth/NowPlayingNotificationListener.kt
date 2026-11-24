package ee.schimke.cadence.bluetooth

import android.content.BroadcastReceiver
import android.service.notification.NotificationListenerService
import ee.schimke.cadence.devices.UsbEject

/**
 * Notification-listener implementation. Being a registered ComponentName is what lets
 * [android.media.session.MediaSessionManager] return active sessions to the app once the user
 * grants the "Notification access" permission in system settings.
 *
 * It also reads the system's USB drive notification to press its Eject action ([UsbEject]), which
 * is the only way an ordinary app can eject a drive.
 */
class NowPlayingNotificationListener : NotificationListenerService() {
  private var ejectReceiver: BroadcastReceiver? = null

  override fun onListenerConnected() {
    ejectReceiver = ejectReceiver ?: UsbEject.registerListenerReceiver(this)
  }

  override fun onListenerDisconnected() {
    ejectReceiver?.let { UsbEject.unregisterListenerReceiver(this, it) }
    ejectReceiver = null
  }
}

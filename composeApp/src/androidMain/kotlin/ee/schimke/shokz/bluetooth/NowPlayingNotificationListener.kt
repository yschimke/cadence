package ee.schimke.shokz.bluetooth

import android.service.notification.NotificationListenerService

/**
 * Empty notification-listener implementation. Its only purpose is to be a
 * registered ComponentName so that [android.media.session.MediaSessionManager]
 * will return active sessions to the app once the user grants the
 * "Notification access" permission in system settings.
 */
class NowPlayingNotificationListener : NotificationListenerService()

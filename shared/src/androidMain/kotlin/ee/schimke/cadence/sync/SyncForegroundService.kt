package ee.schimke.cadence.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ee.schimke.cadence.notifications.CadenceNotifications
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import androidx.core.content.getSystemService
import ee.schimke.cadence.metro.AndroidAppGraph
import ee.schimke.cadence.metro.AppGraphProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var observer: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    ensureChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, buildNotification(initialText()), foregroundType())
    val targetId = intent?.getStringExtra(EXTRA_TARGET_DEVICE_ID)
    val orchestrator = appGraph().syncOrchestrator
    observer?.cancel()
    observer = scope.launch {
      var hasStarted = false
      launch { orchestrator.startSync(targetId) }
      orchestrator.progress.collectLatest { progress ->
        if (progress.running) hasStarted = true
        updateNotification(progress)
        if (hasStarted && !progress.running) {
          stopForeground(STOP_FOREGROUND_DETACH)
          stopSelf()
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun appGraph(): AndroidAppGraph = (applicationContext as AppGraphProvider).appGraph

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val mgr = getSystemService<NotificationManager>() ?: return
    if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
      mgr.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            "File sync",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "Progress of USB file sync to the connected device" },
      )
    }
  }

  private fun foregroundType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    else 0

  private fun initialText() = "Preparing sync…"

  private fun body(text: String, max: Int = 0, current: Int = 0, error: Boolean = false) =
    NotificationBody(
      "Syncing files",
      text,
      if (error) NotificationIcon.Error else NotificationIcon.Sync,
      progress = if (max > 0) current.toFloat() / max else null,
      indeterminate = max == 0 && !error,
      contentIntent = CadenceNotifications.openApp(this),
    )

  private val configure: NotificationCompat.Builder.() -> Unit = {
    setOngoing(true)
    setCategory(NotificationCompat.CATEGORY_PROGRESS)
  }

  /** The first post is synchronous (startForeground), so plain; updates get the Livery body. */
  private fun buildNotification(text: String): Notification =
    CadenceNotifications.buildPlain(this, CHANNEL_ID, body(text), configure)

  private suspend fun updateNotification(progress: SyncProgress) {
    val mgr = getSystemService<NotificationManager>() ?: return
    val text =
      when {
        progress.lastError != null -> "Error: ${progress.lastError}"
        !progress.running && progress.filesTotal > 0 ->
          "Done — ${progress.filesCompleted}/${progress.filesTotal} files"
        progress.currentFileName != null ->
          "${progress.currentFileName} (${progress.filesCompleted + 1}/${progress.filesTotal})"
        else -> initialText()
      }
    val notif =
      CadenceNotifications.build(
        this,
        CHANNEL_ID,
        NOTIFICATION_ID,
        body(
          text,
          max = progress.filesTotal,
          current = progress.filesCompleted,
          error = progress.lastError != null,
        ),
        configure,
      )
    mgr.notify(NOTIFICATION_ID, notif)
  }

  companion object {
    private const val CHANNEL_ID = "cadence_sync"
    private const val NOTIFICATION_ID = 0xC0DE
    const val EXTRA_TARGET_DEVICE_ID = "ee.schimke.cadence.sync.TARGET_DEVICE_ID"

    fun start(context: Context, targetDeviceId: String? = null) {
      val intent =
        Intent(context, SyncForegroundService::class.java).apply {
          if (targetDeviceId != null) putExtra(EXTRA_TARGET_DEVICE_ID, targetDeviceId)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }
}

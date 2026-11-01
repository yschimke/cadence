package ee.schimke.cadence.podcasts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.time.ZonedDateTime
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val WORK = "podcasts-overnight"
private const val PREFS = "podcast-overnight"

fun scheduleOvernightPodcasts(context: Context) {
  val app = context.applicationContext
  val work = WorkManager.getInstance(app)
  if (!app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)) {
    work.cancelUniqueWork(WORK)
    return
  }
  val now = ZonedDateTime.now()
  var next = now.toLocalDate().atTime(2, 0).atZone(now.zone)
  if (!next.isAfter(now)) next = next.plusDays(1)
  val request = PeriodicWorkRequestBuilder<OvernightPodcastWorker>(24, TimeUnit.HOURS)
    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
    .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiredNetworkType(NetworkType.UNMETERED).build())
    .build()
  work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
}

class OvernightPodcastWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    if (ZonedDateTime.now().hour !in 0..6) return Result.success()
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("podcast-preparation", "Podcast preparation", NotificationManager.IMPORTANCE_LOW))
    val notification = NotificationCompat.Builder(applicationContext, "podcast-preparation")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("Preparing podcasts")
      .setContentText("Cadence is preparing your podcasts while charging")
      .setOngoing(true).build()
    setForeground(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(9012, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(9012, notification))
    val controller = AndroidPodcastPlatform.instance(applicationContext).controller()
    val ok = controller.run {
      if (!loaded.value) load()
      if (state.value.library.playlist != null) {
        if (canRefresh) refreshSource()
        prepare()
      }
    }
    return if (ok) Result.success() else Result.retry()
  }
}

@Composable actual fun OvernightPodcastOptions() {
  val context = LocalContext.current.applicationContext
  val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
  var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Checkbox(enabled, onCheckedChange = {
      enabled = it
      prefs.edit().putBoolean("enabled", it).apply()
      scheduleOvernightPodcasts(context)
    })
    Column {
      Text("Prepare overnight", style = MaterialTheme.typography.titleSmall)
      Text("Around 2am while charging on Wi-Fi. You can still prepare now.", style = MaterialTheme.typography.bodySmall)
    }
  }
}

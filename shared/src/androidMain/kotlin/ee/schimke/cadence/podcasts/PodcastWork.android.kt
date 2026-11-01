package ee.schimke.cadence.podcasts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.work.*

internal const val WORK_NAME = "podcast-prepare-now"
internal const val TASK = "task"
internal const val ERROR = "error"

internal fun enqueuePodcastWork(context: Context, task: String): Operation {
  val request = OneTimeWorkRequestBuilder<PodcastPreparationWorker>()
    .setInputData(workDataOf(TASK to task))
    .build()
  return WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

@Composable
actual fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi {
  val context = LocalContext.current.applicationContext
  val work = remember(context) { WorkManager.getInstance(context) }
  val infos by remember(work) { work.getWorkInfosForUniqueWorkFlow(WORK_NAME) }.collectAsState(emptyList())
  val current = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
  val active = current?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
  val status by controller.status.collectAsState()
  return PodcastWorkUi(
    supportsDownload = true,
    active = active,
    status = when {
      current?.state == WorkInfo.State.ENQUEUED || current?.state == WorkInfo.State.BLOCKED -> "Waiting to start…"
      current?.state == WorkInfo.State.RUNNING -> status
      current?.state == WorkInfo.State.FAILED -> current.outputData.getString(ERROR) ?: "Could not complete. Try again."
      else -> null
    },
    download = { enqueuePodcastWork(context, "download") },
    process = { enqueuePodcastWork(context, "process") },
    refresh = { enqueuePodcastWork(context, "refresh") },
  )
}

class PodcastPreparationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val task = inputData.getString(TASK)
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("podcast-preparation", "Podcast preparation", NotificationManager.IMPORTANCE_LOW))
    val notification = NotificationCompat.Builder(applicationContext, "podcast-preparation")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(when (task) { "download" -> "Downloading podcasts"; "refresh" -> "Refreshing podcasts"; else -> "Processing podcasts" })
      .setContentText("Cadence is working in the background")
      .setOngoing(true).build()
    setForeground(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(9013, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(9013, notification))
    val controller = AndroidPodcastPlatform.instance(applicationContext).controller()
    val ok = controller.run {
      if (!loaded.value) load()
      when (task) {
        "download" -> downloadOriginals()
        "refresh" -> {
          status.value = "Refreshing source…"
          platform.publishStatus(status.value, state.value)
          refreshSource()
        }
        "process" -> prepare()
        else -> throw PodcastProblem("Unknown podcast task")
      }
    }
    return if (ok) Result.success() else Result.failure(workDataOf(ERROR to controller.status.value))
  }
}

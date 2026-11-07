package ee.schimke.cadence.podcasts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal const val WORK_NAME = "podcast-prepare-now"
internal const val TASK = "task"
internal const val ERROR = "error"
internal const val PROGRESS_STATUS = "progress-status"

internal fun enqueuePodcastWork(context: Context, task: String): Operation {
  val request =
    OneTimeWorkRequestBuilder<PodcastPreparationWorker>()
      .setInputData(workDataOf(TASK to task))
      .apply {
        if (task == "download" || task == "refresh") {
          setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
          )
        }
      }
      .build()
  return WorkManager.getInstance(context.applicationContext)
    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

@Composable
actual fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi {
  val context = LocalContext.current.applicationContext
  val work = remember(context) { WorkManager.getInstance(context) }
  val infos by
    remember(work) { work.getWorkInfosForUniqueWorkFlow(WORK_NAME) }.collectAsState(emptyList())
  val current = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
  val active =
    current?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
  val status by controller.status.collectAsState()
  val fraction by controller.progress.collectAsState()
  // Notification permission is optional for the work itself. Start it even if the user declines.
  // Saveable: the permission result can arrive in a recreated activity.
  var pendingTask by rememberSaveable { mutableStateOf("download") }
  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      enqueuePodcastWork(context, pendingTask)
    }
  // Asked once, with the first long task: it shows progress and, later, the overnight "ready" note.
  fun withNotifications(task: String) {
    if (
      Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      pendingTask = task
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      enqueuePodcastWork(context, task)
    }
  }
  return PodcastWorkUi(
    supportsDownload = true,
    active = active,
    status =
      when (current?.state) {
        // Only network tasks have constraints, so a wait is almost always for a connection.
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED -> "Waiting for a network connection…"
        WorkInfo.State.RUNNING -> status
        else -> null
      },
    fraction = fraction.takeIf { current?.state == WorkInfo.State.RUNNING },
    error =
      current
        ?.takeIf { it.state == WorkInfo.State.FAILED }
        ?.let {
          it.outputData.getString(ERROR) ?: "Couldn't finish. Try again."
        },
    failedTask = current?.takeIf { it.state == WorkInfo.State.FAILED }?.outputData?.getString(TASK),
    // Forgets finished work, which is all that holds the error.
    dismissError = { work.pruneWork() },
    download = { withNotifications("download") },
    process = { withNotifications("process") },
    refresh = { enqueuePodcastWork(context, "refresh") },
  )
}

class PodcastPreparationWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val task = inputData.getString(TASK)
    val manager =
      applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
      NotificationChannel(
        "podcast-preparation",
        "Podcast preparation",
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
    val title =
      when (task) {
        "download" -> "Downloading podcasts"
        "refresh" -> "Refreshing podcasts"
        else -> "Processing podcasts"
      }
    val openIntent =
      applicationContext.packageManager
        .getLaunchIntentForPackage(applicationContext.packageName)
        ?.let {
          PendingIntent.getActivity(
            applicationContext,
            0,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        }
    fun notification(message: String, fraction: Float? = null) =
      NotificationCompat.Builder(applicationContext, "podcast-preparation")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setContentIntent(openIntent)
        .setOnlyAlertOnce(true)
        .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
        .setOngoing(true)
        .addAction(
          android.R.drawable.ic_menu_close_clear_cancel,
          "Cancel",
          WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
        )
        .build()
    val initial = notification("Preparing…")
    setForeground(
      if (Build.VERSION.SDK_INT >= 29)
        ForegroundInfo(9013, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
      else ForegroundInfo(9013, initial),
    )
    val controller = AndroidPodcastPlatform.instance(applicationContext).controller()
    val ok = coroutineScope {
      val updates =
        launch(start = CoroutineStart.UNDISPATCHED) {
          // Percent updates arrive often; the notification only needs whole steps.
          combine(controller.status, controller.progress) { message, fraction ->
              message to fraction
            }
            .drop(1)
            .distinctUntilChanged()
            .collect { (message, fraction) ->
              setProgress(workDataOf(PROGRESS_STATUS to message))
              manager.notify(9013, notification(message, fraction))
            }
        }
      try {
        controller.run {
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
      } finally {
        updates.cancel()
      }
    }
    if (ok) return Result.success()
    val error = controller.status.value
    // The progress notification goes with the worker; leave one behind that says what went wrong.
    if (
      Build.VERSION.SDK_INT < 33 ||
        applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
      manager.notify(
        9014,
        NotificationCompat.Builder(applicationContext, "podcast-preparation")
          .setSmallIcon(android.R.drawable.stat_notify_error)
          .setContentTitle(
            when (task) {
              "download" -> "Download stopped"
              "refresh" -> "Refresh failed"
              else -> "Processing stopped"
            },
          )
          .setContentText(error)
          .setStyle(NotificationCompat.BigTextStyle().bigText(error))
          .setContentIntent(openIntent)
          .setAutoCancel(true)
          .build(),
      )
    return Result.failure(workDataOf(ERROR to error, TASK to task))
  }
}

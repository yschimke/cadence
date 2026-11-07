package ee.schimke.cadence.podcasts

import ee.schimke.cadence.notifications.CadenceNotifications
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.platform.Analytics
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
import androidx.compose.ui.platform.LocalInspectionMode
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
  // A preview has no CadenceApplication to configure WorkManager (the app disables its automatic
  // initializer), so getInstance would throw. Nothing runs in a preview: show the idle state, with
  // the same buttons the app shows.
  if (LocalInspectionMode.current) return PodcastWorkUi(supportsDownload = true)
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
    val openIntent = CadenceNotifications.openApp(applicationContext)
    val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
    suspend fun notification(message: String, fraction: Float? = null) =
      CadenceNotifications.build(
        applicationContext,
        "podcast-preparation",
        PROGRESS_NOTIFICATION,
        NotificationBody(
          title,
          message,
          if (task == "process") NotificationIcon.Process else NotificationIcon.Download,
          progress = fraction,
          indeterminate = fraction == null,
          contentIntent = openIntent,
          cancelIntent = cancelIntent,
        ),
      ) {
        setOngoing(true)
      }
    val initial = notification("Preparing…")
    setForeground(
      if (Build.VERSION.SDK_INT >= 29)
        ForegroundInfo(PROGRESS_NOTIFICATION, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
      else ForegroundInfo(PROGRESS_NOTIFICATION, initial),
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
              manager.notify(PROGRESS_NOTIFICATION, notification(message, fraction))
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
    Analytics.current.event("podcast_task", mapOf("task" to (task ?: "unknown"), "ok" to ok))
    if (ok) return Result.success()
    val error = controller.status.value
    // The progress notification goes with the worker; leave one behind that says what went wrong.
    if (
      Build.VERSION.SDK_INT < 33 ||
        applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
      manager.notify(
        ERROR_NOTIFICATION,
        CadenceNotifications.build(
          applicationContext,
          "podcast-preparation",
          ERROR_NOTIFICATION,
          NotificationBody(
            when (task) {
              "download" -> "Download stopped"
              "refresh" -> "Refresh failed"
              else -> "Processing stopped"
            },
            error,
            NotificationIcon.Error,
            contentIntent = openIntent,
          ),
        ) {
          setAutoCancel(true)
        },
      )
    return Result.failure(workDataOf(ERROR to error, TASK to task))
  }
}

/** The podcast worker's progress, and what it leaves behind when it fails. */
private const val PROGRESS_NOTIFICATION = 9013

/** Its own id: it shared 9014 with [READY_NOTIFICATION], so each replaced the other. */
private const val ERROR_NOTIFICATION = 9015

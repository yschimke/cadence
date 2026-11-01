package ee.schimke.cadence.podcasts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Launcher counterpart of the cloud card. All actions schedule the same unique WorkManager job. */
class CloudSyncWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)
  override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context, manager, intArrayOf(id))
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != RcLauncherWidgets.ACTION_WIDGET_ACTION) { super.onReceive(context, intent); return }
    val task = intent.getStringExtra(RcLauncherWidgets.EXTRA_ACTION_PAYLOAD)
    if (task !in setOf("refresh", "download", "process")) return
    val pending = goAsync()
    scope.launch {
      try { withContext(Dispatchers.IO) { enqueuePodcastWork(context, task!!).result.get() } }
      finally { pending.finish() }
    }
  }
  private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val pending = goAsync()
    scope.launch { try { render(context.applicationContext, manager, ids) } finally { pending.finish() } }
  }

  companion object {
    private const val PREFS = "cloud-sync-widget"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(context: Context) {
      val app = context.applicationContext
      // A previous process may have died during a controller operation.
      prefs(app).edit().putBoolean("busy", false).apply()
      scope.launch {
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(WORK_NAME).distinctUntilChanged().collect { infos ->
          val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
          val active = info?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
          val status = when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting to start…"
            WorkInfo.State.RUNNING -> "Running"
            WorkInfo.State.FAILED -> info.outputData.getString(ERROR) ?: "Could not complete. Try again."
            else -> null
          }
          prefs(app).edit().putBoolean("work-active", active).putString("work-status", status).apply()
          updateAll(app)
        }
      }
    }

    fun publish(context: Context, state: LocalPodcastState, canRefresh: Boolean, busy: Boolean, status: String) {
      val summary = cloudSyncStatus(state, canRefresh, supportsDownload = true)
      prefs(context).edit().putString("source", summary.source).putString("downloads", summary.downloads)
        .putString("processing", summary.processing).putString("checked", summary.checked)
        .putBoolean("has-source", summary.hasSource).putBoolean("refresh", summary.canRefresh)
        .putBoolean("download", summary.canDownload).putBoolean("process", summary.canProcess)
        .putBoolean("busy", busy).putString("progress", status.substringBefore(" · Last import:"))
        .putInt("downloaded-count", summary.downloadedCount ?: -1)
        .putInt("download-needed", summary.downloadNeeded ?: -1)
        .putInt("processing-needed", summary.processingNeeded ?: -1)
        .apply()
      updateAll(context)
    }

    private fun snapshot(context: Context): CloudSyncStatus {
      val p = prefs(context)
      val active = p.getBoolean("work-active", false)
      val busy = active || p.getBoolean("busy", false)
      val workStatus = p.getString("work-status", null)
      val progress = when {
        active && workStatus != "Running" -> workStatus
        busy -> p.getString("progress", null) ?: "Working…"
        workStatus != null -> workStatus
        else -> null
      }
      return CloudSyncStatus(
        source = p.getString("source", null) ?: "Connect podcasts",
        downloads = p.getString("downloads", "").orEmpty(), processing = p.getString("processing", "").orEmpty(),
        checked = p.getString("checked", "").orEmpty(), hasSource = p.getBoolean("has-source", false),
        canRefresh = p.getBoolean("refresh", false) && !busy, canDownload = p.getBoolean("download", false) && !busy,
        canProcess = p.getBoolean("process", false) && !busy, busy = busy, progress = progress,
        downloadedCount = p.getInt("downloaded-count", -1).takeIf { it >= 0 },
        downloadNeeded = p.getInt("download-needed", -1).takeIf { it >= 0 },
        processingNeeded = p.getInt("processing-needed", -1).takeIf { it >= 0 },
      )
    }
    fun updateAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, CloudSyncWidget::class.java))
      if (ids.isNotEmpty()) scope.launch { render(context.applicationContext, manager, ids) }
    }
    private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) = mutex.withLock {
      val state = snapshot(context)
      for (id in ids) {
        val view = if (Build.VERSION.SDK_INT >= RcLauncherWidgets.MIN_HOST_API) {
          runCatching { remoteViews(context, manager, id, state) }.getOrElse { legacyViews(context, id, state) }
        } else legacyViews(context, id, state)
        manager.updateAppWidget(id, view)
      }
    }
    private fun appIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName)
    private fun broadcast(context: Context, id: Int, actionId: Int, payload: String) = RcLauncherWidgets.actionBroadcast(context, CloudSyncWidget::class.java, id, actionId, payload)

    @RequiresApi(RcLauncherWidgets.MIN_HOST_API)
    private suspend fun remoteViews(context: Context, manager: AppWidgetManager, id: Int, state: CloudSyncStatus): RemoteViews {
      val slot = RcWidgetSizing.slotOf(manager, id)
      val capture = captureLauncherWidget(context, slot) {
        CloudSyncWidgetContent(state,
          onOpen = rcWidgetHostAction(name = "open", payload = "open"),
          onRefresh = rcWidgetHostAction(name = "refresh", payload = "refresh"),
          onDownload = rcWidgetHostAction(name = "download", payload = "download"),
          onProcess = rcWidgetHostAction(name = "process", payload = "process"),
          widthDp = slot.widthDp, heightDp = slot.heightDp)
      }
      val taps = capture.actions.mapNotNull { (actionId, payload) ->
        val intent = if (payload == "open") appIntent(context)?.let { RcLauncherWidgets.actionActivity(context, it, id, actionId) } else broadcast(context, id, actionId, payload)
        intent?.let { actionId to it }
      }.toMap()
      return RcLauncherWidgets.remoteViews(capture.bytes, taps)
    }
    private fun legacyViews(context: Context, id: Int, state: CloudSyncStatus): RemoteViews {
      val views = RemoteViews(context.packageName, context.resources.getIdentifier("cloud_sync_widget", "layout", context.packageName))
      fun view(name: String) = context.resources.getIdentifier(name, "id", context.packageName)
      views.setTextViewText(view("cloud_source"), state.source)
      views.setTextViewText(view("cloud_details"), listOfNotNull(state.downloadedCount?.let { "$it downloaded" }, state.progress ?: state.checked).filter { it.isNotBlank() }.joinToString(" · "))
      appIntent(context)?.let { views.setOnClickPendingIntent(view("cloud_source"), PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
      views.setTextViewText(view("cloud_download"), state.downloadNeeded?.let { "$it to download" } ?: state.downloads)
      views.setTextViewText(view("cloud_process"), state.processingNeeded?.let { "$it to process" } ?: state.processing)
      listOf(Triple("cloud_refresh", "refresh", state.canRefresh), Triple("cloud_download", "download", state.canDownload), Triple("cloud_process", "process", state.canProcess)).forEachIndexed { index, (name, payload, enabled) ->
        views.setViewVisibility(view(name), if (state.hasSource) android.view.View.VISIBLE else android.view.View.GONE)
        views.setBoolean(view(name), "setEnabled", enabled)
        views.setOnClickPendingIntent(view(name), broadcast(context, id, 1000 + index, payload))
      }
      return views
    }
  }
}

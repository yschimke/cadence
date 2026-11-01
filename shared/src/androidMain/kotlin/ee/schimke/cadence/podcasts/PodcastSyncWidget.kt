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
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.ConnectedDevice
import ee.schimke.cadence.bluetooth.batterySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Snapshot of actual on-device preparation; tapping opens Cadence. No fictitious background sync.
 *
 * On Android 16+ the launcher draws it from a Remote Compose document captured on the device for
 * each widget's own slot ([PodcastSyncWidgetContent]); older launchers get the two-line
 * `RemoteViews` it always had.
 */
class PodcastSyncWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    update(context, manager, appWidgetIds)
  }

  /** Resized: a Remote Compose widget fills the new slot, so capture it again. */
  override fun onAppWidgetOptionsChanged(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    update(context, manager, intArrayOf(appWidgetId))
  }

  private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
    if (Build.VERSION.SDK_INT < RcLauncherWidgets.MIN_HOST_API) {
      manager.updateAppWidget(ids, legacyViews(context))
      return
    }
    val pending = goAsync()
    scope.launch {
      try {
        updateRemoteCompose(context, manager, ids)
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    /** Payload of the card's tap: open Cadence. */
    private const val OPEN_APP = "open"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One update at a time, each reading the status once it holds the lock: a capture that started
     * earlier can't finish later and leave an older status on screen.
     */
    private val updates = Mutex()

    @Volatile private var currentDevice: ConnectedDevice? = null
    @Volatile private var swimming = false

    /** Refresh the widget when the controller receives a new connected-headphone battery level. */
    fun start(context: Context, controller: BluetoothController) {
      val appContext = context.applicationContext
      scope.launch {
        controller.state.map { Triple(it.connectedDevice, it.localAudioControls, it.openSwimConnected) }.distinctUntilChanged().collect { (device, mp3, openSwim) ->
          val summary = device?.batterySummary()
          val preferences = prefs(appContext)
          currentDevice = device
          if (summary != null && openSwim && device.address.isNotBlank()) {
            val key = digest(device.address)
            preferences.edit().putString("battery-$key", summary).putLong("battery-time-$key", System.currentTimeMillis()).putString("last-battery-address", device.address).apply()
          }
          swimming = mp3
          updateAll(appContext)
        }
      }
    }

    private fun copied(context: Context): String = prefs(context).getString("copied", null) ?: "Last copy: unknown"
    private fun available(context: Context): String = prefs(context).getString("available", null) ?: "Sync: no podcasts"

    internal fun battery(context: Context, connected: ConnectedDevice? = currentDevice, selectedTarget: String? = null): String? {
      val preferences = prefs(context)
      val target = selectedTarget ?: preferences.getString("target", "").orEmpty()
      val linked = linkedHeadphoneAddress(context, target)
      if (connected != null && (linked == null || linked == connected.address))
        connected.batterySummary()?.let { return it }
      val address = batteryHeadphoneAddress(linked, connected?.address, preferences.getString("last-battery-address", null))
      if (address == null) {
        return if (linked == null && connected == null) preferences.getString("last-battery", null)?.let { "Last headset: $it" } else null
      }
      val key = digest(address)
      val summary = preferences.getString("battery-$key", null) ?: return null
      val seen = preferences.getLong("battery-time-$key", 0)
      val date = android.text.format.DateUtils.getRelativeTimeSpanString(seen, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
      return "Last $date: $summary"
    }

    fun updateAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, PodcastSyncWidget::class.java))
      if (ids.isEmpty()) return
      if (Build.VERSION.SDK_INT < RcLauncherWidgets.MIN_HOST_API) {
        manager.updateAppWidget(ids, legacyViews(context))
      } else {
        scope.launch { updateRemoteCompose(context, manager, ids) }
      }
    }

    private fun prefs(context: Context) =
      context.getSharedPreferences("podcast-widget", Context.MODE_PRIVATE)

    private fun launchIntent(context: Context): Intent? =
      context.packageManager.getLaunchIntentForPackage(context.packageName)

    private fun legacyViews(context: Context): RemoteViews {
      val prefs = prefs(context)
      val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2)
      views.setTextViewText(android.R.id.text1, copied(context))
      views.setTextViewText(android.R.id.text2, listOfNotNull(available(context), battery(context)).joinToString(" · "))
      launchIntent(context)?.let { intent ->
        views.setOnClickPendingIntent(android.R.id.text1, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      }
      return views
    }

    @RequiresApi(RcLauncherWidgets.MIN_HOST_API)
    private suspend fun updateRemoteCompose(
      context: Context,
      manager: AppWidgetManager,
      ids: IntArray,
    ): Unit = updates.withLock {
      val prefs = prefs(context)
      val ready = prefs.getBoolean("ready", false)
      val copied = copied(context)
      val available = available(context)
      val launch = launchIntent(context)
      for (id in ids) {
        val slot = RcWidgetSizing.slotOf(manager, id)
        val views =
          runCatching {
              val capture =
                captureLauncherWidget(context, slot) {
                  PodcastSyncWidgetContent(
                    ready = ready,
                    copied = copied,
                    available = available,
                    battery = battery(context),
                    showSwimming = swimming,
                    heightDp = slot.heightDp,
                    widthDp = slot.widthDp,
                    onTap = rcWidgetHostAction(name = OPEN_APP, payload = OPEN_APP),
                  )
                }
              val taps =
                if (launch == null) emptyMap()
                else
                  capture.actions.mapValues { (actionId, _) ->
                    RcLauncherWidgets.actionActivity(context, Intent(launch), id, actionId)
                  }
              RcLauncherWidgets.remoteViews(capture.bytes, taps)
            }
            // A capture that fails still leaves the widget showing its status.
            .getOrElse { legacyViews(context) }
        manager.updateAppWidget(id, views)
      }
    }
  }
}

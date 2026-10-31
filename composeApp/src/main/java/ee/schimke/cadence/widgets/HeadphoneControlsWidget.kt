package ee.schimke.cadence.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import ee.schimke.cadence.R
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.WorkingMode
import ee.schimke.cadence.metro.AppGraphProvider
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Home-screen headphone controls, the phone counterpart of the Wear "Mode & controls" widget.
 *
 * Every control goes through the app's [BluetoothController], the only code allowed to reach the
 * OpenSwim Pro vendor transport, so the widget follows the same mode gating as the controls screen.
 * Android 16+ launchers draw a Remote Compose document captured per slot; older launchers get a
 * `RemoteViews` layout with the same controls.
 */
class HeadphoneControlsWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    val pending = goAsync()
    scope.launch {
      try {
        render(context, manager, appWidgetIds)
      } finally {
        pending.finish()
      }
    }
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    onUpdate(context, manager, intArrayOf(appWidgetId))
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != RcLauncherWidgets.ACTION_WIDGET_ACTION) {
      super.onReceive(context, intent)
      return
    }
    val action =
      HeadphoneWidgetAction.fromWire(intent.getStringExtra(RcLauncherWidgets.EXTRA_ACTION_PAYLOAD))
        ?: return
    val controller = controller(context) ?: return
    val pending = goAsync()
    scope.launch {
      try {
        when (action) {
          HeadphoneWidgetAction.Previous -> controller.previous()
          HeadphoneWidgetAction.Play -> controller.play()
          HeadphoneWidgetAction.Pause -> controller.pause()
          HeadphoneWidgetAction.Next -> controller.next()
          HeadphoneWidgetAction.SwitchToMp3 -> controller.setWorkingMode(WorkingMode.Mp3)
          HeadphoneWidgetAction.SwitchToBluetooth ->
            controller.setWorkingMode(WorkingMode.Bluetooth)
        }
        controller.refresh()
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    /** Payload of the header tap: open Cadence. */
    private const val OPEN_APP = "open"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updates = Mutex()

    /** Latest controller state; the widget never queries Bluetooth itself. */
    @Volatile
    private var latest: HeadphoneWidgetState =
      HeadphoneWidgetState.from(ee.schimke.cadence.bluetooth.BluetoothState())

    /** Keeps every placed widget in step with the controller for the life of the process. */
    fun start(context: Context, controller: BluetoothController) {
      val appContext = context.applicationContext
      // Captured documents hold resolved colours: recapture when light/dark or the wallpaper
      // palette changes so the widget keeps following the system theme.
      appContext.registerComponentCallbacks(
        object : ComponentCallbacks {
          override fun onConfigurationChanged(newConfig: Configuration) = updateAll(appContext)

          @Deprecated("Deprecated in Java") override fun onLowMemory() = Unit
        }
      )
      scope.launch {
        controller.state.map(HeadphoneWidgetState::from).distinctUntilChanged().collect {
          latest = it
          updateAll(appContext)
        }
      }
    }

    private fun updateAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, HeadphoneControlsWidget::class.java))
      if (ids.isEmpty()) return
      scope.launch { render(context, manager, ids) }
    }

    /** The system's dynamic scheme for the current light or dark mode. */
    private fun widgetColors(context: Context): WidgetColors {
      val dark =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
          Configuration.UI_MODE_NIGHT_YES
      return WidgetColors.from(
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      )
    }

    private fun controller(context: Context): BluetoothController? =
      (context.applicationContext as? AppGraphProvider)?.appGraph?.bluetoothController

    private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) =
      updates.withLock {
        val state = latest
        for (id in ids) {
          val views =
            if (Build.VERSION.SDK_INT >= RcLauncherWidgets.MIN_HOST_API) {
              runCatching { remoteComposeViews(context, manager, id, state) }
                // A capture that fails still leaves working controls on screen.
                .getOrElse { legacyViews(context, id, state) }
            } else {
              legacyViews(context, id, state)
            }
          manager.updateAppWidget(id, views)
        }
      }

    @RequiresApi(RcLauncherWidgets.MIN_HOST_API)
    private suspend fun remoteComposeViews(
      context: Context,
      manager: AppWidgetManager,
      id: Int,
      state: HeadphoneWidgetState,
    ): RemoteViews {
      val slot = RcWidgetSizing.slotOf(manager, id)
      val capture =
        captureLauncherWidget(context, slot) {
          HeadphoneControlsWidgetContent(
            state = state,
            layout = HeadphoneWidgetLayout.of(state, slot.widthDp, slot.heightDp),
            colors = widgetColors(context),
            onOpen = rcWidgetHostAction(name = OPEN_APP, payload = OPEN_APP),
            onAction = { action ->
              rcWidgetHostAction(name = action.wireName, payload = action.wireName)
            },
          )
        }
      val launch = launchIntent(context)
      val taps =
        capture.actions
          .mapNotNull { (actionId, payload) ->
            val intent =
              if (payload == OPEN_APP) {
                launch?.let { RcLauncherWidgets.actionActivity(context, Intent(it), id, actionId) }
              } else {
                broadcast(context, id, actionId, payload)
              }
            intent?.let { actionId to it }
          }
          .toMap()
      return RcLauncherWidgets.remoteViews(capture.bytes, taps)
    }

    /**
     * Pre-Android 16 launchers: one `RemoteViews` per size step, and the launcher shows the largest
     * that fits the widget's current size, so the same [HeadphoneWidgetLayout] tiers apply.
     */
    private fun legacyViews(context: Context, id: Int, state: HeadphoneWidgetState): RemoteViews =
      RemoteViews(
        LegacyWidthsDp.flatMap { width ->
            LegacyHeightsDp.map { height ->
              SizeF(width.toFloat(), height.toFloat()) to
                legacyViews(context, id, state, HeadphoneWidgetLayout.of(state, width, height))
            }
          }
          .toMap()
      )

    private fun legacyViews(
      context: Context,
      id: Int,
      state: HeadphoneWidgetState,
      layout: HeadphoneWidgetLayout,
    ): RemoteViews {
      val views =
        RemoteViews(
          context.packageName,
          if (layout.singleRow) R.layout.headphone_controls_widget_row
          else R.layout.headphone_controls_widget,
        )
      views.setTextViewText(
        R.id.headphone_widget_headline,
        if (layout.shortTitle) state.shortTitle else state.title,
      )
      views.setViewVisibility(R.id.headphone_widget_header, layout.showTitle.visibility)
      views.setTextViewText(R.id.headphone_widget_now_playing, state.detail.orEmpty())
      views.setViewVisibility(R.id.headphone_widget_now_playing, layout.showDetail.visibility)
      views.setTextViewText(R.id.headphone_widget_settings, state.extra.orEmpty())
      views.setViewVisibility(R.id.headphone_widget_settings, layout.showExtra.visibility)
      launchIntent(context)?.let {
        views.setOnClickPendingIntent(
          R.id.headphone_widget_header,
          PendingIntent.getActivity(
            context,
            0,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          ),
        )
      }
      val buttons =
        mapOf(
          HeadphoneWidgetAction.Previous to R.id.headphone_widget_previous,
          HeadphoneWidgetAction.Play to R.id.headphone_widget_play,
          HeadphoneWidgetAction.Pause to R.id.headphone_widget_pause,
          HeadphoneWidgetAction.Next to R.id.headphone_widget_next,
          HeadphoneWidgetAction.SwitchToMp3 to R.id.headphone_widget_mode_mp3,
          HeadphoneWidgetAction.SwitchToBluetooth to R.id.headphone_widget_mode_bluetooth,
        )
      val shown =
        layout.transport + listOfNotNull(state.modeSwitch?.takeIf { layout.showModeSwitch })
      buttons.forEach { (action, viewId) ->
        views.setViewVisibility(viewId, (action in shown).visibility)
        views.setOnClickPendingIntent(
          viewId,
          broadcast(context, id, LegacyActionIdBase + action.ordinal, action.wireName),
        )
      }
      return views
    }

    private val Boolean.visibility: Int
      get() = if (this) View.VISIBLE else View.GONE

    private fun broadcast(context: Context, widgetId: Int, actionId: Int, payload: String) =
      RcLauncherWidgets.actionBroadcast(
        context,
        HeadphoneControlsWidget::class.java,
        widgetId,
        actionId,
        payload,
      )

    private fun launchIntent(context: Context): Intent? =
      context.packageManager.getLaunchIntentForPackage(context.packageName)

    /** Size steps for the pre-Android 16 layouts (at most 16 pairs). */
    private val LegacyWidthsDp = listOf(110, 180, 250, 320)
    private val LegacyHeightsDp = listOf(40, 120, 150, 190)

    /** Keeps legacy button request codes apart from captured Remote Compose action ids. */
    private const val LegacyActionIdBase = 1_000
  }
}

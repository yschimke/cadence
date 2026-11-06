package ee.schimke.cadence.widgets

import android.app.PendingIntent
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import ee.schimke.cadence.R
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.ShokzPlayer
import ee.schimke.cadence.bluetooth.WorkingMode
import ee.schimke.cadence.metro.AppGraphProvider
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.instantiate.WidgetTap
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider
import ee.schimke.livery.widgets.launcher.WidgetSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen headphone controls, the phone counterpart of the Wear "Mode & controls" widget.
 *
 * Every control goes through the app's [BluetoothController], the only code allowed to reach the
 * OpenSwim Pro vendor transport, so the widget follows the same mode gating as the controls screen.
 *
 * Android 16+ launchers draw a Livery template (contract [HeadphoneControlsTemplate.CONTRACT]) from
 * the newest signed catalog on the device, or else the one the APK ships (`composeApp/livery`,
 * built into the assets; see [WidgetLivery]). Livery picks the design (a season's while it runs)
 * and the template for the slot's size tier, writes the state and the system's colours in, keeps
 * the live branch of its conditional layout, and publishes it. Older launchers, and any template
 * that can't be drawn, get a `RemoteViews` layout with the same controls.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class HeadphoneControlsWidget : LiveryLauncherWidgetProvider() {
  override suspend fun plan(context: Context, slot: WidgetSlot): PlanResult =
    livery(context)
      .planner
      .forAppData(
        slot.query(
          HeadphoneControlsTemplate.CONTRACT,
          minor = HeadphoneControlsTemplate.CONTRACT_MINOR,
        ),
        HeadphoneControlsTemplate.data(latest, context.systemWidgetColors()),
      )

  override fun images(context: Context): ImageResolver = livery(context).images

  /**
   * Every state's branch stays in the document the launcher gets (the player picks the live one):
   * rc-players only drops a branch that holds no values, and every captured branch does.
   */
  override val documentMaxBytes: Long = DOCUMENT_MAX_BYTES

  override fun fallback(context: Context, widgetId: Int, reason: String): RemoteViews =
    legacyViews(context, widgetId, latest)

  // Opening Cadence is an activity, which a broadcast receiver may not start.
  override fun tapIntent(
    context: Context,
    widgetId: Int,
    actionId: Int,
    action: String,
  ): PendingIntent {
    val launch = launchIntent(context)
    return if (action == HeadphoneControlsTemplate.OPEN && launch != null)
      RcLauncherWidgets.actionActivity(context, launch, widgetId, actionId)
    else super.tapIntent(context, widgetId, actionId, action)
  }

  override suspend fun onTap(context: Context, widgetId: Int, tap: WidgetTap) {
    val action = HeadphoneWidgetAction.fromWire(tap.action) ?: return
    val controller = controller(context) ?: return
    withContext(Dispatchers.Main.immediate) {
      val player = shokzPlayer
      if (latest.showWater && player != null)
        when (action) {
          HeadphoneWidgetAction.Previous -> player.previousTrack()
          HeadphoneWidgetAction.Play -> player.play()
          HeadphoneWidgetAction.Pause -> player.pause()
          HeadphoneWidgetAction.Next -> player.nextTrack()
          HeadphoneWidgetAction.SwitchToMp3 -> controller.setWorkingMode(WorkingMode.Mp3)
          HeadphoneWidgetAction.SwitchToBluetooth ->
            controller.setWorkingMode(WorkingMode.Bluetooth)
        }
      else
        when (action) {
          HeadphoneWidgetAction.Previous -> controller.previous()
          HeadphoneWidgetAction.Play -> controller.play()
          HeadphoneWidgetAction.Pause -> controller.pause()
          HeadphoneWidgetAction.Next -> controller.next()
          HeadphoneWidgetAction.SwitchToMp3 -> controller.setWorkingMode(WorkingMode.Mp3)
          HeadphoneWidgetAction.SwitchToBluetooth ->
            controller.setWorkingMode(WorkingMode.Bluetooth)
        }
    }
  }

  companion object {
    /** The largest instantiated document this widget hands the launcher. */
    const val DOCUMENT_MAX_BYTES: Long = 100_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var shokzPlayer: ShokzPlayer? = null

    /** Latest controller state; the widget never queries Bluetooth itself. */
    @Volatile
    private var latest: HeadphoneWidgetState =
      HeadphoneWidgetState.from(ee.schimke.cadence.bluetooth.BluetoothState())

    /** Keeps every placed widget in step with the controller for the life of the process. */
    fun start(context: Context, controller: BluetoothController) {
      if (shokzPlayer == null) shokzPlayer = ShokzPlayer(controller)
      val appContext = context.applicationContext
      // The system's colours are written into each render: render again when light/dark or the
      // wallpaper palette changes so the widget keeps following the system theme.
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

    internal fun updateAll(context: Context) =
      LiveryLauncherWidgetProvider.updateAll(context, HeadphoneControlsWidget::class.java)

    private fun controller(context: Context): BluetoothController? =
      (context.applicationContext as? AppGraphProvider)?.appGraph?.bluetoothController

    private fun livery(context: Context): WidgetLivery = WidgetLivery.get(context)

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

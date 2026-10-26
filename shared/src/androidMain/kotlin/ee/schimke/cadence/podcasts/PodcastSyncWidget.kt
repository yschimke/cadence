package ee.schimke.cadence.podcasts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** Snapshot of actual on-device preparation; tapping opens Cadence. No fictitious background sync. */
class PodcastSyncWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    updateAll(context)
  }
  companion object {
    fun updateAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val prefs = context.getSharedPreferences("podcast-widget", Context.MODE_PRIVATE)
      val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2)
      views.setTextViewText(android.R.id.text1, if (prefs.getBoolean("ready", false)) "Cadence · Ready to copy to USB" else "Cadence · Audio not ready")
      views.setTextViewText(android.R.id.text2, prefs.getString("status", "Open Cadence to sync podcasts"))
      context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
        views.setOnClickPendingIntent(android.R.id.text1, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      }
      manager.updateAppWidget(ComponentName(context, PodcastSyncWidget::class.java), views)
    }
  }
}

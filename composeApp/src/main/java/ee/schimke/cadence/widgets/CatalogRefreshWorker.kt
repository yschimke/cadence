package ee.schimke.cadence.widgets

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ee.schimke.cadence.platform.Analytics
import ee.schimke.cadence.podcasts.PodcastSyncWidget
import ee.schimke.livery.client.RefreshResult
import java.util.concurrent.TimeUnit

/**
 * Downloads the widget catalog Cadence publishes (see [WidgetLivery]) about once a day, and redraws
 * the headphone widgets when a newer one arrives. A season's design is in the catalog before its
 * first day, so the switch itself needs no network.
 *
 * A failed refresh is not retried here: the repository backs off on its own, and the bundled or
 * last downloaded catalog keeps drawing.
 */
class CatalogRefreshWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val livery = WidgetLivery.get(applicationContext)
    when (val result = livery.repository.refresh()) {
      is RefreshResult.Updated -> {
        Log.i(TAG, "widget catalog updated to serial ${result.serial}")
        HeadphoneControlsWidget.updateAll(applicationContext)
        PodcastSyncWidget.updateAll(applicationContext)
        // The new catalog may move, add or drop seasons.
        SeasonRedrawWorker.ensureScheduled(applicationContext, replace = true)
      }
      is RefreshResult.Failed -> Log.w(TAG, "widget catalog refresh failed: ${result.reason}")
      is RefreshResult.Skipped -> Log.d(TAG, "widget catalog refresh skipped: ${result.reason}")
      RefreshResult.Unchanged -> Unit
    }
    return Result.success()
  }

  companion object {
    /**
     * Fetches the catalog now (Settings' refresh), whatever the daily schedule says, and redraws
     * both widgets either way, so they pick up the current season's look. Returns a line for the
     * user.
     */
    suspend fun refreshNow(context: Context): String {
      val app = context.applicationContext
      val result =
        if (WidgetLivery.trustedKeys.isEmpty()) null
        else WidgetLivery.get(app).repository.refresh(force = true)
      HeadphoneControlsWidget.updateAll(app)
      PodcastSyncWidget.updateAll(app)
      if (result is RefreshResult.Updated) SeasonRedrawWorker.ensureScheduled(app, replace = true)
      Analytics.current.event(
        "widget_designs_refresh",
        mapOf("result" to (result?.let { it::class.simpleName } ?: "disabled")),
      )
      return when (result) {
        null -> "This build doesn't download widget designs."
        is RefreshResult.Updated -> "Updated to the latest designs."
        RefreshResult.Unchanged -> "Already up to date."
        is RefreshResult.Skipped -> "Not refreshed: ${result.reason}"
        is RefreshResult.Failed -> "Couldn't refresh: ${result.reason}"
      }
    }

    private const val TAG = "CatalogRefresh"
    private const val WORK_NAME = "livery-catalog-refresh"

    /** Enqueues the daily refresh; keeps an existing schedule. */
    fun schedule(context: Context) {
      if (WidgetLivery.trustedKeys.isEmpty()) {
        // No key to verify a download with: nothing could be taken.
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return
      }
      val request =
        PeriodicWorkRequestBuilder<CatalogRefreshWorker>(1, TimeUnit.DAYS)
          .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
          )
          .build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }
}

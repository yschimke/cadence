package ee.schimke.cadence.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import ee.schimke.cadence.metro.AssistedWorkerFactory
import ee.schimke.cadence.metro.WorkerKey
import ee.schimke.cadence.podcasts.PodcastSyncWidget
import ee.schimke.livery.protocol.Manifest
import java.util.concurrent.TimeUnit

/**
 * Redraws the headphone widgets, and the podcast sync card whose pool follows the same season, when
 * a season starts or ends. A season is an experiment with a start and an end, and the widget picks
 * its design only when it is drawn. Launcher widgets aren't redrawn on a timer
 * (`updatePeriodMillis` is 0), so without this an idle widget would keep the old season's look
 * until something else redraws it.
 *
 * Each run redraws and then schedules itself for the next boundary in the catalog the widget draws
 * from: the downloaded one, else the APK's. It needs no network, because a season's design is
 * already on the device before it starts.
 */
@AssistedInject
class SeasonRedrawWorker(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val widgetLivery: WidgetLivery,
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    if (inputData.getBoolean(KEY_REDRAW, true)) {
      HeadphoneControlsWidget.updateAll(applicationContext)
      // The sync card's pool follows the same season.
      PodcastSyncWidget.updateAll(applicationContext)
    }
    scheduleNext(applicationContext, widgetLivery)
    return Result.success()
  }

  @AssistedFactory
  @WorkerKey(SeasonRedrawWorker::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : AssistedWorkerFactory {
    override fun create(context: Context, params: WorkerParameters): SeasonRedrawWorker
  }

  companion object {
    private const val WORK_NAME = "livery-season-redraw"
    private const val KEY_REDRAW = "redraw"

    /**
     * Makes sure a redraw is scheduled for the next season boundary. On app start this keeps one
     * that is already pending; after a new catalog arrives, [replace] reschedules from its seasons.
     */
    fun ensureScheduled(context: Context, replace: Boolean = false) {
      // Schedule from a worker, which can read the catalog off the main thread.
      val request =
        OneTimeWorkRequestBuilder<SeasonRedrawWorker>()
          .setInputData(Data.Builder().putBoolean(KEY_REDRAW, false).build())
          .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(
          WORK_NAME,
          if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
          request,
        )
    }

    private suspend fun scheduleNext(context: Context, livery: WidgetLivery) {
      val manifest = livery.repository.current()
      val now = System.currentTimeMillis()
      val next = nextBoundary(manifest, now)
      val work = WorkManager.getInstance(context)
      if (next == null) {
        work.cancelUniqueWork(WORK_NAME)
        return
      }
      val request =
        OneTimeWorkRequestBuilder<SeasonRedrawWorker>()
          // A second past the boundary, so the season has started (or ended) when it draws.
          .setInitialDelay(next - now + 1_000, TimeUnit.MILLISECONDS)
          .build()
      // Appends after this run (or replaces failed/cancelled) without cancelling the active worker.
      work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** The next time after [now] that an experiment or season in [manifest] starts or ends. */
    internal fun nextBoundary(manifest: Manifest?, now: Long): Long? =
      manifest
        ?.experiments
        .orEmpty()
        .flatMap { listOfNotNull(it.startEpochMs, it.endEpochMs) }
        .filter { it > now }
        .minOrNull()
  }
}

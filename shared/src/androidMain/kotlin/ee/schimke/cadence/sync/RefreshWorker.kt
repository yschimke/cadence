package ee.schimke.cadence.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import ee.schimke.cadence.metro.AssistedWorkerFactory
import ee.schimke.cadence.metro.WorkerKey
import ee.schimke.cadence.platform.Analytics
import kotlinx.coroutines.CancellationException

@AssistedInject
class RefreshWorker(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val refreshOrchestrator: RefreshOrchestrator,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val profileId =
      inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
    return try {
      refreshOrchestrator.refresh(profileId)
      Analytics.current.event("sync_refresh", mapOf("ok" to true))
      Result.success()
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      Analytics.current.event("sync_refresh", mapOf("ok" to false))
      // Periodic work runs again on its cadence; one-off work uses the
      // default backoff. Retrying handles transient network failures.
      Result.retry()
    }
  }

  @AssistedFactory
  @WorkerKey(RefreshWorker::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : AssistedWorkerFactory {
    override fun create(context: Context, params: WorkerParameters): RefreshWorker
  }

  companion object {
    const val KEY_PROFILE_ID = "profile_id"

    fun periodicTag(profileId: String) = "cadence-refresh-periodic-$profileId"

    fun oneShotTag(profileId: String) = "cadence-refresh-oneshot-$profileId"
  }
}

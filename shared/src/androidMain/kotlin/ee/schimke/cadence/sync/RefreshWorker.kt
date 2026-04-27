package ee.schimke.cadence.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ee.schimke.cadence.metro.AppGraphProvider

class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val app = applicationContext as? AppGraphProvider ?: return Result.retry()
        val orchestrator = app.appGraph.refreshOrchestrator
        return try {
            orchestrator.refresh(profileId)
            Result.success()
        } catch (t: Throwable) {
            // Periodic work runs again on its cadence; one-off work uses the
            // default backoff. Retrying handles transient network failures.
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        fun periodicTag(profileId: String) = "cadence-refresh-periodic-$profileId"
        fun oneShotTag(profileId: String) = "cadence-refresh-oneshot-$profileId"
    }
}

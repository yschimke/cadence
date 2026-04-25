package ee.schimke.shokz.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ee.schimke.shokz.ShokzApplication

class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val app = applicationContext as? ShokzApplication ?: return Result.retry()
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
        fun periodicTag(profileId: String) = "shokz-refresh-periodic-$profileId"
        fun oneShotTag(profileId: String) = "shokz-refresh-oneshot-$profileId"
    }
}

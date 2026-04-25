package ee.schimke.cadence.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.cadence.datastore.proto.NetworkConstraint
import ee.schimke.cadence.datastore.proto.SyncProfile
import java.util.concurrent.TimeUnit

@ContributesBinding(AppScope::class, binding = binding<RefreshScheduler>())
@SingleIn(AppScope::class)
@Inject
class WorkManagerRefreshScheduler(
    private val context: Context,
) : RefreshScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun schedule(profile: SyncProfile) {
        val tag = RefreshWorker.periodicTag(profile.id)
        if (!profile.auto_refresh || profile.refresh_interval_minutes <= 0) {
            workManager.cancelUniqueWork(tag)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            profile.refresh_interval_minutes.toLong().coerceAtLeast(15),
            TimeUnit.MINUTES,
        )
            .setConstraints(profile.network_constraint.toConstraints())
            .setInputData(Data.Builder().putString(RefreshWorker.KEY_PROFILE_ID, profile.id).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(tag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            tag,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel(profileId: String) {
        workManager.cancelUniqueWork(RefreshWorker.periodicTag(profileId))
    }

    override fun runNow(profileId: String) {
        val tag = RefreshWorker.oneShotTag(profileId)
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(Data.Builder().putString(RefreshWorker.KEY_PROFILE_ID, profileId).build())
            .addTag(tag)
            // Manual refreshes ignore the network gate so the user can force a run.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
    }

    private fun NetworkConstraint.toConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(
            when (this) {
                NetworkConstraint.ANY -> NetworkType.NOT_REQUIRED
                NetworkConstraint.CONNECTED -> NetworkType.CONNECTED
                NetworkConstraint.UNMETERED -> NetworkType.UNMETERED
            }
        ).build()
}

package ee.schimke.cadence

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ee.schimke.cadence.metro.AndroidAppGraph
import ee.schimke.cadence.metro.AppGraphProvider
import ee.schimke.cadence.metro.CadenceAppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CadenceApplication : Application(), AppGraphProvider {
    /** Holder reference for the app graph for [MetroAppComponentFactory]. */
    override val appGraph: AndroidAppGraph by lazy {
        createGraphFactory<CadenceAppGraph.Factory>().create(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Keep WorkManager schedules in sync with the auto_refresh flags on each
        // profile. When a profile is added, toggled or removed the periodic job
        // is enqueued, updated or cancelled accordingly.
        appScope.launch {
            var previous = emptySet<String>()
            appGraph.syncRepo.profiles.collect { profiles ->
                val current = profiles.map { it.id }.toSet()
                (previous - current).forEach { appGraph.refreshScheduler.cancel(it) }
                profiles.forEach { appGraph.refreshScheduler.schedule(it) }
                previous = current
            }
        }
    }
}



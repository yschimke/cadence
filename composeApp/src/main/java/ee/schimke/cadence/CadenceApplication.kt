package ee.schimke.cadence

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.os.Build
import androidx.work.Configuration
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import ee.schimke.cadence.analytics.CadenceFirebase
import ee.schimke.cadence.metro.AndroidAppGraph
import ee.schimke.cadence.metro.AppGraphProvider
import ee.schimke.cadence.metro.CadenceAppGraph
import ee.schimke.cadence.wear.PhoneWearDataPublisher
import ee.schimke.cadence.widgets.CatalogRefreshWorker
import ee.schimke.cadence.widgets.HeadphoneControlsWidget
import ee.schimke.cadence.widgets.NotificationLivery
import ee.schimke.cadence.widgets.SeasonRedrawWorker
import ee.schimke.cadence.widgets.WidgetSeason
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CadenceApplication :
  Application(), AppGraphProvider, MetroApplication, Configuration.Provider {
  private val cadenceGraph: CadenceAppGraph by lazy {
    createGraphFactory<CadenceAppGraph.Factory>().create(this)
  }

  override val appGraph: AndroidAppGraph
    get() = cadenceGraph

  override val appComponentProviders: MetroAppComponentProviders
    get() = if (isNotificationListenerProcess()) EmptyAppComponentProviders else cadenceGraph

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(cadenceGraph.workerFactory).build()

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    // NowPlayingNotificationListener runs in :notification_listener so the system's
    // NotificationListenerService binding doesn't pin the main UI process out of CACHED.
    // Skip main-process initialization (Bluetooth, Wear publisher, widgets, WorkManager) there.
    if (isNotificationListenerProcess()) {
      return
    }
    // First, so the widgets and workers created below report to it.
    CadenceFirebase.install(this)
    ee.schimke.cadence.podcasts.scheduleOvernightPodcasts(applicationContext)
    ee.schimke.cadence.podcasts.CloudSyncWidget.start(applicationContext)
    PhoneWearDataPublisher(
        context = applicationContext,
        bluetoothController = cadenceGraph.bluetoothController,
        syncOrchestrator = cadenceGraph.syncOrchestrator,
      )
      .start()
    val widgetLivery = cadenceGraph.widgetLivery
    // Cadence's other widgets follow the headphone widget's season.
    WidgetSeason.current = { widgetLivery.seasonLook() }
    // Every notification's expanded body is a Remote Compose template from the same catalog.
    ee.schimke.cadence.notifications.CadenceNotifications.renderer =
      NotificationLivery(widgetLivery)
    HeadphoneControlsWidget.start(applicationContext, cadenceGraph.bluetoothController)
    CatalogRefreshWorker.schedule(applicationContext)
    SeasonRedrawWorker.ensureScheduled(applicationContext)
    ee.schimke.cadence.podcasts.PodcastSyncWidget.start(
      applicationContext,
      cadenceGraph.bluetoothController,
    )
    // Keep WorkManager schedules in sync with the auto_refresh flags on each
    // profile. When a profile is added, toggled or removed the periodic job
    // is enqueued, updated or cancelled accordingly.
    appScope.launch {
      var previous = emptySet<String>()
      cadenceGraph.syncRepo.profiles.collect { profiles ->
        val current = profiles.map { it.id }.toSet()
        (previous - current).forEach { cadenceGraph.refreshScheduler.cancel(it) }
        profiles.forEach { cadenceGraph.refreshScheduler.schedule(it) }
        previous = current
      }
    }
  }

  private fun isNotificationListenerProcess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
      getProcessName().endsWith(":notification_listener")

  private object EmptyAppComponentProviders : MetroAppComponentProviders {
    override val activityProviders: Map<KClass<out Activity>, () -> Activity> = emptyMap()
    override val providerProviders: Map<KClass<out ContentProvider>, () -> ContentProvider> =
      emptyMap()
    override val receiverProviders: Map<KClass<out BroadcastReceiver>, () -> BroadcastReceiver> =
      emptyMap()
    override val serviceProviders: Map<KClass<out Service>, () -> Service> = emptyMap()
  }
}

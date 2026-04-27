package ee.schimke.cadence.metro

import android.app.Activity
import android.app.Application
import android.content.Context
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import ee.schimke.cadence.data.AndroidFileSystem
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncRepo
import okio.FileSystem
import kotlin.reflect.KClass

/**
 * Contract surface exposed by the Android app graph. The concrete
 * `@DependencyGraph` lives in `:composeApp` so Metro can aggregate
 * activity contributions (e.g. `MainActivity`) from the application module.
 */
interface AndroidAppGraph: AppGraph {
    @Multibinds(allowEmpty = true)
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    val viewModelGraphFactory: AndroidViewModelGraph.Factory

    val syncOrchestrator: SyncOrchestrator

    val syncRepo: SyncRepo

    val refreshOrchestrator: RefreshOrchestrator

    val refreshScheduler: RefreshScheduler

    @Provides
    fun provideFileSystem(context: Context): FileSystem = AndroidFileSystem(context)

    @Provides
    fun provideContext(application: Application): Context = application
}
package ee.schimke.cadence.metro

import android.app.Activity
import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import ee.schimke.cadence.CadenceApplication
import ee.schimke.cadence.data.AndroidFileSystem
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncRepo
import okio.FileSystem
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
interface AndroidAppGraph: AppGraph {
    /**
     * A multibinding map of activity classes to their providers accessible for
     * [MetroAppComponentFactory].
     */
    @Multibinds
    abstract val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    val viewModelGraphFactory: AndroidViewModelGraph.Factory

    val syncOrchestrator: SyncOrchestrator

    val syncRepo: SyncRepo

    val refreshOrchestrator: RefreshOrchestrator

    val refreshScheduler: RefreshScheduler

    @Provides
    fun provideFileSystem(context: Context): FileSystem = AndroidFileSystem(context)

    @Provides
    fun provideContext(application: Application): Context = application

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AndroidAppGraph
    }
}
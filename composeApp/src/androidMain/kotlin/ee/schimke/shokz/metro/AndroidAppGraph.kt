package ee.schimke.shokz.metro

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ee.schimke.shokz.data.AndroidFileSystem
import ee.schimke.shokz.data.createFilesDataStore
import ee.schimke.shokz.datastore.proto.Settings
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
abstract class AndroidAppGraph : AppGraph {
    /**
     * A multibinding map of activity classes to their providers accessible for
     * [MetroAppComponentFactory].
     */
    @Multibinds
    abstract val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    lateinit var ds: DataStore<Settings>

    @Provides
    @SingleIn(AppScope::class)
    fun provideFilesDataStore(context: Context): DataStore<Settings> {
        // TODO remove this
        synchronized(this) {
            if (!::ds.isInitialized) {
                ds = createFilesDataStore {
                    context.filesDir.resolve("devices-4.pb").toOkioPath()
                }
            }
        }

        return ds
    }

    @Provides
    fun provideFileSystem(context: Context): FileSystem = AndroidFileSystem(context)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
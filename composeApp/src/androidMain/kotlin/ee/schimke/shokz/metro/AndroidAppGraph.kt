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
import ee.schimke.shokz.data.AndroidStorageManager
import ee.schimke.shokz.data.StorageManager
import ee.schimke.shokz.data.createFilesDataStore
import ee.schimke.shokz.datastore.proto.Devices
import ee.schimke.shokz.platform.AndroidPlatform
import ee.schimke.shokz.platform.Platform
import ee.schimke.shokz.usb.AndroidUsbManager
import ee.schimke.shokz.usb.UsbManager
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

    @Provides
    fun providePlatform(platform: AndroidPlatform): Platform = platform

    lateinit var ds: DataStore<Devices>

    @Provides
    @SingleIn(AppScope::class)
    fun provideFilesDataStore(context: Context): DataStore<Devices> {
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

    @Provides
    fun provideStorageManager(storageManager: AndroidStorageManager): StorageManager =
        storageManager

    @Provides
    fun provideUsbManager(usbManager: AndroidUsbManager): UsbManager = usbManager

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
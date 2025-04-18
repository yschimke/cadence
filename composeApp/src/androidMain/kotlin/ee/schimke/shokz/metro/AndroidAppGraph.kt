package ee.schimke.shokz.metro

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ee.schimke.shokz.data.createFilesDataStore
import ee.schimke.shokz.datastore.proto.Devices
import ee.schimke.shokz.platform.AndroidPlatform
import ee.schimke.shokz.platform.Platform
import okio.Path.Companion.toOkioPath
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
interface AndroidAppGraph: AppGraph {
  /**
   * A multibinding map of activity classes to their providers accessible for
   * [MetroAppComponentFactory].
   */
  @Multibinds val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

  @Provides fun providePlatform(platform: AndroidPlatform): Platform = platform

  @Provides @SingleIn(AppScope::class) fun provideFilesDataStore(platform: AndroidPlatform, context: Context): DataStore<Devices> =
    createFilesDataStore {
      context.filesDir.resolve("devices-2.pb").toOkioPath()
    }

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides context: Context): AndroidAppGraph
  }
}
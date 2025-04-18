package ee.schimke.shokz.metro

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ee.schimke.shokz.platform.AndroidPlatform
import ee.schimke.shokz.platform.Platform
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
interface AndroidAppGraph: AppGraph {
  /**
   * A multibinding map of activity classes to their providers accessible for
   * [MetroAppComponentFactory].
   */
  @Multibinds val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

  @Provides fun providePlatform(platform: AndroidPlatform): Platform = platform
}
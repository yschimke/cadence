package ee.schimke.cadence.metro

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface CadenceAppGraph : AndroidAppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): CadenceAppGraph
    }
}

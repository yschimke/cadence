package ee.schimke.shokz

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ee.schimke.shokz.metro.AndroidAppGraph

class ShokzApplication : Application() {
    /** Holder reference for the app graph for [MetroAppComponentFactory]. */
    val appGraph by lazy { createGraphFactory<AndroidAppGraph.Factory>().create(this) }

    override fun onCreate() {
        super.onCreate()
    }
}


package ee.schimke.shokz

import android.app.Application
import dev.zacsweers.metro.createGraph
import ee.schimke.shokz.metro.AndroidAppGraph
import kotlin.getValue

class ShokzApplication: Application() {
    /** Holder reference for the app graph for [MetroAppComponentFactory]. */
    val appGraph by lazy { createGraph<AndroidAppGraph>() }

    override fun onCreate() {
        super.onCreate()

        println(appGraph.activityProviders)
    }
}


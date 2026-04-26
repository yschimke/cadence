package ee.schimke.cadence.metro

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.annotation.Keep
import androidx.core.app.AppComponentFactory
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass

/**
 * An [AppComponentFactory] that uses Metro for constructor injection of Activities.
 *
 * If you have minSdk < 28, you can fall back to using member injection on Activities or (better)
 * use an architecture that abstracts the Android framework components away.
 */
@Keep
class MetroAppComponentFactory : AppComponentFactory() {

    private inline fun <reified T : Any> getInstance(
        cl: ClassLoader,
        className: String,
        providers: Map<KClass<out T>, Provider<T>>,
    ): T? {
        val clazz = Class.forName(className, false, cl).asSubclass(T::class.java)
        val modelProvider = providers[clazz.kotlin] ?: return null
        return modelProvider()
    }

    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        if (!isReady()) {
            return super.instantiateActivityCompat(cl, className, intent)
        }
        return getInstance(cl, className, activityProviders)
            ?: super.instantiateActivityCompat(cl, className, intent)
    }

    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        val app = super.instantiateApplicationCompat(cl, className)
        activityProviders = (app as AppGraphProvider).appGraph.activityProviders
        return app
    }

    // AppComponentFactory can be created multiple times
    companion object {
        private lateinit var activityProviders: Map<KClass<out Activity>, Provider<Activity>>

        // Renderer harnesses (e.g. Robolectric) may instantiate activities before the
        // Application object initialises this map; defer to the platform default in that case.
        private fun isReady(): Boolean = ::activityProviders.isInitialized
    }
}
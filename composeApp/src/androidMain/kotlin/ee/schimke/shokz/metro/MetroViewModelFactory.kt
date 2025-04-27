package ee.schimke.shokz.metro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlin.collections.get
import kotlin.invoke

/**
 * A [androidx.lifecycle.ViewModelProvider.Factory] that uses an injected map of [kotlin.reflect.KClass] to [dev.zacsweers.metro.Provider] of [androidx.lifecycle.ViewModel]
 * to create ViewModels.
 */
@ContributesBinding(AppScope::class)
@Inject
class MetroViewModelFactory(val appGraph: AndroidAppGraph) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val viewModelGraph = createGraphFactory<AndroidViewModelGraph.Factory>().create(appGraph, extras)

        println(viewModelGraph.viewModelProviders)

        val provider =
            viewModelGraph.viewModelProviders[modelClass.kotlin]
                ?: throw IllegalArgumentException("Unknown model class $modelClass")

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        return modelClass.cast(provider())
    }
}
package ee.schimke.shokz.home

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.platform.Platform

@ContributesIntoMap(AppScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
    private val platform: Platform
): ViewModel() {

    fun greeting(): String {
        return platform.name
    }
}
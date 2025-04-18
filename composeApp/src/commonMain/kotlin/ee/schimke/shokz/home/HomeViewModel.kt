package ee.schimke.shokz.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.metro.ViewModelCreator
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.platform.Platform


class HomeViewModel(
    private val platform: Platform
) : ViewModel() {

    fun greeting(): String {
        return platform.name
    }
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModelCreator(
    private val platform: Platform
) : ViewModelCreator {
    override fun create(extras: CreationExtras): HomeViewModel = HomeViewModel(platform)
}
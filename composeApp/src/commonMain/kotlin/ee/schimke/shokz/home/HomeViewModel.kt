package ee.schimke.shokz.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.files.DeviceFilesViewModel
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.metro.ViewModelScope
import ee.schimke.shokz.platform.Platform

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
) : ViewModel() {
}
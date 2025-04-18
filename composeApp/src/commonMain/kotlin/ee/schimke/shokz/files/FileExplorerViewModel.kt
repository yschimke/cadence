package ee.schimke.shokz.files

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.home.HomeViewModel
import ee.schimke.shokz.metro.ViewModelKey

@ContributesIntoMap(AppScope::class)
@ViewModelKey(FileExplorerViewModel::class)
@Inject
class FileExplorerViewModel: ViewModel() {
}
package ee.schimke.shokz.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.home.HomeViewModel
import ee.schimke.shokz.metro.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import okio.Path

@ContributesIntoMap(AppScope::class)
@ViewModelKey(FileExplorerViewModel::class)
@Inject
class FileExplorerViewModel : ViewModel() {
    val uiState: StateFlow<UiState> = flowOf(UiState.Loading).stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = UiState.Loading
    )

    sealed interface UiState {
        data object Loading : UiState

        data class Files(val files: List<Path>)
    }
}


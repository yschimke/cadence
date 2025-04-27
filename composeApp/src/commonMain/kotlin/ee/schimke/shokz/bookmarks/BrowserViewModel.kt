@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.shokz.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.datastore.proto.Bookmark
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.metro.ViewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(BookmarksViewModel::class)
@Inject
class BookmarksViewModel(
) : ViewModel() {
    val uiState: StateFlow<UiState> = flow<UiState> {
        emit(
            Loaded(
                listOf(
                    Bookmark(
                        "Podbean",
                        "https://www.podbean.com/all",
                        favicon = "https://pbcdn1.podbean.com/fs1/site/images/favicon.ico"
                    )
                )
            )
        )
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), Loading)

    sealed interface UiState

    data object Loading : UiState

    data class Loaded(val bookmarks: List<Bookmark>) : UiState
}


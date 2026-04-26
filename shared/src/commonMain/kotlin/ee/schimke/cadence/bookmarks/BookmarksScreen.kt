package ee.schimke.cadence.bookmarks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ee.schimke.cadence.metro.metroViewModel
import io.ktor.http.Url

@Composable
fun BookmarksScreen(modifier: Modifier = Modifier, onNavigateTo: (Url) -> Unit) {
  val viewModel = metroViewModel<BookmarksViewModel>()

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  BookmarksScreen(modifier, uiState, onNavigateTo)
}

@Composable
fun BookmarksScreen(
  modifier: Modifier,
  uiState: BookmarksViewModel.UiState,
  onNavigateTo: (Url) -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth().safeContentPadding()) {
    if (uiState is BookmarksViewModel.Loaded) {
      uiState.bookmarks.forEach { Card(onClick = { onNavigateTo(Url(it.url)) }) { Text(it.name) } }
    }
  }
}

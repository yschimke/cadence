package ee.schimke.shokz.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ee.schimke.shokz.metro.metroViewModel

@Composable
fun FileExplorerScreen(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<FileExplorerViewModel>()

    val uiState = viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth()
            .safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Files")
        Text("Files: ${uiState.value}")
    }
}
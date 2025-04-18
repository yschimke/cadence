package ee.schimke.shokz.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.shokz.metro.metroViewModel

@Composable
fun DeviceFilesScreen(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<DeviceFilesViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth()
            .safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Files " + uiState.name)

        if (uiState is DeviceFilesViewModel.UiState.Loaded) {
            val files = (uiState as DeviceFilesViewModel.UiState.Loaded).files

            files.forEach {
                Surface {
                    Row {
                        Text(it.name)
                    }
                }
            }
        } else if (uiState is DeviceFilesViewModel.UiState.Loading) {
            Text("Loading...")
        }
    }
}
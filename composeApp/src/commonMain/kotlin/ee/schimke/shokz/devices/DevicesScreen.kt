package ee.schimke.shokz.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.metroViewModel
import okio.Path
import okio.Path.Companion.toPath

@Composable
fun DevicesScreen(modifier: Modifier = Modifier, onDeviceClick: (Device) -> Unit) {
    val viewModel = metroViewModel<DevicesViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    val permissionCheck = rememberFileExplorerOpenLauncher(onGranted = {
        viewModel.addDevice(it)
    })

    Column(
        modifier = modifier.fillMaxWidth()
            .safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Devices")

        if (uiState is DevicesViewModel.UiState.Devices) {
            val files = (uiState as DevicesViewModel.UiState.Devices).devices

            files.forEach {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = { onDeviceClick(it) }) {
                    Text(it.name)
                    Text(it.path.toPath().name)
                }
            }
        } else if (uiState is DevicesViewModel.UiState.Loading) {
            Text("Loading...")
        }

        Button(onClick = permissionCheck) {
            Text("Manage New Device")
        }
    }
}

@Composable
expect fun rememberFileExplorerOpenLauncher(
    onGranted: (Path) -> Unit,
): () -> Unit
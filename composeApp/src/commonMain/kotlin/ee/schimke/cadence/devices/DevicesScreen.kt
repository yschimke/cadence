package ee.schimke.cadence.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.metro.metroViewModel
import okio.Path
import okio.Path.Companion.toPath

@Composable
fun DevicesScreen(modifier: Modifier = Modifier, onDeviceClick: (Device) -> Unit) {
    val viewModel = metroViewModel<DevicesViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    val permissionCheck = rememberFileExplorerOpenLauncher(onGranted = {
        viewModel.addDevice(it)
    })

    DevicesScreen(modifier, uiState, permissionCheck, onDeviceClick)
}

@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    uiState: DevicesViewModel.UiState,
    permissionCheck: () -> Unit,
    onDeviceClick: (Device) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
    ) {
        Text("Devices")

        if (uiState is DevicesViewModel.UiState.Devices) {
            uiState.devices.forEach {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = { onDeviceClick(it) }) {
                    Text(it.name)
                    Text(it.path.toPath().name.substringAfterLast("%3A").replace("%2F", "/"))
                }
            }
        } else if (uiState is DevicesViewModel.UiState.Loading) {
            Text("Loading...")
        }

        Button(onClick = permissionCheck) {
            Text("Manage New Device")
        }

        Text("Available Devices")

        if (uiState is DevicesViewModel.UiState.Devices) {
            uiState.usbDevices.forEach {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = {  }) {
                    Text(it.name)
                    Text(it.toString())
                }
            }
        }
    }
}

@Composable
expect fun rememberFileExplorerOpenLauncher(
    onGranted: (Path) -> Unit,
): () -> Unit
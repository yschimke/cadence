package ee.schimke.cadence.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.usb.CadenceUsb
import ee.schimke.cadence.usb.UsbDevice
import okio.Path
import okio.Path.Companion.toPath

@Composable
fun DevicesScreen(modifier: Modifier = Modifier, onDeviceClick: (Device) -> Unit) {
  val viewModel = metroViewModel<DevicesViewModel>()

  val uiState by viewModel.uiState.collectAsState()

  val cadenceDetected =
    (uiState as? DevicesViewModel.UiState.Devices)?.usbDevices?.any { it.isCadenceStorage() } ==
      true
  val defaultName by rememberUpdatedState(if (cadenceDetected) "Cadence" else null)
  val permissionCheck =
    rememberFileExplorerOpenLauncher(
      onGranted = { pickedName, path -> viewModel.addDevice(path, defaultName ?: pickedName) }
    )

  Column(modifier) {
    ee.schimke.cadence.usb.UsbConnectionPanel(viewModel.usbManager)
    DevicesScreen(Modifier, uiState, permissionCheck, onDeviceClick)
  }
}

@Composable
fun DevicesScreen(
  modifier: Modifier = Modifier,
  uiState: DevicesViewModel.UiState,
  permissionCheck: () -> Unit,
  onDeviceClick: (Device) -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth().safeContentPadding(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
  ) {
    val devicesState = uiState as? DevicesViewModel.UiState.Devices
    val recognised = devicesState?.usbDevices?.filter { it.isCadenceStorage() }.orEmpty()
    val managedEmpty = devicesState?.devices.isNullOrEmpty()

    if (managedEmpty && recognised.isNotEmpty()) {
      ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = permissionCheck) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            "${recognised.first().friendlyName()} detected",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            "Connect to point Cadence at the mounted folder.",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    Text("Previously added", style = MaterialTheme.typography.titleMedium)

    if (devicesState != null) {
      if (devicesState.devices.isEmpty()) {
        Text(
          "No devices yet. Plug in Cadence USB storage and it'll show up below.",
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        devicesState.devices.forEach {
          ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = { onDeviceClick(it) }) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(it.name, style = MaterialTheme.typography.titleSmall)
              Text(
                it.path.toPath().name.substringAfterLast("%3A").replace("%2F", "/"),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    } else if (uiState is DevicesViewModel.UiState.Loading) {
      Text("Loading...")
    }

    Button(onClick = permissionCheck) { Text("Add a device") }

    Text("Connected now", style = MaterialTheme.typography.titleMedium)

    if (devicesState != null) {
      if (recognised.isEmpty()) {
        Text(
          "No Cadence USB storage plugged in.",
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        recognised.forEach { device ->
          ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = permissionCheck) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(device.friendlyName(), style = MaterialTheme.typography.titleSmall)
              Text(device.vendorProductLabel(), style = MaterialTheme.typography.bodySmall)
              Text(
                "Click to point Cadence at the mounted folder.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }

      val others = devicesState.usbDevices.size - recognised.size
      if (others > 0) {
        Text(
          "$others other USB device${if (others == 1) "" else "s"} present (hubs, HID, etc.).",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

private fun UsbDevice.isCadenceStorage() = CadenceUsb.isKnownStorage(vendorId, productId)

private fun UsbDevice.friendlyName() = productName ?: manufacturerName ?: name

private fun UsbDevice.vendorProductLabel(): String {
  val v = (vendorId and 0xffff).toString(16).padStart(4, '0')
  val p = (productId and 0xffff).toString(16).padStart(4, '0')
  return "USB $v:$p"
}

@Composable
expect fun rememberFileExplorerOpenLauncher(
  onGranted: (name: String, path: Path) -> Unit
): () -> Unit

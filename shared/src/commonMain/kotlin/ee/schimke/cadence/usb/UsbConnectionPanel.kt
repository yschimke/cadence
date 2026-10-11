package ee.schimke.cadence.usb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

@Composable
fun UsbConnectionPanel(manager: UsbManager) {
  val connection by manager.connection.collectAsState(UsbConnection())
  Column {
    Text(connection.message)
    Row {
      Button(
        enabled = connection.available && !connection.busy,
        onClick = manager::requestConnection,
      ) {
        Text("Connect USB")
      }
      TextButton(onClick = manager::disconnect) { Text("Disconnect USB") }
    }
  }
}

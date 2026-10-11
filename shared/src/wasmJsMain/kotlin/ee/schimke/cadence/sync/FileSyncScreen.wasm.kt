package ee.schimke.cadence.sync

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import ee.schimke.cadence.browser.*
import ee.schimke.cadence.devices.PickedDirectory
import kotlinx.serialization.json.Json

@Composable
actual fun rememberDirectorySourceLauncher(
  onPicked: (name: String, treeUri: String) -> Unit
): () -> Unit {
  var error by remember { mutableStateOf<String?>(null) }
  val callback by rememberUpdatedState(onPicked)
  error?.let { message ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("Directory access") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
    )
  }
  return {
    pickBrowserDirectory(
      {
        val picked = Json.decodeFromString<PickedDirectory>(it)
        error = null
        callback(picked.name, picked.location)
      },
      { error = it },
    )
  }
}

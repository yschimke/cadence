package ee.schimke.cadence.devices

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import ee.schimke.cadence.browser.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath

@Serializable data class PickedDirectory(val name: String, val location: String)

@Composable
actual fun rememberFileExplorerOpenLauncher(
  onGranted: (name: String, path: Path) -> Unit
): () -> Unit {
  var error by remember { mutableStateOf<String?>(null) }
  val callback by rememberUpdatedState(onGranted)
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
        callback(picked.name, picked.location.toPath())
      },
      { error = it },
    )
  }
}

package ee.schimke.cadence.sync

import androidx.compose.runtime.*
import ee.schimke.cadence.devices.chooseDirectory
import kotlinx.coroutines.launch

@Composable
actual fun rememberDirectorySourceLauncher(
  onPicked: (name: String, treeUri: String) -> Unit
): () -> Unit {
  val scope = rememberCoroutineScope()
  val callback by rememberUpdatedState(onPicked)
  return { scope.launch { chooseDirectory()?.let { callback(it.name, it.absolutePath) } } }
}

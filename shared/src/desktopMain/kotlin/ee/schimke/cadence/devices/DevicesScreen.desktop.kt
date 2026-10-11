package ee.schimke.cadence.devices

import androidx.compose.runtime.*
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import okio.Path
import okio.Path.Companion.toOkioPath

@Composable
actual fun rememberFileExplorerOpenLauncher(
  onGranted: (name: String, path: Path) -> Unit
): () -> Unit {
  val scope = rememberCoroutineScope()
  val callback by rememberUpdatedState(onGranted)
  return { scope.launch { chooseDirectory()?.let { callback(it.name, it.toOkioPath()) } } }
}

suspend fun chooseDirectory(): java.io.File? = suspendCancellableCoroutine { continuation ->
  SwingUtilities.invokeLater {
    if (continuation.isActive) {
      val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
      val result =
        if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) picker.selectedFile
        else null
      if (continuation.isActive) continuation.resume(result)
    }
  }
}

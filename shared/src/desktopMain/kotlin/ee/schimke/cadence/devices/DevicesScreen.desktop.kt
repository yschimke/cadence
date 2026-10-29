package ee.schimke.cadence.devices

import androidx.compose.runtime.*
import java.io.File
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
  return {
    scope.launch {
      val mounts = usbMountCandidates()
      if (mounts.size == 1) {
        callback(mounts.single().name, mounts.single().toOkioPath())
      } else {
        chooseDirectory(mounts.firstOrNull()?.parentFile)?.let {
          callback(it.name, it.toOkioPath())
        }
      }
    }
  }
}

/** Mount points for freshly inserted removable media, per OS. */
private fun usbMountCandidates(): List<File> {
  val os = System.getProperty("os.name").orEmpty().lowercase()
  return when {
    "mac" in os || "darwin" in os -> macMounts()
    "windows" in os -> windowsRemovableRoots()
    else -> linuxUdisksMounts()
  }
}

/** udisks2 defaults: /run/media/$USER (modern) and /media/$USER (older). */
private fun linuxUdisksMounts(): List<File> {
  val user = System.getProperty("user.name") ?: return emptyList()
  return listOf(File("/run/media/$user"), File("/media/$user"))
    .filter { it.isDirectory }
    .flatMap { it.listFiles()?.toList().orEmpty() }
    .filter { it.isDirectory }
}

/** macOS mounts everything (incl. system) under /Volumes; skip the volume that is the root. */
private fun macMounts(): List<File> {
  val root = File("/").canonicalFile
  return File("/Volumes").listFiles().orEmpty().filter {
    it.isDirectory && it.canonicalFile != root
  }
}

/**
 * Windows: list roots marked "Removable Disk" by the OS. [javax.swing.filechooser.FileSystemView]
 * exposes the drive type without native code.
 */
private fun windowsRemovableRoots(): List<File> {
  val view = javax.swing.filechooser.FileSystemView.getFileSystemView()
  return File.listRoots().orEmpty().filter {
    it.isDirectory &&
      view.getSystemTypeDescription(it)?.contains("removable", ignoreCase = true) == true
  }
}

suspend fun chooseDirectory(startAt: File? = null): File? =
  suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater {
      if (continuation.isActive) {
        val picker =
          JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            startAt?.takeIf { it.isDirectory }?.let { currentDirectory = it }
          }
        val result =
          if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) picker.selectedFile
          else null
        if (continuation.isActive) continuation.resume(result)
      }
    }
  }

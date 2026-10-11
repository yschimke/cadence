package ee.schimke.cadence.filelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.http.decodeURLPart
import okio.FileSystem
import okio.Path

data class DirectoryEntry(val path: Path, val directory: Boolean)

expect suspend fun listDirectory(fileSystem: FileSystem?, path: Path): List<DirectoryEntry>

@Composable
fun FileTree(root: Path, fileSystem: FileSystem?, modifier: Modifier = Modifier) {
  Column(modifier.verticalScroll(rememberScrollState())) { FileNode(root, true, fileSystem, 0) }
}

@Composable
private fun FileNode(path: Path, directory: Boolean, fileSystem: FileSystem?, depth: Int) {
  var expanded by remember(path) { mutableStateOf(depth == 0) }
  var children by remember(path) { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
  var error by remember(path) { mutableStateOf<String?>(null) }
  LaunchedEffect(path, expanded) {
    if (expanded && directory) {
      try {
        children = listDirectory(fileSystem, path)
        error = null
      } catch (e: Exception) {
        error = e.message ?: "Cannot read directory"
      }
    }
  }
  Text(
    (if (directory) (if (expanded) "▾ " else "▸ ") else "") + displayName(path),
    Modifier.padding(start = (depth * 16).dp, top = 6.dp).clickable(enabled = directory) {
      expanded = !expanded
    },
  )
  if (expanded) {
    error?.let { Text(it) }
    children.forEach { FileNode(it.path, it.directory, fileSystem, depth + 1) }
  }
}

private fun displayName(path: Path): String {
  if (!path.toString().startsWith("content:")) return path.name.ifEmpty { path.toString() }
  return runCatching { path.name.decodeURLPart().substringAfterLast('/').substringAfterLast(':') }
    .getOrDefault(path.name)
}

package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
expect fun CloudSyncHomeCard(
  status: CloudSyncStatus,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
  onDownload: () -> Unit,
  onProcess: () -> Unit,
)

@Composable
fun CloudSyncHomeFallback(
  status: CloudSyncStatus,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
  onDownload: () -> Unit,
  onProcess: () -> Unit,
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
          Text("Sync with cloud", style = MaterialTheme.typography.titleMedium)
          Text(status.source)
        }
        if (status.hasSource)
          IconButton(onClick = onRefresh, enabled = status.canRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh podcasts")
          }
      }
      if (status.hasSource) {
        Row {
          TextButton(onClick = onDownload, enabled = status.canDownload) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text(status.downloadNeeded?.let { "$it to download" } ?: status.downloads)
          }
          TextButton(onClick = onProcess, enabled = status.canProcess) {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
            Text(status.processingNeeded?.let { "$it to process" } ?: status.processing)
          }
        }
        status.downloadedCount?.let { Text("$it downloaded") }
        Text(status.progress ?: status.checked)
      }
      if (!status.hasSource) TextButton(onClick = onOpen) { Text("Connect") }
    }
  }
}

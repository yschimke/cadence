package ee.schimke.cadence.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun HomeScreen(
  onFileExplorer: () -> Unit,
  onBookmarks: () -> Unit,
  onBluetoothControls: () -> Unit,
  onFileSync: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = metroViewModel<HomeViewModel>()

  HomeContent(
    onFileExplorer = onFileExplorer,
    onBookmarks = onBookmarks,
    onBluetoothControls = onBluetoothControls,
    onFileSync = onFileSync,
    modifier = modifier,
  )
}

@Composable
internal fun HomeContent(
  onFileExplorer: () -> Unit,
  onBookmarks: () -> Unit,
  onBluetoothControls: () -> Unit,
  onFileSync: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().safeContentPadding().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      "Cadence",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Button(onClick = onBluetoothControls, modifier = Modifier.fillMaxWidth()) {
      Text("Bluetooth Controls")
    }
    Button(onClick = onFileSync, modifier = Modifier.fillMaxWidth()) { Text("File Sync") }
    Button(onClick = onFileExplorer, modifier = Modifier.fillMaxWidth()) { Text("File Explorer") }
    Button(onClick = onBookmarks, modifier = Modifier.fillMaxWidth()) { Text("Bookmarks") }
  }
}

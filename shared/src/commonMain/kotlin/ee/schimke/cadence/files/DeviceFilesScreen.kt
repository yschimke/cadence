package ee.schimke.cadence.files

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.filesystem.FileSystemBonsaiStyle
import ee.schimke.cadence.filelist.cadenceFileSystemTree
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun DeviceFilesScreen(modifier: Modifier = Modifier) {
  val viewModel = metroViewModel<DeviceFilesViewModel>()

  val uiState by viewModel.uiState.collectAsState()

  DeviceFilesContent(uiState, modifier)
}

@Composable
internal fun DeviceFilesContent(
  uiState: DeviceFilesViewModel.UiState,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().safeContentPadding(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Files " + uiState.name)

    if (uiState is DeviceFilesViewModel.UiState.Loaded) {
      val loaded = uiState

      Surface {
        Column {
          Text("Volume Info")
          Text(loaded.volume.toString())
        }
      }

      val files = loaded

      Bonsai(
        cadenceFileSystemTree(rootPath = files.root, fileSystem = files.fileSystem),
        style = FileSystemBonsaiStyle(),
        modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color.Black),
      )
    } else if (uiState is DeviceFilesViewModel.UiState.Loading) {
      Text("Loading...")
    }
  }
}

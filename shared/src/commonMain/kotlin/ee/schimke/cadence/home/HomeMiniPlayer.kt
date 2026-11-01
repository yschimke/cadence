package ee.schimke.cadence.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.cadence.bluetooth.BluetoothState

@Composable
expect fun HomeMiniPlayer(
  state: BluetoothState,
  onPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
)

@Composable
fun HomeMiniPlayerFallback(
  state: BluetoothState,
  onPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
) {
  Surface {
    Row(Modifier.fillMaxWidth()) {
      Text(
        if (state.connectedDevice == null) "Playback unavailable over USB" else "Headphones",
        Modifier.weight(1f),
      )
      IconButton(enabled = state.connectedDevice != null, onClick = onPrevious) {
        Icon(Icons.Filled.SkipPrevious, "Previous")
      }
      IconButton(enabled = state.connectedDevice != null, onClick = onPlayPause) {
        Icon(Icons.Filled.PlayArrow, "Play or pause")
      }
      IconButton(enabled = state.connectedDevice != null, onClick = onNext) {
        Icon(Icons.Filled.SkipNext, "Next")
      }
    }
  }
}

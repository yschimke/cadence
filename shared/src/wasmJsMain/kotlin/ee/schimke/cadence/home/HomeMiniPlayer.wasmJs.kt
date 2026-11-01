package ee.schimke.cadence.home

import androidx.compose.runtime.Composable
import ee.schimke.cadence.bluetooth.BluetoothState

@Composable
actual fun HomeMiniPlayer(
  state: BluetoothState,
  onPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
) = HomeMiniPlayerFallback(state, onPrevious, onPlayPause, onNext)

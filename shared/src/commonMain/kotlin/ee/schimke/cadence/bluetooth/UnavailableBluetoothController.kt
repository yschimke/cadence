package ee.schimke.cadence.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow

/** Classic audio controls need OS integration; browsers cannot use RFCOMM through Web Bluetooth. */
class UnavailableBluetoothController : BluetoothController {
  override val supported = false

  override val state = MutableStateFlow(BluetoothState())

  override fun refresh() {}

  override fun setVolume(percent: Int) {}

  override fun adjustVolume(delta: Int) {}

  override fun toggleMute() {}

  override fun play() {}

  override fun pause() {}

  override fun playPause() {}

  override fun next() {}

  override fun previous() {}

  override fun stop() {}

  override fun fastForward() {}

  override fun rewind() {}

  override fun openSystemBluetoothSettings() {}

  override fun requestMediaAccess() {}

  override suspend fun setWorkingMode(mode: WorkingMode) =
    "Classic Bluetooth controls are available in the Android app."

  override suspend fun setEqualizer(preset: EqualizerPreset) =
    "Classic Bluetooth controls are available in the Android app."

  override suspend fun setPlaybackOrder(order: PlaybackOrder) =
    "Classic Bluetooth controls are available in the Android app."

  override suspend fun dispatchAdvanced(command: AdvancedCommand) =
    "Classic Bluetooth controls are available in the Android app."
}

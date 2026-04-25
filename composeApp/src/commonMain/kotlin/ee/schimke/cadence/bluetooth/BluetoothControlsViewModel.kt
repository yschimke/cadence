package ee.schimke.cadence.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(BluetoothControlsViewModel::class)
@Inject
class BluetoothControlsViewModel(
    private val controller: BluetoothController,
) : ViewModel() {

    val state: StateFlow<BluetoothState> = controller.state.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BluetoothState()
    )

    private val _lastCommandResult = MutableStateFlow<String?>(null)
    val lastCommandResult: StateFlow<String?> = _lastCommandResult.asStateFlow()

    fun refresh() = controller.refresh()

    fun setVolume(percent: Int) = controller.setVolume(percent)
    fun adjustVolume(delta: Int) = controller.adjustVolume(delta)
    fun toggleMute() = controller.toggleMute()

    fun play() = controller.play()
    fun pause() = controller.pause()
    fun playPause() = controller.playPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun stop() = controller.stop()
    fun fastForward() = controller.fastForward()
    fun rewind() = controller.rewind()

    fun openSystemBluetoothSettings() = controller.openSystemBluetoothSettings()
    fun requestMediaAccess() = controller.requestMediaAccess()

    fun dispatch(command: AdvancedCommand) {
        viewModelScope.launch {
            val result = controller.dispatchAdvanced(command)
            _lastCommandResult.value = "${command.label}: $result"
        }
    }

    fun consumeLastResult() {
        _lastCommandResult.value = null
    }
}

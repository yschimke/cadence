package ee.schimke.cadence.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun BluetoothControlsScreen(modifier: Modifier = Modifier) {
  val viewModel = metroViewModel<BluetoothControlsViewModel>()
  val state by viewModel.state.collectAsState()
  val lastResult by viewModel.lastCommandResult.collectAsState()
  val snackbarHost = remember { SnackbarHostState() }

  LaunchedEffect(Unit) { viewModel.refresh() }
  LaunchedEffect(lastResult) {
    lastResult?.let {
      snackbarHost.showSnackbar(it)
      viewModel.consumeLastResult()
    }
  }

  BluetoothControlsContent(
    modifier = modifier,
    state = state,
    snackbarHost = snackbarHost,
    onRefresh = viewModel::refresh,
    onSetVolume = viewModel::setVolume,
    onAdjustVolume = viewModel::adjustVolume,
    onToggleMute = viewModel::toggleMute,
    onPlay = viewModel::play,
    onPause = viewModel::pause,
    onPlayPause = viewModel::playPause,
    onPrevious = viewModel::previous,
    onNext = viewModel::next,
    onStop = viewModel::stop,
    onFastForward = viewModel::fastForward,
    onRewind = viewModel::rewind,
    onOpenSettings = viewModel::openSystemBluetoothSettings,
    onRequestMediaAccess = viewModel::requestMediaAccess,
    onSelectWorkingMode = viewModel::selectWorkingMode,
    onSelectEqualizer = viewModel::selectEqualizer,
    onSelectPlaybackOrder = viewModel::selectPlaybackOrder,
    onAdvanced = viewModel::dispatch,
  )
}

@Composable
fun BluetoothControlsContent(
  modifier: Modifier = Modifier,
  state: BluetoothState,
  snackbarHost: SnackbarHostState,
  onRefresh: () -> Unit,
  onSetVolume: (Int) -> Unit,
  onAdjustVolume: (Int) -> Unit,
  onToggleMute: () -> Unit,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onStop: () -> Unit,
  onFastForward: () -> Unit,
  onRewind: () -> Unit,
  onOpenSettings: () -> Unit,
  onRequestMediaAccess: () -> Unit,
  onSelectWorkingMode: (WorkingMode) -> Unit,
  onSelectEqualizer: (EqualizerPreset) -> Unit = {},
  onSelectPlaybackOrder: (PlaybackOrder) -> Unit = {},
  onAdvanced: (AdvancedCommand) -> Unit,
) {
  Box(modifier = modifier) {
    // Cards flow into as many ~360dp columns as fit: one on a phone, two or three on a tablet,
    // rather than one column stretched across the window.
    LazyVerticalStaggeredGrid(
      columns = StaggeredGridCells.Adaptive(360.dp),
      modifier = Modifier.fillMaxWidth().safeContentPadding(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalItemSpacing = 12.dp,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { ConnectionCard(state, onOpenSettings, onRefresh) }
      if (state.permissionMissing) {
        item { PermissionWarningCard(onOpenSettings) }
      }
      item { WorkingModeCard(state.workingMode, state.openSwimConnected, onSelectWorkingMode) }
      item { NowPlayingCard(state, onRequestMediaAccess) }
      item {
        TransportCard(
          // Headset transport only applies once Cadence has switched an OpenSwim Pro to MP3.
          mode = if (state.localAudioControls) WorkingMode.Mp3 else WorkingMode.Bluetooth,
          onPlay = onPlay,
          onPause = onPause,
          onPlayPause = onPlayPause,
          onPrevious = onPrevious,
          onNext = onNext,
          onStop = onStop,
          onRewind = onRewind,
          onFastForward = onFastForward,
          playing =
            if (state.localAudioControls) state.headsetPlaying == true
            else state.mediaInfo?.playing == true,
        )
      }
      item { VolumeCard(state, onSetVolume, onAdjustVolume, onToggleMute) }
      item { EqPresetsCard(state, onSelectEqualizer) }
      item { PlaybackOrderCard(state, onSelectPlaybackOrder) }
      item { AdvancedCommandsCard(onAdvanced) }
    }
    SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
private fun ConnectionCard(
  state: BluetoothState,
  onOpenSettings: () -> Unit,
  onRefresh: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val device = state.connectedDevice
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector =
            if (device != null) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
          contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            text = device?.name ?: "No device connected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (device != null) {
            Text(
              text = device.address,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        TextButton(onClick = onRefresh) { Text("Refresh") }
      }
      if (device != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          device.profileNames.forEach { profile ->
            AssistChip(onClick = {}, label = { Text(profile) })
          }
        }
        if (device.batteryPercent != null) {
          Text("Battery: ${device.batteryPercent}%")
        }
        if (device.codec != null) {
          Text("Codec: ${device.codec}")
        }
      }
      OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Open system Bluetooth settings")
      }
    }
  }
}

@Composable
private fun PermissionWarningCard(onOpenSettings: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Bluetooth permission required", fontWeight = FontWeight.SemiBold)
      Text("Grant the BLUETOOTH_CONNECT permission to read connected device state.")
      OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
    }
  }
}

@Composable
private fun NowPlayingCard(state: BluetoothState, onRequestMediaAccess: () -> Unit) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "Now playing",
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      val info = state.mediaInfo
      when {
        state.localAudioControls ->
          // The phone's media session is not what the headset plays from its own storage.
          Text(
            "Playing from headphone storage. The headset does not report track details.",
            style = MaterialTheme.typography.bodyMedium,
          )
        !state.mediaAccessGranted -> {
          Text("Media metadata is unavailable until notification access is granted.")
          OutlinedButton(onClick = onRequestMediaAccess) { Text("Grant media access") }
        }
        info == null -> Text("No active media session.")
        else -> {
          Text(info.title ?: "Unknown title", fontWeight = FontWeight.SemiBold)
          info.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
          info.album?.let {
            Text(
              it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          info.packageName?.let {
            Text(
              "Source: $it",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (info.durationMs != null && info.positionMs != null && info.durationMs > 0) {
            val pct = (info.positionMs.toFloat() / info.durationMs.toFloat()).coerceIn(0f, 1f)
            Slider(value = pct, onValueChange = {}, enabled = false)
            Text(
              "${formatTime(info.positionMs)} / ${formatTime(info.durationMs)}",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TransportCard(
  mode: WorkingMode,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onStop: () -> Unit,
  onRewind: () -> Unit,
  onFastForward: () -> Unit,
  playing: Boolean,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onPrevious) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
      }
      if (mode != WorkingMode.Mp3) {
        IconButton(onClick = onRewind) {
          Icon(Icons.Filled.FastRewind, contentDescription = "Rewind")
        }
      }
      // MP3 mode sends explicit Play/Pause: the headset has no captured toggle command.
      FilledIconButton(
        onClick =
          when {
            mode != WorkingMode.Mp3 -> onPlayPause
            playing -> onPause
            else -> onPlay
          }
      ) {
        Icon(
          imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playing) "Pause" else "Play",
        )
      }
      IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
      if (mode != WorkingMode.Mp3) {
        IconButton(onClick = onFastForward) {
          Icon(Icons.Filled.FastForward, contentDescription = "Fast forward")
        }
        IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
      }
    }
  }
}

@Composable
private fun VolumeCard(
  state: BluetoothState,
  onSetVolume: (Int) -> Unit,
  onAdjustVolume: (Int) -> Unit,
  onToggleMute: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (state.localAudioControls) {
        HeadsetVolume(state.headsetVolumePercent, onSetVolume, onAdjustVolume)
        return@Column
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Volume",
          modifier = Modifier.semantics { heading() },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text("${state.volumePercent}%")
      }
      Slider(
        value = state.volumePercent.toFloat(),
        onValueChange = { onSetVolume(it.toInt()) },
        valueRange = 0f..100f,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { onAdjustVolume(-1) }) {
          Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume down")
        }
        IconButton(onClick = onToggleMute) {
          Icon(
            imageVector =
              if (state.muted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (state.muted) "Unmute" else "Mute",
          )
        }
        IconButton(onClick = { onAdjustVolume(1) }) {
          Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume up")
        }
      }
    }
  }
}

/**
 * Headset (MP3 mode) volume. Each write opens a vendor channel, so the slider commits on release
 * rather than on every drag step. The level shown is the last one the headset acknowledged.
 */
@Composable
private fun HeadsetVolume(
  acknowledged: Int?,
  onSetVolume: (Int) -> Unit,
  onAdjustVolume: (Int) -> Unit,
) {
  var dragging by remember { mutableStateOf<Float?>(null) }
  val shown = dragging ?: (acknowledged ?: 50).toFloat()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      "Headphone volume",
      modifier = Modifier.semantics { heading() },
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.weight(1f))
    Text(if (acknowledged == null && dragging == null) "Not set" else "${shown.toInt()}%")
  }
  Slider(
    value = shown,
    onValueChange = { dragging = it },
    onValueChangeFinished = {
      dragging?.let { onSetVolume(it.toInt()) }
      dragging = null
    },
    valueRange = 0f..100f,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    IconButton(onClick = { onAdjustVolume(-1) }, enabled = acknowledged != null) {
      Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume down")
    }
    IconButton(onClick = { onAdjustVolume(1) }, enabled = acknowledged != null) {
      Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume up")
    }
  }
  Text(
    "Sets the volume of MP3 playback on the headphones.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private val eqPresetCommands =
  listOf(
    AdvancedCommand.EqFlat,
    AdvancedCommand.EqVocal,
    AdvancedCommand.ToggleSwimmingMode,
  )

@Composable
private fun WorkingModeCard(
  selected: WorkingMode,
  enabled: Boolean,
  onSelect: (WorkingMode) -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "Working mode",
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      val options = WorkingMode.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
          SegmentedButton(
            selected = mode == selected,
            onClick = { onSelect(mode) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            icon = {
              Icon(
                imageVector =
                  when (mode) {
                    WorkingMode.Bluetooth -> Icons.Filled.Bluetooth
                    WorkingMode.Mp3 -> Icons.Filled.MusicNote
                  },
                contentDescription = null,
              )
            },
            label = {
              Text(
                when (mode) {
                  WorkingMode.Bluetooth -> "Bluetooth"
                  WorkingMode.Mp3 -> "MP3"
                }
              )
            },
          )
        }
      }
      Text(
        "Switches OpenSwim Pro between Bluetooth and on-device MP3 playback. Local Audio transport, volume, EQ, and playback order are available in MP3 mode.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EqPresetsCard(state: BluetoothState, onSelect: (EqualizerPreset) -> Unit) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Tune, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
          "EQ presets",
          modifier = Modifier.semantics { heading() },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      ChoiceRow(
        options = EqualizerPreset.entries,
        selected = state.equalizer,
        enabled = state.openSwimConnected,
        label = { it.label },
        onSelect = onSelect,
      )
      if (!state.openSwimConnected) {
        SupportingText("Connect an OpenSwim Pro to change its EQ.")
      }
    }
  }
}

@Composable
private fun PlaybackOrderCard(state: BluetoothState, onSelect: (PlaybackOrder) -> Unit) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Shuffle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
          "MP3 playback order",
          modifier = Modifier.semantics { heading() },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      ChoiceRow(
        options = PlaybackOrder.entries,
        selected = state.playbackOrder,
        enabled = state.localAudioControls,
        label = { it.label },
        onSelect = onSelect,
      )
      if (!state.localAudioControls) {
        SupportingText("Available when an OpenSwim Pro is in MP3 mode.")
      }
    }
  }
}

/**
 * A single-choice row whose selection may be unknown: nothing is highlighted until acknowledged.
 */
@Composable
private fun <T> ChoiceRow(
  options: List<T>,
  selected: T?,
  enabled: Boolean,
  label: (T) -> String,
  onSelect: (T) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        selected = option == selected,
        onClick = { onSelect(option) },
        enabled = enabled,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        label = { Text(label(option), maxLines = 1) },
      )
    }
  }
}

@Composable
private fun SupportingText(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun AdvancedCommandsCard(onAdvanced: (AdvancedCommand) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            "Advanced commands",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            "Extra controls reported by your headphones. Most are previews until firmware support lands.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { expanded = !expanded }) {
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
          )
        }
      }
      if (expanded) {
        AdvancedCommand.entries
          .filterNot { it in eqPresetCommands }
          .groupBy { it.category }
          .forEach { (category, commands) ->
            HorizontalDivider()
            Text(
              category.name,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            commands.forEach { cmd ->
              OutlinedButton(onClick = { onAdvanced(cmd) }, modifier = Modifier.fillMaxWidth()) {
                Text(cmd.label)
              }
            }
          }
      }
    }
  }
}

private fun formatTime(ms: Long): String {
  val totalSec = ms / 1000
  val mins = totalSec / 60
  val secs = totalSec % 60
  return "$mins:${secs.toString().padStart(2, '0')}"
}

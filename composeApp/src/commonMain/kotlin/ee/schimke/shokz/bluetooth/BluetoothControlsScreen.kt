package ee.schimke.shokz.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.shokz.metro.metroViewModel

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
        onPlayPause = viewModel::playPause,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onStop = viewModel::stop,
        onFastForward = viewModel::fastForward,
        onRewind = viewModel::rewind,
        onOpenSettings = viewModel::openSystemBluetoothSettings,
        onRequestMediaAccess = viewModel::requestMediaAccess,
        onAdvanced = viewModel::dispatch,
    )
}

@Composable
internal fun BluetoothControlsContent(
    modifier: Modifier = Modifier,
    state: BluetoothState,
    snackbarHost: SnackbarHostState,
    onRefresh: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onAdjustVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    onAdvanced: (AdvancedCommand) -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .safeContentPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ConnectionCard(state, onOpenSettings, onRefresh) }
            if (state.permissionMissing) {
                item { PermissionWarningCard(onOpenSettings) }
            }
            item { NowPlayingCard(state, onRequestMediaAccess) }
            item { TransportCard(onPlayPause, onPrevious, onNext, onStop, onRewind, onFastForward, state.mediaInfo?.playing == true) }
            item { VolumeCard(state, onSetVolume, onAdjustVolume, onToggleMute) }
            item { AdvancedCommandsCard(onAdvanced) }
        }
        SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConnectionCard(state: BluetoothState, onOpenSettings: () -> Unit, onRefresh: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val device = state.connectedDevice
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (device != null) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
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
            Text("Now playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val info = state.mediaInfo
            when {
                !state.mediaAccessGranted -> {
                    Text("Media metadata is unavailable until notification access is granted.")
                    OutlinedButton(onClick = onRequestMediaAccess) { Text("Grant media access") }
                }
                info == null -> Text("No active media session.")
                else -> {
                    Text(info.title ?: "Unknown title", fontWeight = FontWeight.SemiBold)
                    info.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    info.album?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    info.packageName?.let {
                        Text("Source: $it", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            IconButton(onClick = onRewind) { Icon(Icons.Filled.FastRewind, contentDescription = "Rewind") }
            IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
            FilledIconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
            IconButton(onClick = onFastForward) { Icon(Icons.Filled.FastForward, contentDescription = "Fast forward") }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        imageVector = if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
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

@Composable
private fun AdvancedCommandsCard(onAdvanced: (AdvancedCommand) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced commands", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Vendor (RCSP) commands. Most require RFCOMM transport (research/rcsp.md).",
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
                    .groupBy { it.category }
                    .forEach { (category, commands) ->
                        HorizontalDivider()
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        commands.forEach { cmd ->
                            OutlinedButton(
                                onClick = { onAdvanced(cmd) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(cmd.label) }
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
    return "%d:%02d".format(mins, secs)
}

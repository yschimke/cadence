package ee.schimke.shokz.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.shokz.datastore.proto.SourceKind
import ee.schimke.shokz.datastore.proto.StagedFile
import ee.schimke.shokz.datastore.proto.StagedStatus
import ee.schimke.shokz.datastore.proto.SyncSource
import ee.schimke.shokz.metro.metroViewModel

@Composable
fun FileSyncScreen(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<FileSyncViewModel>()
    val state by viewModel.state.collectAsState()
    val launcher = rememberDirectorySourceLauncher { name, uri ->
        viewModel.addLocalDirectory(name, uri)
    }

    var showAddNfs by remember { mutableStateOf(false) }

    if (showAddNfs) {
        AddNfsDialog(
            onDismiss = { showAddNfs = false },
            onConfirm = { name, host, path ->
                viewModel.addNfsShare(name, host, path)
                showAddNfs = false
            },
        )
    }

    FileSyncContent(
        modifier = modifier,
        state = state,
        onAddLocalDirectory = launcher,
        onAddNfsShare = { showAddNfs = true },
        onRemoveSource = viewModel::removeSource,
        onStageAllFromSource = viewModel::stageAllFromSource,
        onRemoveStaged = viewModel::removeStaged,
        onClearCompleted = viewModel::clearCompleted,
        onRetryFailures = viewModel::retryFailures,
        onSelectTargetDevice = viewModel::selectTargetDevice,
        onSetAutoSync = viewModel::setAutoSync,
        onSetUsbMatch = viewModel::setUsbMatch,
        onStartSync = viewModel::startSync,
        onCancelSync = viewModel::cancelSync,
    )
}

@Composable
internal fun FileSyncContent(
    modifier: Modifier = Modifier,
    state: FileSyncViewModel.UiState,
    onAddLocalDirectory: () -> Unit,
    onAddNfsShare: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onStageAllFromSource: (String) -> Unit,
    onRemoveStaged: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onRetryFailures: () -> Unit,
    onSelectTargetDevice: (String) -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetUsbMatch: (String) -> Unit,
    onStartSync: () -> Unit,
    onCancelSync: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .safeContentPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TargetDeviceCard(
                state = state,
                onSelectTargetDevice = onSelectTargetDevice,
                onSetAutoSync = onSetAutoSync,
                onSetUsbMatch = onSetUsbMatch,
            )
        }
        item {
            ProgressCard(
                state = state,
                onStartSync = onStartSync,
                onCancelSync = onCancelSync,
                onRetryFailures = onRetryFailures,
                onClearCompleted = onClearCompleted,
            )
        }
        item {
            SectionHeader(
                title = "Sources",
                subtitle = "Local directories (SAF) or NFS shares",
                trailing = {
                    OutlinedButton(onClick = onAddLocalDirectory) { Text("Add directory") }
                    Gap(8)
                    OutlinedButton(onClick = onAddNfsShare) { Text("Add NFS") }
                },
            )
        }
        if (state.sources.isEmpty()) {
            item {
                Text(
                    "No sources yet. Add a local directory (e.g. your podcast staging folder) to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                onRemove = { onRemoveSource(source.id) },
                onStageAll = { onStageAllFromSource(source.id) },
            )
        }
        item {
            SectionHeader(
                title = "Staging area (${state.stagedFiles.size} files)",
                subtitle = "Files queued to copy on next USB sync",
            )
        }
        if (state.stagedFiles.isEmpty()) {
            item { Text("Nothing staged.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(state.stagedFiles, key = { it.id }) { staged ->
            StagedFileRow(staged, onRemove = { onRemoveStaged(staged.id) })
        }
    }
}

@Composable
private fun Gap(width: Int) {
    Spacer(Modifier.width(width.dp))
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDeviceCard(
    state: FileSyncViewModel.UiState,
    onSelectTargetDevice: (String) -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetUsbMatch: (String) -> Unit,
) {
    val selected = state.devices.firstOrNull { it.id == state.preferences.target_device_id }
    var expanded by remember { mutableStateOf(false) }
    var matchEditing by remember(state.preferences.usb_match) { mutableStateOf(state.preferences.usb_match) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Target device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selected?.name ?: "Select managed device…",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    state.devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name.ifBlank { device.id }) },
                            onClick = {
                                onSelectTargetDevice(device.id)
                                expanded = false
                            },
                        )
                    }
                    if (state.devices.isEmpty()) {
                        DropdownMenuItem(text = { Text("No managed devices yet") }, onClick = {})
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.preferences.auto_sync_on_usb, onCheckedChange = onSetAutoSync)
                Gap(12)
                Text("Auto-sync on USB attach")
            }

            OutlinedTextField(
                value = matchEditing,
                onValueChange = { matchEditing = it.uppercase().take(9) },
                label = { Text("USB match (VID:PID, optional)") },
                placeholder = { Text("05E3:0761") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onSetUsbMatch(matchEditing.trim()) }) { Text("Save USB match") }
        }
    }
}

@Composable
private fun ProgressCard(
    state: FileSyncViewModel.UiState,
    onStartSync: () -> Unit,
    onCancelSync: () -> Unit,
    onRetryFailures: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    val progress = state.progress
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Gap(12)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (progress.running) "Syncing…" else "Idle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    progress.lastError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (progress.running || progress.filesTotal > 0) {
                LinearProgressIndicator(
                    progress = {
                        if (progress.filesTotal == 0) 0f
                        else (progress.filesCompleted.toFloat() / progress.filesTotal.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                progress.currentFileName?.let {
                    Text("$it (${progress.filesCompleted}/${progress.filesTotal})",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (progress.currentTotal > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (progress.currentBytes.toFloat() / progress.currentTotal.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartSync, enabled = !progress.running) { Text("Sync now") }
                if (progress.running) {
                    OutlinedButton(onClick = onCancelSync) { Text("Cancel") }
                }
                OutlinedButton(onClick = onRetryFailures) { Text("Retry failures") }
                TextButton(onClick = onClearCompleted) { Text("Clear completed") }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: SyncSource,
    onRemove: () -> Unit,
    onStageAll: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Gap(12)
                Column(Modifier.weight(1f)) {
                    Text(source.name.ifBlank { "Unnamed" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        source.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = {}, label = {
                    Text(if (source.kind == SourceKind.LOCAL_DIRECTORY) "LOCAL" else "NFS")
                })
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            }
            if (source.kind == SourceKind.NFS_SHARE) {
                Text(
                    "NFS browsing is not yet implemented. Saved for future mount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                OutlinedButton(onClick = onStageAll) { Text("Stage all files") }
            }
        }
    }
}

@Composable
private fun StagedFileRow(staged: StagedFile, onRemove: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(staged.display_name, fontWeight = FontWeight.SemiBold)
                    Text(
                        staged.target_relative_path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(staged.status.name) },
                )
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            }
            if (staged.status == StagedStatus.IN_PROGRESS && staged.size_bytes > 0) {
                LinearProgressIndicator(
                    progress = {
                        (staged.bytes_transferred.toFloat() / staged.size_bytes.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (staged.status == StagedStatus.FAILED && staged.error_message.isNotBlank()) {
                Text(
                    staged.error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddNfsDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, host: String, exportPath: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var exportPath by remember { mutableStateOf("/podcasts") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add NFS share") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") })
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") })
                OutlinedTextField(value = exportPath, onValueChange = { exportPath = it }, label = { Text("Export path") })
                Text(
                    "Note: NFS access is not yet implemented; the share is saved for later use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { host }, host, exportPath) },
                enabled = host.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
expect fun rememberDirectorySourceLauncher(
    onPicked: (name: String, treeUri: String) -> Unit,
): () -> Unit

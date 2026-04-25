package ee.schimke.cadence.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.NetworkConstraint
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun FileSyncScreen(modifier: Modifier = Modifier) {
  val viewModel = metroViewModel<FileSyncViewModel>()
  val state by viewModel.state.collectAsState()
  val suggestions by viewModel.suggestions.collectAsState()
  val launcher = rememberDirectorySourceLauncher { name, uri ->
    viewModel.addLocalDirectory(name, uri)
  }

  var showAddNfs by remember { mutableStateOf(false) }
  var showDiscover by remember { mutableStateOf(false) }
  var showAddProfile by remember { mutableStateOf(false) }

  if (showAddNfs) {
    AddNfsDialog(
      onDismiss = { showAddNfs = false },
      onConfirm = { name, host, path ->
        viewModel.addNfsShare(name, host, path)
        showAddNfs = false
      },
    )
  }
  if (showDiscover) {
    SourceSuggestionsDialog(
      suggestions = suggestions,
      onSelect = viewModel::openSuggestion,
      onDismiss = { showDiscover = false },
    )
  }
  if (showAddProfile) {
    AddProfileDialog(
      availableSources = state.sources,
      onDismiss = { showAddProfile = false },
      onConfirm = { name, sourceIds, interval, network ->
        viewModel.addProfile(name, sourceIds, interval, network)
        showAddProfile = false
      },
    )
  }

  FileSyncContent(
    modifier = modifier,
    state = state,
    onAddLocalDirectory = launcher,
    onAddNfsShare = { showAddNfs = true },
    onDiscoverApps = {
      viewModel.loadSuggestions()
      showDiscover = true
    },
    onRemoveSource = viewModel::removeSource,
    onAddProfile = { showAddProfile = true },
    onDeleteProfile = viewModel::deleteProfile,
    onRefreshProfile = viewModel::refreshNow,
    onToggleAutoRefresh = viewModel::toggleAutoRefresh,
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
  onDiscoverApps: () -> Unit,
  onRemoveSource: (String) -> Unit,
  onAddProfile: () -> Unit,
  onDeleteProfile: (String) -> Unit,
  onRefreshProfile: (String) -> Unit,
  onToggleAutoRefresh: (SyncProfile, Boolean) -> Unit,
  onSelectTargetDevice: (String) -> Unit,
  onSetAutoSync: (Boolean) -> Unit,
  onSetUsbMatch: (String) -> Unit,
  onStartSync: () -> Unit,
  onCancelSync: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxWidth().safeContentPadding(),
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
    item { ProgressCard(state = state, onStartSync = onStartSync, onCancelSync = onCancelSync) }
    item {
      SectionHeader(
        title = "Sync profiles",
        subtitle =
          "Each profile pulls from a set of sources into its own local folder, " +
            "ready to copy to the headphones on USB connect.",
        trailing = {
          Button(onClick = onAddProfile, enabled = state.sources.isNotEmpty()) {
            Text("Add profile")
          }
        },
      )
    }
    if (state.profiles.isEmpty()) {
      item {
        Text(
          if (state.sources.isEmpty())
            "Add a source first, then create a profile to combine sources into a single staging area."
          else "No profiles yet. Add one — its first refresh will run immediately.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    items(state.profiles, key = { it.id }) { profile ->
      ProfileCard(
        profile = profile,
        sources = state.sources,
        refresh = state.refresh,
        onRefreshNow = { onRefreshProfile(profile.id) },
        onToggleAuto = { onToggleAutoRefresh(profile, it) },
        onDelete = { onDeleteProfile(profile.id) },
      )
    }
    item {
      SectionHeader(
        title = "Sources",
        subtitle =
          "Pick any folder reachable through Android's system file picker — local, cloud, SMB, etc.",
        trailing = {
          OutlinedButton(onClick = onAddLocalDirectory) { Text("Add source") }
          Gap(8)
          OutlinedButton(onClick = onAddNfsShare) { Text("Add NFS") }
        },
      )
    }
    item { DiscoverAppsRow(onDiscoverApps = onDiscoverApps) }
    if (state.sources.isEmpty()) {
      item {
        Text(
          "No sources yet. Tap “Add source” to pick a local folder, Drive, OneDrive, " +
            "or any folder exposed by an installed file manager.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    items(state.sources, key = { it.id }) { source ->
      SourceCard(source = source, onRemove = { onRemoveSource(source.id) })
    }
  }
}

@Composable
private fun Gap(width: Int) {
  Spacer(Modifier.width(width.dp))
}

@Composable
private fun SectionHeader(
  title: String,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
  var matchEditing by
    remember(state.preferences.usb_match) { mutableStateOf(state.preferences.usb_match) }

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "Target headphones",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
          value = selected?.name ?: "Select managed device…",
          onValueChange = {},
          readOnly = true,
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
) {
  val sync = state.sync
  val refresh = state.refresh
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Sync, contentDescription = null)
        Gap(12)
        Column(Modifier.weight(1f)) {
          Text(
            when {
              sync.running -> "Copying to headphones…"
              refresh.running -> "Refreshing ${refresh.profileName ?: ""}".trim()
              else -> "Idle"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          val errorText = sync.lastError ?: refresh.lastError
          errorText?.let {
            Text(
              it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
      if (sync.running || sync.filesTotal > 0) {
        LinearProgressIndicator(
          progress = {
            if (sync.filesTotal == 0) 0f
            else (sync.filesCompleted.toFloat() / sync.filesTotal.toFloat()).coerceIn(0f, 1f)
          },
          modifier = Modifier.fillMaxWidth(),
        )
        sync.currentFileName?.let {
          Text(
            "${sync.currentProfileName?.plus(" • ").orEmpty()}$it (${sync.filesCompleted}/${sync.filesTotal})",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (sync.currentTotal > 0) {
          LinearProgressIndicator(
            progress = {
              (sync.currentBytes.toFloat() / sync.currentTotal.toFloat()).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStartSync, enabled = !sync.running) { Text("Sync to headphones") }
        if (sync.running) OutlinedButton(onClick = onCancelSync) { Text("Cancel") }
      }
    }
  }
}

@Composable
private fun ProfileCard(
  profile: SyncProfile,
  sources: List<SyncSource>,
  refresh: RefreshProgress,
  onRefreshNow: () -> Unit,
  onToggleAuto: (Boolean) -> Unit,
  onDelete: () -> Unit,
) {
  val isCurrentlyRefreshing = refresh.running && refresh.profileId == profile.id
  val sourceNames =
    profile.source_ids.mapNotNull { id -> sources.firstOrNull { it.id == id }?.name }
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Cloud, contentDescription = null)
        Gap(12)
        Column(Modifier.weight(1f)) {
          Text(
            profile.name,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            if (sourceNames.isEmpty()) "No sources" else sourceNames.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Filled.Close, contentDescription = "Delete profile")
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = profile.auto_refresh, onCheckedChange = onToggleAuto)
        Gap(12)
        Column(Modifier.weight(1f)) {
          Text(
            if (profile.auto_refresh)
              "Auto-refresh every ${profile.refresh_interval_minutes} min" +
                " on ${profile.network_constraint.label()}"
            else "Manual refresh only",
            style = MaterialTheme.typography.bodyMedium,
          )
          val last = profile.last_refreshed_at.ifBlank { null }
          Text(
            last?.let { "Last refreshed $it" } ?: "Never refreshed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (profile.last_error.isNotBlank()) {
            Text(
              profile.last_error,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
      if (isCurrentlyRefreshing) {
        LinearProgressIndicator(
          progress = {
            if (refresh.filesTotal == 0) 0f
            else (refresh.filesCompleted.toFloat() / refresh.filesTotal.toFloat()).coerceIn(0f, 1f)
          },
          modifier = Modifier.fillMaxWidth(),
        )
        refresh.currentFileName?.let {
          Text(
            "$it (${refresh.filesCompleted}/${refresh.filesTotal})",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefreshNow, enabled = !isCurrentlyRefreshing) {
          Icon(Icons.Filled.Refresh, contentDescription = null)
          Gap(4)
          Text("Refresh now")
        }
      }
    }
  }
}

private fun NetworkConstraint.label(): String =
  when (this) {
    NetworkConstraint.ANY -> "any network"
    NetworkConstraint.CONNECTED -> "any connection"
    NetworkConstraint.UNMETERED -> "Wi-Fi"
  }

@Composable
private fun SourceCard(source: SyncSource, onRemove: () -> Unit) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.Folder, contentDescription = null)
      Gap(12)
      Column(Modifier.weight(1f)) {
        Text(source.name.ifBlank { "Unnamed" }, fontWeight = FontWeight.SemiBold)
        Text(
          source.location,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (source.kind == SourceKind.NFS_SHARE) {
          Text(
            "NFS browsing not yet implemented; saved for future mount.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      AssistChip(
        onClick = {},
        label = { Text(if (source.kind == SourceKind.LOCAL_DIRECTORY) "LOCAL" else "NFS") },
      )
      IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
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
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Display name") },
        )
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") })
        OutlinedTextField(
          value = exportPath,
          onValueChange = { exportPath = it },
          label = { Text("Export path") },
        )
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
      ) {
        Text("Add")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProfileDialog(
  availableSources: List<SyncSource>,
  onDismiss: () -> Unit,
  onConfirm:
    (
      name: String, sourceIds: List<String>, intervalMinutes: Int, network: NetworkConstraint,
    ) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  val selected = remember { mutableStateOf(emptySet<String>()) }
  var interval by remember { mutableStateOf("60") }
  var network by remember { mutableStateOf(NetworkConstraint.UNMETERED) }
  var networkExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create sync profile") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Profile name") },
          placeholder = { Text("Podcasts") },
        )
        Text("Sources", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
          items(availableSources, key = { it.id }) { source ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = selected.value.contains(source.id),
                onCheckedChange = { checked ->
                  selected.value =
                    if (checked) selected.value + source.id else selected.value - source.id
                },
              )
              Text(source.name.ifBlank { source.location })
            }
          }
        }
        OutlinedTextField(
          value = interval,
          onValueChange = { interval = it.filter(Char::isDigit).take(4) },
          label = { Text("Auto-refresh interval (minutes, min 15)") },
        )
        ExposedDropdownMenuBox(
          expanded = networkExpanded,
          onExpandedChange = { networkExpanded = !networkExpanded },
        ) {
          OutlinedTextField(
            value = network.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Network constraint") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = networkExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = networkExpanded,
            onDismissRequest = { networkExpanded = false },
          ) {
            NetworkConstraint.values().forEach { value ->
              DropdownMenuItem(
                text = { Text(value.label()) },
                onClick = {
                  network = value
                  networkExpanded = false
                },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = name.isNotBlank() && selected.value.isNotEmpty(),
        onClick = {
          val minutes = interval.toIntOrNull()?.coerceAtLeast(15) ?: 60
          onConfirm(name.trim(), selected.value.toList(), minutes, network)
        },
      ) {
        Text("Create")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun DiscoverAppsRow(onDiscoverApps: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          "Don't see your storage?",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          "Drive, Dropbox, Nextcloud, SMB, FTP, WebDAV and more become pickable once" +
            " you install an app that exposes them through Android's file picker.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Gap(8)
      OutlinedButton(onClick = onDiscoverApps) { Text("Discover apps") }
    }
  }
}

@Composable
private fun SourceSuggestionsDialog(
  suggestions: List<SourceSuggestion>,
  onSelect: (SourceSuggestion) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Discover storage apps") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "These apps register a Storage Access Framework provider. Once installed," +
            " their folders appear in the Android file picker used by “Add source”.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestions.isEmpty()) {
          Text("Loading…", style = MaterialTheme.typography.bodyMedium)
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(suggestions, key = { it.packageName }) { suggestion ->
              SourceSuggestionRow(suggestion = suggestion, onSelect = onSelect)
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

@Composable
private fun SourceSuggestionRow(
  suggestion: SourceSuggestion,
  onSelect: (SourceSuggestion) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            suggestion.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          if (suggestion.installed) {
            Gap(8)
            AssistChip(onClick = {}, label = { Text("Installed") })
          }
        }
        Text(
          suggestion.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestion.capabilities.isNotEmpty()) {
          Text(
            suggestion.capabilities.joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Gap(8)
      OutlinedButton(onClick = { onSelect(suggestion) }) {
        Text(if (suggestion.installed) "Open" else "Install")
      }
    }
  }
}

@Composable
expect fun rememberDirectorySourceLauncher(
  onPicked: (name: String, treeUri: String) -> Unit
): () -> Unit

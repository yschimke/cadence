package ee.schimke.cadence.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun FileSyncScreen(modifier: Modifier = Modifier, onClose: () -> Unit, onManage: () -> Unit) {
  val viewModel = metroViewModel<FileSyncViewModel>()
  val state by viewModel.state.collectAsState()

  FileSyncContent(
    modifier = modifier,
    state = state,
    onStartSync = viewModel::startSync,
    onCancelSync = viewModel::cancelSync,
    onClose = onClose,
    onManage = onManage,
  )
}

@Composable
fun FileSyncContent(
  modifier: Modifier = Modifier,
  state: FileSyncViewModel.UiState,
  onStartSync: () -> Unit,
  onCancelSync: () -> Unit,
  onClose: () -> Unit,
  onManage: () -> Unit,
) {
  val sync = state.sync
  val device = state.devices.firstOrNull { it.id == state.preferences.target_device_id }
  val deviceLabel = device?.name?.takeIf { it.isNotBlank() } ?: "Headphones"
  val done = !sync.running && sync.filesTotal > 0 && sync.filesCompleted >= sync.filesTotal
  val syncableProfiles =
    remember(state.profiles) { state.profiles.filter { it.last_error.isBlank() } }
  val initialChecked = remember(syncableProfiles) { syncableProfiles.map { it.id }.toSet() }
  var checkedIds by remember(initialChecked) { mutableStateOf(initialChecked) }

  Column(modifier = modifier.fillMaxSize()) {
    SyncTopBar(done = done, onClose = onClose, onManage = onManage)
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
          SyncDeviceHero(
            label = deviceLabel,
            secondary =
              when {
                done -> "${sync.filesCompleted} files landed · safe to disconnect"
                sync.running -> "Syncing — don't unplug"
                state.profiles.isEmpty() -> "No profiles yet — open Manage to add one"
                else -> "Connected · ${syncableProfiles.size} profiles ready"
              },
            running = sync.running,
            done = done,
          )
        }
        when {
          done -> item { SyncDoneSummary(progress = sync) }
          sync.running -> item { SyncProgressView(progress = sync) }
          else -> {
            if (state.profiles.isNotEmpty()) {
              item { SectionLabel("Ready to sync") }
              items(state.profiles, key = { it.id }) { profile ->
                SyncReadyRow(
                  profile = profile,
                  checked = checkedIds.contains(profile.id),
                  onCheckedChange = { checked ->
                    checkedIds = if (checked) checkedIds + profile.id else checkedIds - profile.id
                  },
                )
              }
            }
          }
        }
        item { Spacer(Modifier.height(80.dp)) }
      }
    }
    if (!done) {
      SyncCta(
        running = sync.running,
        readyCount = checkedIds.intersect(syncableProfiles.map { it.id }.toSet()).size,
        onStartSync = onStartSync,
        onCancelSync = onCancelSync,
      )
    } else {
      DoneCta(onClose = onClose)
    }
  }
}

@Composable
private fun SyncTopBar(done: Boolean, onClose: () -> Unit, onManage: () -> Unit) {
  Surface(
    color =
      if (done) MaterialTheme.colorScheme.tertiaryContainer
      else MaterialTheme.colorScheme.primaryContainer,
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        ),
    tonalElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onClose) {
        Icon(
          Icons.Filled.Close,
          contentDescription = "Exit sync mode",
          tint =
            if (done) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Spacer(Modifier.width(4.dp))
      Text(
        if (done) "Sync complete" else "Sync mode",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color =
          if (done) MaterialTheme.colorScheme.onTertiaryContainer
          else MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onManage) {
        Text(
          "Manage",
          color =
            if (done) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun SyncDeviceHero(label: String, secondary: String, running: Boolean, done: Boolean) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(CircleShape)
            .background(
              when {
                done -> MaterialTheme.colorScheme.tertiaryContainer
                running -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
              }
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Headset,
          contentDescription = null,
          tint =
            when {
              done -> MaterialTheme.colorScheme.onTertiaryContainer
              else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
          secondary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SyncReadyRow(
  profile: SyncProfile,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val hasError = profile.last_error.isNotBlank()
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = checked && !hasError,
        onCheckedChange = onCheckedChange,
        enabled = !hasError,
      )
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(
          profile.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          if (hasError) "Last refresh failed — fix in Curate"
          else profile.last_refreshed_at.ifBlank { "Never refreshed" }.let { "Last refreshed $it" },
          style = MaterialTheme.typography.bodySmall,
          color =
            if (hasError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SyncProgressView(progress: SyncProgress) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        progress.currentProfileName ?: "Syncing",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      progress.currentFileName?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val pctFiles =
        if (progress.filesTotal == 0) 0f
        else (progress.filesCompleted.toFloat() / progress.filesTotal.toFloat()).coerceIn(0f, 1f)
      LinearProgressIndicator(progress = { pctFiles }, modifier = Modifier.fillMaxWidth())
      Text(
        "${progress.filesCompleted} of ${progress.filesTotal} files",
        style = MaterialTheme.typography.bodySmall,
      )
      if (progress.currentTotal > 0) {
        val pctBytes =
          (progress.currentBytes.toFloat() / progress.currentTotal.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { pctBytes }, modifier = Modifier.fillMaxWidth())
        Text(
          "${progress.currentBytes / (1024 * 1024)} MB / " +
            "${progress.currentTotal / (1024 * 1024)} MB",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun SyncDoneSummary(progress: SyncProgress) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "All caught up",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "${progress.filesCompleted} files copied to your headphones.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        "You can disconnect the USB cable now.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SyncCta(
  running: Boolean,
  readyCount: Int,
  onStartSync: () -> Unit,
  onCancelSync: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ),
  ) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      if (running) {
        OutlinedButton(onClick = onCancelSync, modifier = Modifier.weight(1f)) { Text("Cancel") }
      } else {
        Button(onClick = onStartSync, enabled = readyCount > 0, modifier = Modifier.weight(1f)) {
          Text(if (readyCount == 0) "Nothing to sync" else "Sync now ($readyCount profiles)")
        }
      }
    }
  }
}

@Composable
private fun DoneCta(onClose: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ),
  ) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Done") }
    }
  }
}

@Composable
expect fun rememberDirectorySourceLauncher(
  onPicked: (name: String, treeUri: String) -> Unit
): () -> Unit

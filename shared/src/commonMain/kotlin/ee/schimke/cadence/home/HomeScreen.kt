package ee.schimke.cadence.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun HomeScreen(
  onBluetoothControls: () -> Unit,
  onFileSync: () -> Unit,
  onManageSync: () -> Unit,
  onBookmarks: () -> Unit,
  onFileExplorer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = metroViewModel<HomeViewModel>()
  val state by viewModel.state.collectAsState()

  HomeContent(
    state = state,
    onRefreshProfile = viewModel::refreshNow,
    onToggleAutoRefresh = viewModel::toggleAutoRefresh,
    onAddProfile = onManageSync,
    onManageSync = onManageSync,
    onBookmarks = onBookmarks,
    onFileExplorer = onFileExplorer,
    onBluetoothControls = onBluetoothControls,
    onSwitchToSync = onFileSync,
    modifier = modifier,
  )
}

@Composable
fun HomeContent(
  state: HomeViewModel.UiState,
  onRefreshProfile: (String) -> Unit,
  onToggleAutoRefresh: (String, Boolean) -> Unit,
  onAddProfile: () -> Unit,
  onManageSync: () -> Unit,
  onBookmarks: () -> Unit,
  onFileExplorer: () -> Unit,
  onBluetoothControls: () -> Unit,
  onSwitchToSync: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val usbConnected = state.attachedDevice != null
  Column(modifier = modifier.fillMaxSize()) {
    CurateTopBar(
      usbConnected = usbConnected,
      btConnected = state.bluetooth.connectedDevice != null,
      onManageSync = onManageSync,
      onBookmarks = onBookmarks,
      onFileExplorer = onFileExplorer,
    )
    val attached = state.attachedDevice
    if (attached != null) {
      UsbAttachBanner(
        deviceLabel = attached.productName ?: "USB headphones",
        onSwitch = onSwitchToSync,
      )
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("Library") }
        if (state.profiles.isEmpty()) {
          item { EmptyLibraryHint(onManageSync = onManageSync) }
        } else {
          items(state.profiles, key = { it.id }) { profile ->
            val sourceNames =
              profile.source_ids.mapNotNull { id ->
                state.sources.firstOrNull { it.id == id }?.name
              }
            ProfileLibraryCard(
              profile = profile,
              sourceNames = sourceNames,
              onRefresh = { onRefreshProfile(profile.id) },
              onToggleAutoRefresh = { onToggleAutoRefresh(profile.id, it) },
            )
          }
        }
        item { Spacer(Modifier.height(72.dp)) }
      }
      FloatingActionButton(
        onClick = onAddProfile,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add profile")
      }
    }
    BluetoothPeekBar(bt = state.bluetooth, onOpenControls = onBluetoothControls)
  }
}

@Composable
private fun CurateTopBar(
  usbConnected: Boolean,
  btConnected: Boolean,
  onManageSync: () -> Unit,
  onBookmarks: () -> Unit,
  onFileExplorer: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Cadence",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.weight(1f))
      StatusPill(
        icon = if (usbConnected) Icons.Filled.Usb else Icons.Filled.Headset,
        label = if (usbConnected) "USB" else "No USB",
        emphasised = usbConnected,
      )
      Spacer(Modifier.width(8.dp))
      StatusPill(
        icon = Icons.Filled.Bluetooth,
        label = if (btConnected) "BT" else "BT off",
        emphasised = btConnected,
      )
      Spacer(Modifier.width(4.dp))
      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text("Manage sources & profiles") },
          leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
          onClick = {
            menuOpen = false
            onManageSync()
          },
        )
        DropdownMenuItem(
          text = { Text("Bookmarks") },
          leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
          onClick = {
            menuOpen = false
            onBookmarks()
          },
        )
        DropdownMenuItem(
          text = { Text("File explorer") },
          leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
          onClick = {
            menuOpen = false
            onFileExplorer()
          },
        )
      }
    }
  }
}

@Composable
private fun StatusPill(icon: ImageVector, label: String, emphasised: Boolean) {
  val container =
    if (emphasised) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val onContainer =
    if (emphasised) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(color = container, shape = RoundedCornerShape(50)) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(6.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer)
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
private fun EmptyLibraryHint(onManageSync: () -> Unit) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "No profiles yet",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "Add a source folder, then create a profile to combine sources " +
          "into a staging area ready to sync.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(onClick = onManageSync) { Text("Manage sources & profiles") }
    }
  }
}

@Composable
private fun ProfileLibraryCard(
  profile: SyncProfile,
  sourceNames: List<String>,
  onRefresh: () -> Unit,
  onToggleAutoRefresh: (Boolean) -> Unit,
) {
  val hasError = profile.last_error.isNotBlank()
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(
                if (hasError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (hasError) Icons.Filled.Warning else Icons.Filled.Folder,
            contentDescription = null,
            tint =
              if (hasError) MaterialTheme.colorScheme.onErrorContainer
              else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            profile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            buildString {
              append(profile.staging_subpath.ifBlank { "/" })
              append(" · ")
              append(profile.last_refreshed_at.ifBlank { "never refreshed" }.takeLast(20))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (sourceNames.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          sourceNames.take(3).forEach { name ->
            AssistChip(
              onClick = {},
              label = { Text(name, style = MaterialTheme.typography.labelSmall) },
              colors =
                AssistChipDefaults.assistChipColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            )
          }
        }
      }
      if (hasError) {
        Text(
          profile.last_error,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(onClick = onRefresh) {
          Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (hasError) "Retry" else "Refresh")
        }
        Spacer(Modifier.weight(1f))
        Switch(checked = profile.auto_refresh, onCheckedChange = onToggleAutoRefresh)
        Spacer(Modifier.width(4.dp))
        Text(
          "Auto",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun BluetoothPeekBar(bt: BluetoothState, onOpenControls: () -> Unit) {
  val connected = bt.connectedDevice
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth(),
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Bluetooth,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(12.dp))
      if (connected != null) {
        Column(Modifier.weight(1f)) {
          Text(
            connected.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            "${connected.batteryPercent ?: "?"}% · ${connected.codec ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Text(
          "No Bluetooth headphones connected",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
      }
      OutlinedButton(onClick = onOpenControls) { Text("Controls") }
    }
  }
}

@Composable
private fun UsbAttachBanner(deviceLabel: String, onSwitch: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Filled.Usb,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "$deviceLabel plugged in",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          "Switch to sync mode to copy your profiles.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Button(onClick = onSwitch) { Text("Switch") }
    }
  }
}

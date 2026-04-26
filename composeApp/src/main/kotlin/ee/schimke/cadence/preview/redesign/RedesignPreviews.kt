package ee.schimke.cadence.preview.redesign

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.preview.PreviewFixtures
import ee.schimke.cadence.sync.SyncProgress

/**
 * Preview-only redesign sketches. These composables are not wired to ViewModels
 * — they exist purely so the preview workflow can render before/after
 * thumbnails for design review. Production wiring follows once a direction is
 * agreed.
 *
 * Two modes:
 *  - Curate (no USB headphones): library-first home, profiles as heroes.
 *  - Sync (Shokz attached over USB): focused takeover with a single CTA.
 */

// ---------------------------------------------------------------------------
// Curate mode — ahead-of-time playlist folder management
// ---------------------------------------------------------------------------

@Composable
internal fun CurateHomeRedesign(
  profiles: List<SyncProfile>,
  sources: List<SyncSource>,
  bt: BluetoothState,
  showUsbBanner: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    CurateTopBar(usbConnected = false, btConnected = bt.connectedDevice != null)
    if (showUsbBanner) {
      UsbAttachBanner(deviceLabel = "Shokz OpenSwim Pro")
    }
    Column(
      modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(Modifier.height(8.dp))
      SectionLabel("Library")
      profiles.forEach { profile ->
        val sourceNames =
          profile.source_ids.mapNotNull { id -> sources.firstOrNull { it.id == id }?.name }
        ProfileLibraryCard(profile = profile, sourceNames = sourceNames)
      }
      Spacer(Modifier.height(8.dp))
      AddProfileFab()
    }
    BluetoothPeekBar(bt = bt)
  }
}

@Composable
private fun CurateTopBar(usbConnected: Boolean, btConnected: Boolean) {
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
    }
  }
}

@Composable
private fun StatusPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  emphasised: Boolean,
) {
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
private fun ProfileLibraryCard(profile: SyncProfile, sourceNames: List<String>) {
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
        OutlinedButton(onClick = {}) {
          Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (hasError) "Retry" else "Refresh")
        }
        Spacer(Modifier.weight(1f))
        Switch(checked = profile.auto_refresh, onCheckedChange = {})
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
private fun AddProfileFab() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    FloatingActionButton(
      onClick = {},
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      Icon(Icons.Filled.Add, contentDescription = "Add profile")
    }
  }
}

@Composable
private fun BluetoothPeekBar(bt: BluetoothState) {
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
        Column {
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
        )
      }
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = {}) { Text("Controls") }
    }
  }
}

@Composable
private fun UsbAttachBanner(deviceLabel: String) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
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
      Button(onClick = {}) { Text("Switch") }
    }
  }
}

// ---------------------------------------------------------------------------
// Sync mode — Shokz over USB, focused action
// ---------------------------------------------------------------------------

internal data class SyncReadyEntry(
  val profile: SyncProfile,
  val newCount: Int,
  val updateCount: Int,
  val removeCount: Int,
  val totalBytes: Long,
  val checked: Boolean,
)

@Composable
internal fun SyncModeRedesign(
  deviceLabel: String,
  freeSpaceLabel: String,
  entries: List<SyncReadyEntry>,
  progress: SyncProgress,
  done: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    SyncTopBar(done = done)
    Column(
      modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(Modifier.height(8.dp))
      SyncDeviceHero(
        label = deviceLabel,
        secondary =
          when {
            done -> "${progress.filesCompleted} files landed · safe to disconnect"
            progress.running -> "Syncing — don't unplug"
            else -> "USB connected · $freeSpaceLabel free"
          },
        running = progress.running,
        done = done,
      )
      when {
        done -> SyncDoneSummary(progress = progress)
        progress.running -> SyncProgressView(progress = progress)
        else -> {
          SectionLabel("Ready to sync")
          entries.forEach { SyncReadyRow(entry = it) }
        }
      }
    }
    if (!done) {
      SyncCta(progress = progress, totalFiles = entries.sumOf { it.newCount + it.updateCount })
    }
  }
}

@Composable
private fun SyncTopBar(done: Boolean) {
  Surface(
    color =
      if (done) MaterialTheme.colorScheme.tertiaryContainer
      else MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth(),
    tonalElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Close,
        contentDescription = "Exit sync mode",
        tint =
          if (done) MaterialTheme.colorScheme.onTertiaryContainer
          else MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Spacer(Modifier.width(12.dp))
      Text(
        if (done) "Sync complete" else "Sync mode",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color =
          if (done) MaterialTheme.colorScheme.onTertiaryContainer
          else MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
private fun SyncDeviceHero(label: String, secondary: String, running: Boolean, done: Boolean) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Text(
          label,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
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
private fun SyncReadyRow(entry: SyncReadyEntry) {
  val hasError = entry.profile.last_error.isNotBlank()
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(checked = entry.checked && !hasError, onCheckedChange = {}, enabled = !hasError)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(
          entry.profile.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        val summary = buildString {
          if (entry.newCount > 0) append("${entry.newCount} new")
          if (entry.updateCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("${entry.updateCount} updated")
          }
          if (entry.removeCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("${entry.removeCount} to remove")
          }
          if (entry.totalBytes > 0) {
            if (isNotEmpty()) append(" · ")
            append("${entry.totalBytes / (1024 * 1024)} MB")
          }
        }
        Text(
          if (hasError) "Last refresh failed — fix in Curate"
          else summary.ifBlank { "Up to date" },
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
private fun SyncCta(progress: SyncProgress, totalFiles: Int) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (progress.running) {
        OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Cancel") }
      } else {
        Button(onClick = {}, modifier = Modifier.weight(1f)) {
          Text(if (totalFiles == 0) "Already synced" else "Sync now ($totalFiles files)")
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// @Preview entry points — these are what the CI workflow renders
// ---------------------------------------------------------------------------

private val themedModifier
  @Composable
  get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

@Preview(name = "Redesign — Curate home", showBackground = true, heightDp = 1300)
@Composable
internal fun CurateHomePreview() {
  AndroidMaterialTheme {
    CurateHomeRedesign(
      profiles =
        listOf(
          PreviewFixtures.podcastsProfile,
          PreviewFixtures.swimProfile,
          PreviewFixtures.audiobooksProfile,
        ),
      sources = PreviewFixtures.sources,
      bt = PreviewFixtures.btConnectedPlaying,
      showUsbBanner = false,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Curate home, USB banner", showBackground = true, heightDp = 1400)
@Composable
internal fun CurateHomeUsbBannerPreview() {
  AndroidMaterialTheme {
    CurateHomeRedesign(
      profiles =
        listOf(
          PreviewFixtures.podcastsProfile,
          PreviewFixtures.swimProfile,
          PreviewFixtures.audiobooksProfile,
        ),
      sources = PreviewFixtures.sources,
      bt = PreviewFixtures.btConnectedPlaying,
      showUsbBanner = true,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Sync mode, ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyPreview() {
  AndroidMaterialTheme {
    SyncModeRedesign(
      deviceLabel = "OpenSwim Pro",
      freeSpaceLabel = "1.6 GB",
      entries =
        listOf(
          SyncReadyEntry(
            profile = PreviewFixtures.podcastsProfile,
            newCount = 7,
            updateCount = 0,
            removeCount = 0,
            totalBytes = 38L * 1024 * 1024,
            checked = true,
          ),
          SyncReadyEntry(
            profile = PreviewFixtures.swimProfile,
            newCount = 3,
            updateCount = 0,
            removeCount = 1,
            totalBytes = 14L * 1024 * 1024,
            checked = true,
          ),
          SyncReadyEntry(
            profile = PreviewFixtures.audiobooksProfile,
            newCount = 0,
            updateCount = 0,
            removeCount = 0,
            totalBytes = 0L,
            checked = false,
          ),
        ),
      progress = PreviewFixtures.syncIdle,
      done = false,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Sync mode, syncing", showBackground = true, heightDp = 1100)
@Composable
internal fun SyncModeSyncingPreview() {
  AndroidMaterialTheme {
    SyncModeRedesign(
      deviceLabel = "OpenSwim Pro",
      freeSpaceLabel = "1.6 GB",
      entries = emptyList(),
      progress = PreviewFixtures.syncRunning,
      done = false,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Sync mode, complete", showBackground = true, heightDp = 1000)
@Composable
internal fun SyncModeDonePreview() {
  AndroidMaterialTheme {
    SyncModeRedesign(
      deviceLabel = "OpenSwim Pro",
      freeSpaceLabel = "1.6 GB",
      entries = emptyList(),
      progress = PreviewFixtures.syncDone,
      done = true,
      modifier = themedModifier,
    )
  }
}

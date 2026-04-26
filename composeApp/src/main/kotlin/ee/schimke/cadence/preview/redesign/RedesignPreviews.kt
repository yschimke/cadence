package ee.schimke.cadence.preview.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.home.HomeContent
import ee.schimke.cadence.home.HomeViewModel
import ee.schimke.cadence.preview.PreviewFixtures
import ee.schimke.cadence.sync.FileSyncContent
import ee.schimke.cadence.sync.FileSyncViewModel

/**
 * Redesigned home (Curate mode) and file sync (Sync mode) screens, rendered against
 * the production composables so previews track the real UI rather than a parallel sketch.
 */
private val themedModifier
  @Composable
  get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

private val curateState =
  HomeViewModel.UiState(
    profiles =
      listOf(
        PreviewFixtures.podcastsProfile,
        PreviewFixtures.swimProfile,
        PreviewFixtures.audiobooksProfile,
      ),
    sources = PreviewFixtures.sources,
    preferences = SyncPreferences(),
    bluetooth = PreviewFixtures.btConnectedPlaying,
    attachedDevice = null,
  )

@Preview(name = "Redesign — Curate home", showBackground = true, heightDp = 1300)
@Composable
internal fun CurateHomePreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = curateState,
      onRefreshProfile = {},
      onToggleAutoRefresh = { _, _ -> },
      onAddProfile = {},
      onManageSync = {},
      onBookmarks = {},
      onFileExplorer = {},
      onBluetoothControls = {},
      onSwitchToSync = {},
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Curate home, USB banner", showBackground = true, heightDp = 1400)
@Composable
internal fun CurateHomeUsbBannerPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = curateState.copy(attachedDevice = PreviewFixtures.usbDevices.first()),
      onRefreshProfile = {},
      onToggleAutoRefresh = { _, _ -> },
      onAddProfile = {},
      onManageSync = {},
      onBookmarks = {},
      onFileExplorer = {},
      onBluetoothControls = {},
      onSwitchToSync = {},
      modifier = themedModifier,
    )
  }
}

private fun syncReady(): FileSyncViewModel.UiState =
  FileSyncViewModel.UiState(
    sources = PreviewFixtures.sources,
    profiles =
      listOf(
        PreviewFixtures.podcastsProfile,
        PreviewFixtures.swimProfile,
        PreviewFixtures.audiobooksProfile,
      ),
    devices =
      listOf(
        Device(id = "dev-1", name = "OpenSwim Pro", path = ""),
      ),
    preferences = SyncPreferences(target_device_id = "dev-1"),
    refresh = PreviewFixtures.refreshIdle,
    sync = PreviewFixtures.syncIdle,
  )

@Preview(name = "Redesign — Sync mode, ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyPreview() {
  AndroidMaterialTheme {
    FileSyncContent(
      state = syncReady(),
      onStartSync = {},
      onCancelSync = {},
      onClose = {},
      onManage = {},
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Sync mode, syncing", showBackground = true, heightDp = 1100)
@Composable
internal fun SyncModeSyncingPreview() {
  AndroidMaterialTheme {
    FileSyncContent(
      state = syncReady().copy(sync = PreviewFixtures.syncRunning),
      onStartSync = {},
      onCancelSync = {},
      onClose = {},
      onManage = {},
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Redesign — Sync mode, complete", showBackground = true, heightDp = 1000)
@Composable
internal fun SyncModeDonePreview() {
  AndroidMaterialTheme {
    FileSyncContent(
      state = syncReady().copy(sync = PreviewFixtures.syncDone),
      onStartSync = {},
      onCancelSync = {},
      onClose = {},
      onManage = {},
      modifier = themedModifier,
    )
  }
}

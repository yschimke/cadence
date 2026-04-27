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
import ee.schimke.cadence.theme.CadenceTheme

/**
 * Redesigned home (Curate mode) and file sync (Sync mode) screens, rendered
 * against the production composables so previews track the real UI rather
 * than a parallel sketch.
 *
 * Each scene is rendered twice — once under [AndroidMaterialTheme] (system
 * dynamic colours + Roboto Flex) and once under [CadenceTheme] (Coastal
 * Blue palette + Manrope/Inter) — so the design-system difference is
 * visible at a glance.
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

@Composable
private fun CurateScene(state: HomeViewModel.UiState) {
  HomeContent(
    state = state,
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

private fun syncReady(): FileSyncViewModel.UiState =
  FileSyncViewModel.UiState(
    sources = PreviewFixtures.sources,
    profiles =
      listOf(
        PreviewFixtures.podcastsProfile,
        PreviewFixtures.swimProfile,
        PreviewFixtures.audiobooksProfile,
      ),
    devices = listOf(Device(id = "dev-1", name = "Headphones (sample)", path = "")),
    preferences = SyncPreferences(target_device_id = "dev-1"),
    refresh = PreviewFixtures.refreshIdle,
    sync = PreviewFixtures.syncIdle,
  )

@Composable
private fun SyncScene(state: FileSyncViewModel.UiState) {
  FileSyncContent(
    state = state,
    onStartSync = {},
    onCancelSync = {},
    onClose = {},
    onManage = {},
    modifier = themedModifier,
  )
}

// ---- Curate ----

@Preview(name = "System — Curate home", showBackground = true, heightDp = 1300)
@Composable
internal fun CurateHomePreview() {
  AndroidMaterialTheme { CurateScene(curateState) }
}

@Preview(name = "Cadence — Curate home", showBackground = true, heightDp = 1300)
@Composable
internal fun CurateHomeCadencePreview() {
  CadenceTheme { CurateScene(curateState) }
}

@Preview(name = "Cadence dark — Curate home", showBackground = true, heightDp = 1300)
@Composable
internal fun CurateHomeCadenceDarkPreview() {
  CadenceTheme(darkTheme = true) { CurateScene(curateState) }
}

@Preview(name = "System — Curate USB banner", showBackground = true, heightDp = 1400)
@Composable
internal fun CurateHomeUsbBannerPreview() {
  AndroidMaterialTheme {
    CurateScene(curateState.copy(attachedDevice = PreviewFixtures.usbDevices.first()))
  }
}

@Preview(name = "Cadence — Curate USB banner", showBackground = true, heightDp = 1400)
@Composable
internal fun CurateHomeUsbBannerCadencePreview() {
  CadenceTheme {
    CurateScene(curateState.copy(attachedDevice = PreviewFixtures.usbDevices.first()))
  }
}

// ---- Sync ----

@Preview(name = "System — Sync ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyPreview() {
  AndroidMaterialTheme { SyncScene(syncReady()) }
}

@Preview(name = "Cadence — Sync ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyCadencePreview() {
  CadenceTheme { SyncScene(syncReady()) }
}

@Preview(name = "Cadence dark — Sync ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyCadenceDarkPreview() {
  CadenceTheme(darkTheme = true) { SyncScene(syncReady()) }
}

@Preview(name = "System — Sync syncing", showBackground = true, heightDp = 1100)
@Composable
internal fun SyncModeSyncingPreview() {
  AndroidMaterialTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncRunning)) }
}

@Preview(name = "Cadence — Sync syncing", showBackground = true, heightDp = 1100)
@Composable
internal fun SyncModeSyncingCadencePreview() {
  CadenceTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncRunning)) }
}

@Preview(name = "System — Sync complete", showBackground = true, heightDp = 1000)
@Composable
internal fun SyncModeDonePreview() {
  AndroidMaterialTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncDone)) }
}

@Preview(name = "Cadence — Sync complete", showBackground = true, heightDp = 1000)
@Composable
internal fun SyncModeDoneCadencePreview() {
  CadenceTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncDone)) }
}

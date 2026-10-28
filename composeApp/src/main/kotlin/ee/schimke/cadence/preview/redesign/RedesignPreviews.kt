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
import ee.schimke.cadence.preview.PreviewFixtures
import ee.schimke.cadence.sync.FileSyncContent
import ee.schimke.cadence.sync.FileSyncViewModel

/**
 * The copy-to-headphones screen in each state, rendered against the production composable. The
 * library home has its own previews in `ScreenPreviews`.
 */
private val themedModifier
  @Composable get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

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

@Preview(name = "Sync ready", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyPreview() {
  AndroidMaterialTheme { SyncScene(syncReady()) }
}

@Preview(name = "Sync ready (dark)", showBackground = true, heightDp = 1200)
@Composable
internal fun SyncModeReadyDarkPreview() {
  AndroidMaterialTheme(darkTheme = true) { SyncScene(syncReady()) }
}

@Preview(name = "Sync syncing", showBackground = true, heightDp = 1100)
@Composable
internal fun SyncModeSyncingPreview() {
  AndroidMaterialTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncRunning)) }
}

@Preview(name = "Sync complete", showBackground = true, heightDp = 1000)
@Composable
internal fun SyncModeDonePreview() {
  AndroidMaterialTheme { SyncScene(syncReady().copy(sync = PreviewFixtures.syncDone)) }
}

package ee.schimke.cadence.preview.playstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.bluetooth.BluetoothControlsContent
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.home.HomeContent
import ee.schimke.cadence.home.HomeViewModel
import ee.schimke.cadence.preview.PreviewFixtures
import ee.schimke.cadence.sync.FileSyncContent
import ee.schimke.cadence.sync.FileSyncViewModel
import ee.schimke.cadence.sync.ManageSyncContent
import ee.schimke.cadence.theme.CadenceTheme

// Device specs sized to Play Store screenshot rules (aspect ratio between
// 16:9 and 9:16, 320–3840 px on either side). Pixel 8a's native 1080×2400
// (9:20) is outside the cap, so the phone spec uses a 9:16 viewport at the
// 8a's 420 dpi.
private const val PHONE_PIXEL_8A = "spec:width=411dp,height=731dp,dpi=420"
private const val TABLET_7IN = "spec:width=600dp,height=960dp,dpi=320"
private const val TABLET_10IN = "spec:width=800dp,height=1280dp,dpi=320"

private val themedModifier
  @Composable
  get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

private val homeState =
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
private fun HomeScene() {
  HomeContent(
    state = homeState,
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

private fun syncReadyState(): FileSyncViewModel.UiState =
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

// ---- Phone (Pixel 8a) -----------------------------------------------------

@Preview(name = "PlayStore phone — Home light", device = PHONE_PIXEL_8A, showBackground = true)
@Composable
internal fun PlayStorePhoneHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

@Preview(name = "PlayStore phone — Home dark", device = PHONE_PIXEL_8A, showBackground = true)
@Composable
internal fun PlayStorePhoneHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

@Preview(name = "PlayStore phone — Sync", device = PHONE_PIXEL_8A, showBackground = true)
@Composable
internal fun PlayStorePhoneSync() {
  CadenceTheme(darkTheme = false) {
    FileSyncContent(
      state = syncReadyState(),
      onStartSync = {},
      onCancelSync = {},
      onClose = {},
      onManage = {},
      modifier = themedModifier,
    )
  }
}

@Preview(name = "PlayStore phone — Bluetooth", device = PHONE_PIXEL_8A, showBackground = true)
@Composable
internal fun PlayStorePhoneBluetooth() {
  CadenceTheme(darkTheme = false) {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btConnectedPlaying,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlayPause = {},
      onPrevious = {},
      onNext = {},
      onStop = {},
      onFastForward = {},
      onRewind = {},
      onOpenSettings = {},
      onRequestMediaAccess = {},
      onSelectWorkingMode = {},
      onAdvanced = {},
    )
  }
}

@Preview(name = "PlayStore phone — Manage", device = PHONE_PIXEL_8A, showBackground = true)
@Composable
internal fun PlayStorePhoneManage() {
  CadenceTheme(darkTheme = false) {
    ManageSyncContent(
      modifier = themedModifier,
      state = PreviewFixtures.fileSyncPopulated(),
      onAddLocalDirectory = {},
      onAddNfsShare = {},
      onDiscoverApps = {},
      onRemoveSource = {},
      onAddProfile = {},
      onDeleteProfile = {},
      onRefreshProfile = {},
      onToggleAutoRefresh = { _, _ -> },
      onSelectTargetDevice = {},
      onSetAutoSync = {},
      onSetUsbMatch = {},
    )
  }
}

// ---- 7" tablet ------------------------------------------------------------

@Preview(name = "PlayStore 7\" — Home light", device = TABLET_7IN, showBackground = true)
@Composable
internal fun PlayStoreSevenInchHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

@Preview(name = "PlayStore 7\" — Home dark", device = TABLET_7IN, showBackground = true)
@Composable
internal fun PlayStoreSevenInchHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

// ---- 10" tablet -----------------------------------------------------------

@Preview(name = "PlayStore 10\" — Home light", device = TABLET_10IN, showBackground = true)
@Composable
internal fun PlayStoreTenInchHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

@Preview(name = "PlayStore 10\" — Home dark", device = TABLET_10IN, showBackground = true)
@Composable
internal fun PlayStoreTenInchHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

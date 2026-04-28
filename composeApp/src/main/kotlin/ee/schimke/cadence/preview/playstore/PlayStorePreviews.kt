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

/**
 * Canonical Play Store screenshot sources.
 *
 * Every `@Preview` in this file renders to a PNG that ships in the Play
 * Store listing under
 * `composeApp/src/main/play/listings/en-GB/graphics/`. Other previews in
 * sibling packages (`preview/`, `preview/redesign/`) are for design and
 * IDE iteration only and **must not** be reused for the listing.
 *
 * Each preview is tagged with `group = "Play Store"` so they cluster in
 * the IDE preview pane and can be filtered on the command line via
 * `compose-preview render --filter PlayStore`.
 *
 * Device specs are sized to Play Store screenshot rules (aspect ratio
 * between 16:9 and 9:16, 320–3840 px on either side). Pixel 8a's native
 * 1080×2400 (9:20) is outside the cap, so the phone spec uses a 9:16
 * viewport at the 8a's 420 dpi.
 *
 * Re-render workflow:
 * ```
 * ANDROID_HOME=/path/to/sdk compose-preview render --filter PlayStore
 * ```
 *
 * Then copy the PNGs from `composeApp/build/compose-previews/renders/` to
 * the destinations below. Filenames in each directory determine the order
 * Play Store displays them.
 *
 * Listing destinations (managed by Gradle Play Publisher):
 *
 * | Preview function                | Listing path                                                         |
 * |---------------------------------|----------------------------------------------------------------------|
 * | [PlayStorePhoneHomeLight]       | `phone-screenshots/01-home-light.png`                                |
 * | [PlayStorePhoneHomeDark]        | `phone-screenshots/02-home-dark.png`                                 |
 * | [PlayStorePhoneSync]            | `phone-screenshots/03-sync.png`                                      |
 * | [PlayStorePhoneBluetooth]       | `phone-screenshots/04-bluetooth.png`                                 |
 * | [PlayStorePhoneManage]          | `phone-screenshots/05-manage.png`                                    |
 * | [PlayStoreSevenInchHomeLight]   | `seven-inch-screenshots/01-home-light.png`                           |
 * | [PlayStoreSevenInchHomeDark]    | `seven-inch-screenshots/02-home-dark.png`                            |
 * | [PlayStoreTenInchHomeLight]     | `ten-inch-screenshots/01-home-light.png`                             |
 * | [PlayStoreTenInchHomeDark]      | `ten-inch-screenshots/02-home-dark.png`                              |
 */

/** Tag attached to every Play Store preview so tooling can filter by group. */
private const val PLAY_STORE_GROUP = "Play Store"

/** Pixel 8a viewport at the 8a's 420 dpi, capped to 9:16 — renders 1078×1918 px. */
private const val PHONE_PIXEL_8A = "spec:width=411dp,height=731dp,dpi=420"

/** 7" tablet portrait — renders 1200×1920 px. */
private const val TABLET_7IN = "spec:width=600dp,height=960dp,dpi=320"

/** 10" tablet portrait — renders 1600×2560 px. */
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
// Listing directory: composeApp/src/main/play/listings/en-GB/graphics/phone-screenshots/

/** Play Store phone screenshot 1/5 → `phone-screenshots/01-home-light.png`. */
@Preview(
  name = "Play Store · phone · Home (light)",
  group = PLAY_STORE_GROUP,
  device = PHONE_PIXEL_8A,
  showBackground = true,
)
@Composable
internal fun PlayStorePhoneHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

/** Play Store phone screenshot 2/5 → `phone-screenshots/02-home-dark.png`. */
@Preview(
  name = "Play Store · phone · Home (dark)",
  group = PLAY_STORE_GROUP,
  device = PHONE_PIXEL_8A,
  showBackground = true,
)
@Composable
internal fun PlayStorePhoneHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

/** Play Store phone screenshot 3/5 → `phone-screenshots/03-sync.png`. */
@Preview(
  name = "Play Store · phone · Sync",
  group = PLAY_STORE_GROUP,
  device = PHONE_PIXEL_8A,
  showBackground = true,
)
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

/** Play Store phone screenshot 4/5 → `phone-screenshots/04-bluetooth.png`. */
@Preview(
  name = "Play Store · phone · Bluetooth",
  group = PLAY_STORE_GROUP,
  device = PHONE_PIXEL_8A,
  showBackground = true,
)
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

/** Play Store phone screenshot 5/5 → `phone-screenshots/05-manage.png`. */
@Preview(
  name = "Play Store · phone · Manage",
  group = PLAY_STORE_GROUP,
  device = PHONE_PIXEL_8A,
  showBackground = true,
)
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
// Listing directory: composeApp/src/main/play/listings/en-GB/graphics/seven-inch-screenshots/

/** Play Store 7" tablet screenshot 1/2 → `seven-inch-screenshots/01-home-light.png`. */
@Preview(
  name = "Play Store · 7\" tablet · Home (light)",
  group = PLAY_STORE_GROUP,
  device = TABLET_7IN,
  showBackground = true,
)
@Composable
internal fun PlayStoreSevenInchHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

/** Play Store 7" tablet screenshot 2/2 → `seven-inch-screenshots/02-home-dark.png`. */
@Preview(
  name = "Play Store · 7\" tablet · Home (dark)",
  group = PLAY_STORE_GROUP,
  device = TABLET_7IN,
  showBackground = true,
)
@Composable
internal fun PlayStoreSevenInchHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

// ---- 10" tablet -----------------------------------------------------------
// Listing directory: composeApp/src/main/play/listings/en-GB/graphics/ten-inch-screenshots/

/** Play Store 10" tablet screenshot 1/2 → `ten-inch-screenshots/01-home-light.png`. */
@Preview(
  name = "Play Store · 10\" tablet · Home (light)",
  group = PLAY_STORE_GROUP,
  device = TABLET_10IN,
  showBackground = true,
)
@Composable
internal fun PlayStoreTenInchHomeLight() {
  CadenceTheme(darkTheme = false) { HomeScene() }
}

/** Play Store 10" tablet screenshot 2/2 → `ten-inch-screenshots/02-home-dark.png`. */
@Preview(
  name = "Play Store · 10\" tablet · Home (dark)",
  group = PLAY_STORE_GROUP,
  device = TABLET_10IN,
  showBackground = true,
)
@Composable
internal fun PlayStoreTenInchHomeDark() {
  CadenceTheme(darkTheme = true) { HomeScene() }
}

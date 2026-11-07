package ee.schimke.cadence.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.DeviceFiles
import ee.schimke.cadence.bluetooth.BluetoothControlsContent
import ee.schimke.cadence.developer.DeveloperMode
import ee.schimke.cadence.developer.LocalDeveloperMode
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.devices.DevicesViewModel
import ee.schimke.cadence.files.DeviceFilesContent
import ee.schimke.cadence.files.DeviceFilesViewModel
import ee.schimke.cadence.home.HomeActions
import ee.schimke.cadence.home.HomeContent
import ee.schimke.cadence.home.HomeViewModel
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.PodcastPlayerUi
import ee.schimke.cadence.podcasts.PodcastWorkUi
import ee.schimke.cadence.sync.ManageSyncContent

private val themedModifier
  @Composable get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

@Preview(name = "Home — empty", showBackground = true)
@Composable
internal fun HomeEmptyPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = HomeViewModel.UiState(),
      podcasts = LocalPodcastState(),
      actions = HomeActions(),
      suggestions = PreviewFixtures.suggestions,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Home — library", showBackground = true, heightDp = 1400)
@Composable
internal fun HomeLibraryPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = PreviewFixtures.libraryState,
      podcasts = PreviewFixtures.podcastState,
      actions = HomeActions(),
      canRefreshPodcasts = true,
      podcastWork = PodcastWorkUi(supportsDownload = true),
      podcastPlayer = PodcastPlayerUi(suggestMediaAccess = true),
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Home — headphones connected", showBackground = true, heightDp = 900)
@Composable
internal fun HomeHeadphonesPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state =
        PreviewFixtures.libraryState.copy(attachedDevice = PreviewFixtures.usbDevices.first()),
      podcasts = PreviewFixtures.podcastState,
      actions = HomeActions(),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — ready to copy",
  showBackground = true,
  device = "spec:width=411dp,height=900dp,dpi=420",
)
@Composable
internal fun HomeReadyToCopyPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state =
        PreviewFixtures.libraryState.copy(attachedDevice = PreviewFixtures.usbDevices.first()),
      podcasts = PreviewFixtures.preparedPodcastState,
      actions = HomeActions(),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — up to date",
  showBackground = true,
  device = "spec:width=411dp,height=900dp,dpi=420",
)
@Composable
internal fun HomeUpToDatePreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = PreviewFixtures.libraryState,
      podcasts =
        PreviewFixtures.preparedPodcastState.let { it.copy(copiedSectionIds = it.readySectionIds) },
      actions = HomeActions(),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — headphones and controls",
  showBackground = true,
  device = "spec:width=411dp,height=1200dp,dpi=420",
)
@Composable
internal fun HomeConnectedControlsPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state =
        PreviewFixtures.libraryState.copy(
          attachedDevice = PreviewFixtures.usbDevices.first(),
          bluetooth = PreviewFixtures.btConnectedPlaying,
        ),
      podcasts = PreviewFixtures.preparedPodcastState,
      actions = HomeActions(),
      podcastWork = PodcastWorkUi(supportsDownload = true),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — downloading",
  showBackground = true,
  device = "spec:width=411dp,height=1200dp,dpi=420",
)
@Composable
internal fun HomeDownloadingPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = PreviewFixtures.libraryState,
      podcasts = PreviewFixtures.podcastState,
      actions = HomeActions(),
      canRefreshPodcasts = true,
      podcastWork =
        PodcastWorkUi(
          supportsDownload = true,
          active = true,
          status = "Downloading 2 of 5: Tides of the Pacific · 42%",
          fraction = 0.28f,
        ),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — download failed",
  showBackground = true,
  device = "spec:width=411dp,height=1200dp,dpi=420",
)
@Composable
internal fun HomeDownloadFailedPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = PreviewFixtures.libraryState,
      podcasts = PreviewFixtures.podcastState,
      actions = HomeActions(),
      canRefreshPodcasts = true,
      podcastWork =
        PodcastWorkUi(
          supportsDownload = true,
          error =
            "Downloaded 3 of 5. Couldn't download “Tides of the Pacific” (and 1 more): the " +
              "server stopped responding. Try again to retry the rest.",
          failedTask = "download",
        ),
      modifier = themedModifier,
    )
  }
}

@Preview(
  name = "Home — new releases",
  showBackground = true,
  device = "spec:width=411dp,height=1600dp,dpi=420",
)
@Composable
internal fun HomeFreshPreview() {
  AndroidMaterialTheme {
    HomeContent(
      state = PreviewFixtures.libraryState,
      podcasts = PreviewFixtures.freshPodcastState,
      actions = HomeActions(),
      canArchive = true,
      canAddFresh = true,
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Devices - loaded", showBackground = true)
@Composable
internal fun DevicesScreenLoadedPreview() {
  AndroidMaterialTheme {
    DevicesScreen(
      modifier = themedModifier,
      uiState =
        DevicesViewModel.UiState.Devices(
          devices = PreviewFixtures.devices,
          usbDevices = PreviewFixtures.usbDevices,
        ),
      permissionCheck = {},
      onDeviceClick = {},
      podcastTarget = PreviewFixtures.devices.first().path,
    )
  }
}

@Preview(name = "Devices - loading", showBackground = true)
@Composable
internal fun DevicesScreenLoadingPreview() {
  AndroidMaterialTheme {
    DevicesScreen(
      modifier = themedModifier,
      uiState = DevicesViewModel.UiState.Loading,
      permissionCheck = {},
      onDeviceClick = {},
    )
  }
}

@Preview(name = "Devices - empty", showBackground = true)
@Composable
internal fun DevicesScreenEmptyPreview() {
  AndroidMaterialTheme {
    DevicesScreen(
      modifier = themedModifier,
      uiState = DevicesViewModel.UiState.Devices(emptyList(), emptyList()),
      permissionCheck = {},
      onDeviceClick = {},
    )
  }
}

@Preview(name = "Device files - loading", showBackground = true)
@Composable
internal fun DeviceFilesLoadingPreview() {
  AndroidMaterialTheme {
    DeviceFilesContent(
      uiState = DeviceFilesViewModel.UiState.Loading(DeviceFiles(id = "dev-1")),
      modifier = themedModifier,
    )
  }
}

@Preview(name = "Device files - not available", showBackground = true)
@Composable
internal fun DeviceFilesNotAvailablePreview() {
  AndroidMaterialTheme {
    DeviceFilesContent(
      uiState = DeviceFilesViewModel.UiState.NotAvailable(DeviceFiles(id = "dev-1")),
      modifier = themedModifier,
    )
  }
}

// ----------------------------------------------------------------------
// Bluetooth controls

@Preview(name = "BT controls - disconnected", showBackground = true, heightDp = 1300)
@Composable
internal fun BluetoothDisconnectedPreview() {
  AndroidMaterialTheme {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btDisconnected,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlay = {},
      onPause = {},
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

@Preview(name = "BT controls - connected + playing", showBackground = true, heightDp = 1500)
@Composable
internal fun BluetoothConnectedPlayingPreview() {
  AndroidMaterialTheme {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btConnectedPlaying,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlay = {},
      onPause = {},
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

@Preview(name = "BT controls - permission missing", showBackground = true, heightDp = 1500)
@Composable
internal fun BluetoothPermissionMissingPreview() {
  AndroidMaterialTheme {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btPermissionMissing,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlay = {},
      onPause = {},
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

@Preview(name = "BT controls - MP3 mode", showBackground = true, heightDp = 1500)
@Composable
internal fun BluetoothMp3ModePreview() {
  AndroidMaterialTheme {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btMp3Mode,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlay = {},
      onPause = {},
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

@Preview(name = "BT controls - developer", showBackground = true, heightDp = 1700)
@Composable
internal fun BluetoothDeveloperPreview() {
  AndroidMaterialTheme {
    BluetoothControlsContent(
      modifier = themedModifier,
      state = PreviewFixtures.btConnectedPlaying,
      snackbarHost = remember { SnackbarHostState() },
      onRefresh = {},
      onSetVolume = {},
      onAdjustVolume = {},
      onToggleMute = {},
      onPlay = {},
      onPause = {},
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
      developerMode = true,
    )
  }
}

// ----------------------------------------------------------------------
// Manage sources & profiles (the deep configuration screen)

private val manageCallbacks = ManageCallbacks()

private class ManageCallbacks {
  val onAddLocalDirectory: () -> Unit = {}
  val onAddNfsShare: () -> Unit = {}
  val onDiscoverApps: () -> Unit = {}
  val onRemoveSource: (String) -> Unit = {}
  val onAddProfile: () -> Unit = {}
  val onDeleteProfile: (String) -> Unit = {}
  val onRefreshProfile: (String) -> Unit = {}
  val onToggleAutoRefresh: (ee.schimke.cadence.datastore.proto.SyncProfile, Boolean) -> Unit =
    { _, _ ->
    }
  val onSelectTargetDevice: (String) -> Unit = {}
  val onSetAutoSync: (Boolean) -> Unit = {}
  val onSetUsbMatch: (String) -> Unit = {}
}

@Preview(name = "Manage - empty", showBackground = true, heightDp = 1500)
@Composable
internal fun ManageEmptyPreview() {
  AndroidMaterialTheme {
    ManageSyncContent(
      modifier = themedModifier,
      state = PreviewFixtures.fileSyncEmpty(),
      onAddLocalDirectory = manageCallbacks.onAddLocalDirectory,
      onAddNfsShare = manageCallbacks.onAddNfsShare,
      onDiscoverApps = manageCallbacks.onDiscoverApps,
      onRemoveSource = manageCallbacks.onRemoveSource,
      onAddProfile = manageCallbacks.onAddProfile,
      onDeleteProfile = manageCallbacks.onDeleteProfile,
      onRefreshProfile = manageCallbacks.onRefreshProfile,
      onToggleAutoRefresh = manageCallbacks.onToggleAutoRefresh,
      onSelectTargetDevice = manageCallbacks.onSelectTargetDevice,
      onSetAutoSync = manageCallbacks.onSetAutoSync,
      onSetUsbMatch = manageCallbacks.onSetUsbMatch,
    )
  }
}

@Preview(name = "Manage - populated", showBackground = true, heightDp = 1900)
@Composable
internal fun ManagePopulatedPreview() {
  AndroidMaterialTheme {
    ManageSyncContent(
      modifier = themedModifier,
      state = PreviewFixtures.fileSyncPopulated(),
      onAddLocalDirectory = manageCallbacks.onAddLocalDirectory,
      onAddNfsShare = manageCallbacks.onAddNfsShare,
      onDiscoverApps = manageCallbacks.onDiscoverApps,
      onRemoveSource = manageCallbacks.onRemoveSource,
      onAddProfile = manageCallbacks.onAddProfile,
      onDeleteProfile = manageCallbacks.onDeleteProfile,
      onRefreshProfile = manageCallbacks.onRefreshProfile,
      onToggleAutoRefresh = manageCallbacks.onToggleAutoRefresh,
      onSelectTargetDevice = manageCallbacks.onSelectTargetDevice,
      onSetAutoSync = manageCallbacks.onSetAutoSync,
      onSetUsbMatch = manageCallbacks.onSetUsbMatch,
    )
  }
}

@Preview(name = "Manage - developer, refreshing", showBackground = true, heightDp = 1900)
@Composable
internal fun ManageRefreshingPreview() {
  AndroidMaterialTheme {
    // Profiles and their refresh progress are developer options.
    CompositionLocalProvider(LocalDeveloperMode provides DeveloperMode(enabled = true) {}) {
      ManageSyncContent(
        modifier = themedModifier,
        state = PreviewFixtures.fileSyncRefreshing(),
        onAddLocalDirectory = manageCallbacks.onAddLocalDirectory,
        onAddNfsShare = manageCallbacks.onAddNfsShare,
        onDiscoverApps = manageCallbacks.onDiscoverApps,
        onRemoveSource = manageCallbacks.onRemoveSource,
        onAddProfile = manageCallbacks.onAddProfile,
        onDeleteProfile = manageCallbacks.onDeleteProfile,
        onRefreshProfile = manageCallbacks.onRefreshProfile,
        onToggleAutoRefresh = manageCallbacks.onToggleAutoRefresh,
        onSelectTargetDevice = manageCallbacks.onSelectTargetDevice,
        onSetAutoSync = manageCallbacks.onSetAutoSync,
        onSetUsbMatch = manageCallbacks.onSetUsbMatch,
      )
    }
  }
}

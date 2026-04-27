package ee.schimke.cadence.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.DeviceFiles
import ee.schimke.cadence.bluetooth.BluetoothControlsContent
import ee.schimke.cadence.bookmarks.BookmarksScreen
import ee.schimke.cadence.bookmarks.BookmarksViewModel
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.devices.DevicesViewModel
import ee.schimke.cadence.files.DeviceFilesContent
import ee.schimke.cadence.files.DeviceFilesViewModel
import ee.schimke.cadence.home.HomeContent
import ee.schimke.cadence.home.HomeViewModel
import ee.schimke.cadence.sync.ManageSyncContent

private val themedModifier
    @Composable get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

@Preview(name = "Home — empty", showBackground = true)
@Composable
internal fun HomeEmptyPreview() {
    AndroidMaterialTheme {
        HomeContent(
            state = HomeViewModel.UiState(),
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

@Preview(name = "Devices - loaded", showBackground = true)
@Composable
internal fun DevicesScreenLoadedPreview() {
    AndroidMaterialTheme {
        DevicesScreen(
            modifier = themedModifier,
            uiState = DevicesViewModel.UiState.Devices(
                devices = PreviewFixtures.devices,
                usbDevices = PreviewFixtures.usbDevices,
            ),
            permissionCheck = {},
            onDeviceClick = {},
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

@Preview(name = "Bookmarks - loaded", showBackground = true)
@Composable
internal fun BookmarksScreenLoadedPreview() {
    AndroidMaterialTheme {
        BookmarksScreen(
            modifier = themedModifier,
            uiState = BookmarksViewModel.Loaded(PreviewFixtures.bookmarks),
            onNavigateTo = {},
        )
    }
}

@Preview(name = "Bookmarks - loading", showBackground = true)
@Composable
internal fun BookmarksScreenLoadingPreview() {
    AndroidMaterialTheme {
        BookmarksScreen(
            modifier = themedModifier,
            uiState = BookmarksViewModel.Loading,
            onNavigateTo = {},
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
    val onToggleAutoRefresh:
        (ee.schimke.cadence.datastore.proto.SyncProfile, Boolean) -> Unit = { _, _ -> }
    val onSelectTargetDevice: (String) -> Unit = {}
    val onSetAutoSync: (Boolean) -> Unit = {}
    val onSetUsbMatch: (String) -> Unit = {}
    val onSetThemeMode: (ee.schimke.cadence.datastore.proto.ThemeMode) -> Unit = {}
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

@Preview(name = "Manage - refreshing", showBackground = true, heightDp = 1900)
@Composable
internal fun ManageRefreshingPreview() {
    AndroidMaterialTheme {
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

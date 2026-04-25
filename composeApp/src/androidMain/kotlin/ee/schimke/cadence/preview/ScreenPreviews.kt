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
import ee.schimke.cadence.sync.FileSyncContent

private val themedModifier
    @Composable get() = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

@Preview(name = "Home", showBackground = true)
@Composable
internal fun HomeScreenPreview() {
    AndroidMaterialTheme {
        HomeContent(
            onFileExplorer = {},
            onBookmarks = {},
            onBluetoothControls = {},
            onFileSync = {},
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

@Preview(name = "BT controls - disconnected", showBackground = true, heightDp = 1100)
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
            onAdvanced = {},
        )
    }
}

@Preview(name = "BT controls - connected + playing", showBackground = true, heightDp = 1300)
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
            onAdvanced = {},
        )
    }
}

@Preview(name = "BT controls - permission missing", showBackground = true, heightDp = 1300)
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
            onAdvanced = {},
        )
    }
}

// ----------------------------------------------------------------------
// File Sync

private val syncCallbacks = SyncCallbacks()

private class SyncCallbacks {
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
    val onStartSync: () -> Unit = {}
    val onCancelSync: () -> Unit = {}
}

@Preview(name = "File sync - empty", showBackground = true, heightDp = 1500)
@Composable
internal fun FileSyncEmptyPreview() {
    AndroidMaterialTheme {
        FileSyncContent(
            modifier = themedModifier,
            state = PreviewFixtures.fileSyncEmpty(),
            onAddLocalDirectory = syncCallbacks.onAddLocalDirectory,
            onAddNfsShare = syncCallbacks.onAddNfsShare,
            onDiscoverApps = syncCallbacks.onDiscoverApps,
            onRemoveSource = syncCallbacks.onRemoveSource,
            onAddProfile = syncCallbacks.onAddProfile,
            onDeleteProfile = syncCallbacks.onDeleteProfile,
            onRefreshProfile = syncCallbacks.onRefreshProfile,
            onToggleAutoRefresh = syncCallbacks.onToggleAutoRefresh,
            onSelectTargetDevice = syncCallbacks.onSelectTargetDevice,
            onSetAutoSync = syncCallbacks.onSetAutoSync,
            onSetUsbMatch = syncCallbacks.onSetUsbMatch,
            onStartSync = syncCallbacks.onStartSync,
            onCancelSync = syncCallbacks.onCancelSync,
        )
    }
}

@Preview(name = "File sync - populated", showBackground = true, heightDp = 1900)
@Composable
internal fun FileSyncPopulatedPreview() {
    AndroidMaterialTheme {
        FileSyncContent(
            modifier = themedModifier,
            state = PreviewFixtures.fileSyncPopulated(),
            onAddLocalDirectory = syncCallbacks.onAddLocalDirectory,
            onAddNfsShare = syncCallbacks.onAddNfsShare,
            onDiscoverApps = syncCallbacks.onDiscoverApps,
            onRemoveSource = syncCallbacks.onRemoveSource,
            onAddProfile = syncCallbacks.onAddProfile,
            onDeleteProfile = syncCallbacks.onDeleteProfile,
            onRefreshProfile = syncCallbacks.onRefreshProfile,
            onToggleAutoRefresh = syncCallbacks.onToggleAutoRefresh,
            onSelectTargetDevice = syncCallbacks.onSelectTargetDevice,
            onSetAutoSync = syncCallbacks.onSetAutoSync,
            onSetUsbMatch = syncCallbacks.onSetUsbMatch,
            onStartSync = syncCallbacks.onStartSync,
            onCancelSync = syncCallbacks.onCancelSync,
        )
    }
}

@Preview(name = "File sync - refreshing", showBackground = true, heightDp = 1900)
@Composable
internal fun FileSyncRefreshingPreview() {
    AndroidMaterialTheme {
        FileSyncContent(
            modifier = themedModifier,
            state = PreviewFixtures.fileSyncRefreshing(),
            onAddLocalDirectory = syncCallbacks.onAddLocalDirectory,
            onAddNfsShare = syncCallbacks.onAddNfsShare,
            onDiscoverApps = syncCallbacks.onDiscoverApps,
            onRemoveSource = syncCallbacks.onRemoveSource,
            onAddProfile = syncCallbacks.onAddProfile,
            onDeleteProfile = syncCallbacks.onDeleteProfile,
            onRefreshProfile = syncCallbacks.onRefreshProfile,
            onToggleAutoRefresh = syncCallbacks.onToggleAutoRefresh,
            onSelectTargetDevice = syncCallbacks.onSelectTargetDevice,
            onSetAutoSync = syncCallbacks.onSetAutoSync,
            onSetUsbMatch = syncCallbacks.onSetUsbMatch,
            onStartSync = syncCallbacks.onStartSync,
            onCancelSync = syncCallbacks.onCancelSync,
        )
    }
}

@Preview(name = "File sync - syncing to headphones", showBackground = true, heightDp = 1900)
@Composable
internal fun FileSyncSyncingPreview() {
    AndroidMaterialTheme {
        FileSyncContent(
            modifier = themedModifier,
            state = PreviewFixtures.fileSyncSyncing(),
            onAddLocalDirectory = syncCallbacks.onAddLocalDirectory,
            onAddNfsShare = syncCallbacks.onAddNfsShare,
            onDiscoverApps = syncCallbacks.onDiscoverApps,
            onRemoveSource = syncCallbacks.onRemoveSource,
            onAddProfile = syncCallbacks.onAddProfile,
            onDeleteProfile = syncCallbacks.onDeleteProfile,
            onRefreshProfile = syncCallbacks.onRefreshProfile,
            onToggleAutoRefresh = syncCallbacks.onToggleAutoRefresh,
            onSelectTargetDevice = syncCallbacks.onSelectTargetDevice,
            onSetAutoSync = syncCallbacks.onSetAutoSync,
            onSetUsbMatch = syncCallbacks.onSetUsbMatch,
            onStartSync = syncCallbacks.onStartSync,
            onCancelSync = syncCallbacks.onCancelSync,
        )
    }
}

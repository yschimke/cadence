package ee.schimke.cadence.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.DeviceFiles
import ee.schimke.cadence.bookmarks.BookmarksScreen
import ee.schimke.cadence.bookmarks.BookmarksViewModel
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.devices.DevicesViewModel
import ee.schimke.cadence.files.DeviceFilesContent
import ee.schimke.cadence.files.DeviceFilesViewModel
import ee.schimke.cadence.home.HomeContent

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

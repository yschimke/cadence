package ee.schimke.cadence

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ee.schimke.cadence.bluetooth.BluetoothControlsScreen
import ee.schimke.cadence.bookmarks.BookmarksScreen
import ee.schimke.cadence.browser.BrowserScreen
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.files.DeviceFilesScreen
import ee.schimke.cadence.home.HomeScreen
import ee.schimke.cadence.sync.FileSyncScreen
import kotlinx.serialization.Serializable

@Composable
fun App() {
    val navController = rememberNavController()

    NavHost(
        navController,
        startDestination = Home,
        modifier = Modifier.fillMaxSize(),
        popExitTransition = {
            scaleOut(
                targetScale = 0.9f,
            )
        },
        popEnterTransition = {
            EnterTransition.None
        },
    ) {
        composable<Home> {
            HomeScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
                onFileExplorer = { navController.navigate(Devices) },
                onBookmarks = { navController.navigate(Bookmarks) },
                onBluetoothControls = { navController.navigate(BluetoothControls) },
                onFileSync = { navController.navigate(FileSync) },
            )
        }
        composable<Devices> {
            DevicesScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
                onDeviceClick = {
                    navController.navigate(DeviceFiles(id = it.id))
                })
        }
        composable<DeviceFiles> {
            DeviceFilesScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
            )
        }
        composable<Browser> {
            BrowserScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
            )
        }
        composable<Bookmarks> {
            BookmarksScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
                onNavigateTo = {
                    navController.navigate(Browser(it.toString()))
                }
            )
        }
        composable<BluetoothControls> {
            BluetoothControlsScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
            )
        }
        composable<FileSync> {
            FileSyncScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
            )
        }
    }
}

@Serializable
data object Home

@Serializable
data object Devices

@Serializable
data class DeviceFiles(val id: String)

@Serializable
data class Browser(val url: String?)

@Serializable
data object Bookmarks

@Serializable
data object BluetoothControls

@Serializable
data object FileSync

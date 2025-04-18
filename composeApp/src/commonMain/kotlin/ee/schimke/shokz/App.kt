package ee.schimke.shokz

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
import ee.schimke.shokz.devices.DevicesScreen
import ee.schimke.shokz.files.DeviceFilesScreen
import ee.schimke.shokz.home.HomeScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val navController = rememberNavController()

    NavHost(
        navController,
        startDestination = Devices,
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
                onFileExplorer = {
                    navController.navigate(Devices)
                })
        }
        composable<Devices> {
            DevicesScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
                onDeviceClick = { navController.navigate(DeviceFiles(id = it.id)) }
            )
        }
        composable<DeviceFiles> {
            DeviceFilesScreen(
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
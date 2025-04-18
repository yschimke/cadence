package ee.schimke.shokz

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ee.schimke.shokz.files.FileExplorerScreen
import ee.schimke.shokz.home.HomeScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
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
            HomeScreen(modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(), onFileExplorer = {
                navController.navigate(FileExplorer)
            })
        }
        composable<FileExplorer> {
            FileExplorerScreen(modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(), )
        }
    }
}

@Serializable
data object Home

@Serializable
data object FileExplorer
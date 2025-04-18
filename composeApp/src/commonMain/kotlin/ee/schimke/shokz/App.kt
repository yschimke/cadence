package ee.schimke.shokz

import androidx.compose.runtime.*
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

    NavHost(navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(onFileExplorer = {
//                navController.navigate(FileExplorer)
            })
        }
//        composable<FileExplorer> {
//            FileExplorerScreen()
//        }
    }
}

@Serializable
data object Home

//@Serializable
//data object FileExplorer
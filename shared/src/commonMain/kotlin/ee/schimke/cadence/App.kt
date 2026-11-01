package ee.schimke.cadence

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ee.schimke.cadence.bluetooth.BluetoothControlsScreen
import ee.schimke.cadence.bookmarks.BookmarksScreen
import ee.schimke.cadence.browser.BrowserScreen
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.files.DeviceFilesScreen
import ee.schimke.cadence.home.HomeScreen
import ee.schimke.cadence.podcasts.*
import ee.schimke.cadence.sync.FileSyncScreen
import ee.schimke.cadence.sync.ManageSyncScreen
import kotlinx.serialization.Serializable

@Composable
fun App() {
  val navController = rememberNavController()
  val podcasts = rememberPodcastController()
  val podcastState by podcasts.state.collectAsState()
  if (podcastState.target.isNotBlank()) AutomaticallyMatchHeadphones(podcastState.target)

  val entry by navController.currentBackStackEntryAsState()
  Column(Modifier.fillMaxSize()) {
    val nested = entry != null && navController.previousBackStackEntry != null
    if (nested) {
      // Takes the status bar inset here, so screens below don't add it again.
      Surface(Modifier.fillMaxWidth()) {
        Row(
          Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
          )
        ) {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      }
    }
    NavHost(
      navController,
      startDestination = Home,
      modifier =
        Modifier.weight(1f)
          .then(
            if (nested)
              Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            else Modifier
          ),
      popExitTransition = { scaleOut(targetScale = 0.9f) },
      popEnterTransition = { EnterTransition.None },
    ) {
      composable<Home> {
        HomeScreen(
          podcasts = podcasts,
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
          onAddPodcasts = { navController.navigate(AddPodcasts) },
          onOpenPodcasts = { navController.navigate(Podcasts) },
          onCopyToHeadphones = { navController.navigate(FileSync) },
          onCloudSync = { navController.navigate(CloudSync) },
          onBluetoothControls = { navController.navigate(BluetoothControls) },
          onManageSources = { navController.navigate(ManageSync) },
          onBookmarks = { navController.navigate(Bookmarks) },
          onFileExplorer = { navController.navigate(Devices) },
        )
      }
      composable<Podcasts> {
        PodcastScreen(podcasts, onChangeSource = { navController.navigate(AddPodcasts) })
      }
      composable<CloudSync> { CloudSyncScreen(podcasts) }
      composable<AddPodcasts> {
        AddPodcastsScreen(
          podcasts,
          onDone = { navController.popBackStack(Home, inclusive = false) },
        )
      }
      composable<Devices> {
        DevicesScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
          onDeviceClick = { navController.navigate(DeviceFiles(id = it.id)) },
        )
      }
      composable<DeviceFiles> {
        DeviceFilesScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()
        )
      }
      composable<Browser> {
        BrowserScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()
        )
      }
      composable<Bookmarks> {
        BookmarksScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
          onNavigateTo = { navController.navigate(Browser(it.toString())) },
        )
      }
      composable<BluetoothControls> {
        BluetoothControlsScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()
        )
      }
      composable<FileSync> {
        FileSyncScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
          onClose = { navController.popBackStack() },
          onManage = { navController.navigate(ManageSync) },
        )
      }
      composable<ManageSync> {
        ManageSyncScreen(
          modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
          onAddPodcasts = { navController.navigate(AddPodcasts) },
          onCloudBackup = { navController.navigate(CloudSync) },
        )
      }
    }
  }
}

@Serializable data object Home

@Serializable data object Devices

@Serializable data class DeviceFiles(val id: String)

@Serializable data class Browser(val url: String?)

@Serializable data object Bookmarks

@Serializable data object BluetoothControls

@Serializable data object FileSync

@Serializable data object ManageSync

@Serializable data object Podcasts

@Serializable data object CloudSync

@Serializable data object AddPodcasts

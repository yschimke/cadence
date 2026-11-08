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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ee.schimke.cadence.bluetooth.BluetoothControlsScreen
import ee.schimke.cadence.developer.DeveloperMode
import ee.schimke.cadence.developer.DeveloperModeViewModel
import ee.schimke.cadence.developer.LocalDeveloperMode
import ee.schimke.cadence.devices.DevicesScreen
import ee.schimke.cadence.files.DeviceFilesScreen
import ee.schimke.cadence.home.HomeScreen
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.platform.Analytics
import ee.schimke.cadence.podcasts.*
import ee.schimke.cadence.sync.FileSyncScreen
import ee.schimke.cadence.sync.ManageSyncScreen
import kotlinx.serialization.Serializable

@Composable
fun App(desktopNavigation: DesktopNavigation? = null) {
  val developerModel = metroViewModel<DeveloperModeViewModel>()
  val developerEnabled by developerModel.enabled.collectAsState()
  val developer =
    remember(developerEnabled) { DeveloperMode(developerEnabled, developerModel::setEnabled) }
  CompositionLocalProvider(LocalDeveloperMode provides developer) { AppContent(desktopNavigation) }
}

@Composable
private fun AppContent(desktopNavigation: DesktopNavigation?) {
  val navController = rememberNavController()
  // One screen_view per destination: the route's type name, without its arguments.
  val destination by navController.currentBackStackEntryAsState()
  val screen = destination?.destination?.route?.let(::screenName)
  LaunchedEffect(screen) {
    screen?.let { Analytics.current.screen(it) }
    val section =
      DesktopSection.entries.firstOrNull { it.routeName == screen }
        ?: when (screen) {
          "AddPodcasts" -> DesktopSection.Episodes
          "DeviceFiles" -> DesktopSection.Devices
          else -> null
        }
    if (section != null) desktopNavigation?.activeSection = section
  }
  LaunchedEffect(desktopNavigation?.request) {
    val section = desktopNavigation?.request?.second ?: return@LaunchedEffect
    val route: Any =
      when (section) {
        DesktopSection.Library -> Home
        DesktopSection.Episodes -> Podcasts
        DesktopSection.Devices -> Devices
        DesktopSection.Sources -> ManageSync
        DesktopSection.Transfers -> FileSync
        DesktopSection.CloudBackup -> CloudSync
      }
    navController.navigate(route) {
      launchSingleTop = true
      restoreState = true
      popUpTo(navController.graph.startDestinationId) { saveState = true }
    }
  }
  val podcasts = rememberPodcastController()
  val podcastState by podcasts.state.collectAsState()
  if (podcastState.target.isNotBlank()) AutomaticallyMatchHeadphones(podcastState.target)

  val entry by navController.currentBackStackEntryAsState()
  Row(Modifier.fillMaxSize()) {
    if (desktopNavigation != null) DesktopSidebar(desktopNavigation)
    Column(Modifier.weight(1f).fillMaxSize()) {
      val nested =
        entry != null &&
          navController.previousBackStackEntry != null &&
          (desktopNavigation == null || DesktopSection.entries.none { it.routeName == screen })
      if (nested) {
        // Takes the status bar inset here, so screens below don't add it again.
        Surface(Modifier.fillMaxWidth()) {
          Row(
            Modifier.windowInsetsPadding(
              WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
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
              else Modifier,
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
            onBluetoothControls = { navController.navigate(BluetoothControls) },
            onManageSources = { navController.navigate(ManageSync) },
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
            podcastTarget = podcastState.target,
            // The raw file view is a diagnostic: only offered in developer mode.
            onDeviceClick =
              if (LocalDeveloperMode.current.enabled) {
                { navController.navigate(DeviceFiles(id = it.id)) }
              } else null,
          )
        }
        composable<DeviceFiles> {
          DeviceFilesScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
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
            onClose = { navController.popBackStack() },
            onManage = { navController.navigate(ManageSync) },
          )
        }
        composable<ManageSync> {
          ManageSyncScreen(
            podcastSource = podcastState.library.playlist?.name,
            onOpenPodcasts = { navController.navigate(Podcasts) },
            modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
            onAddPodcasts = { navController.navigate(AddPodcasts) },
            onCloudBackup = { navController.navigate(CloudSync) },
          )
        }
      }
    }
  }
}

@Serializable data object Home

@Serializable data object Devices

@Serializable data class DeviceFiles(val id: String)

@Serializable data object BluetoothControls

@Serializable data object FileSync

@Serializable data object ManageSync

@Serializable data object Podcasts

@Serializable data object CloudSync

@Serializable data object AddPodcasts

/**
 * `ee.schimke.cadence.DeviceFiles/{id}?x={x}` → `DeviceFiles`: no arguments, which can be personal.
 */
internal fun screenName(route: String): String =
  route.substringBefore('/').substringBefore('?').substringAfterLast('.')

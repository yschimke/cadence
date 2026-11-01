package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ee.schimke.cadence.bluetooth.ConnectedDevice

@Composable
actual fun podcastWidgetBattery(device: ConnectedDevice?, target: String): String? =
  PodcastSyncWidget.battery(LocalContext.current.applicationContext, device, target)

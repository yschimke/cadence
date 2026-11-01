package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import ee.schimke.cadence.bluetooth.ConnectedDevice
import ee.schimke.cadence.bluetooth.batterySummary

@Composable
actual fun podcastWidgetBattery(device: ConnectedDevice?, target: String): String? =
  device?.batterySummary()

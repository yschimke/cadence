package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import ee.schimke.cadence.bluetooth.ConnectedDevice

/** Same battery summary, including a last-seen value, as the sync widget. */
@Composable expect fun podcastWidgetBattery(device: ConnectedDevice?, target: String): String?

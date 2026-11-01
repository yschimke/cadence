package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

@Composable expect fun HeadphoneAssociation(target: String)

@Composable expect fun AutomaticallyMatchHeadphones(target: String)

/** A single compatible paired headset and the matching attached volume make a tentative match. */
fun automaticHeadphoneCandidate(
  volumeMatches: Boolean,
  matchingUsbDevices: Int,
  pairedAddresses: List<String>,
  optedOut: Boolean,
): String? =
  pairedAddresses.singleOrNull()?.takeIf { volumeMatches && matchingUsbDevices == 1 && !optedOut }

/** A confirmed match takes precedence over a different currently connected headset. */
fun batteryHeadphoneAddress(linked: String?, connected: String?, lastSeen: String?): String? =
  linked ?: connected ?: lastSeen

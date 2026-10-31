package ee.schimke.cadence.bluetooth

import androidx.compose.runtime.Composable

/** Returns a launcher for the platform's Bluetooth permission flow. */
@Composable expect fun rememberBluetoothConnectPermissionLauncher(onResult: () -> Unit): () -> Unit

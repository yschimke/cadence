package ee.schimke.cadence.bluetooth

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBluetoothConnectPermissionLauncher(onResult: () -> Unit): () -> Unit = {}

package ee.schimke.cadence.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@ContributesBinding(AppScope::class, binding = binding<UsbManager>())
@Inject
class AndroidUsbManager(private val applicationContext: Context) : UsbManager {
  private val manager = applicationContext.getSystemService(android.hardware.usb.UsbManager::class.java)
  override val connection = MutableStateFlow(UsbConnection())
  override fun requestConnection() {
    connection.value = UsbConnection(message = "${manager.deviceList.size} USB device(s) attached. Select Manage New Device to grant access to a mounted storage directory.")
  }
  override fun disconnect() { connection.value = UsbConnection(message = "Directory permissions are managed by Android. Unplug the device to disconnect USB.") }
  override fun listDevices(): Flow<List<UsbDevice>> = callbackFlow {
    fun snapshot() = manager.deviceList.values.map {
      UsbDevice(it.deviceId, it.deviceName, it.deviceClass, it.vendorId, it.manufacturerName, it.productId, it.productName)
    }
    val listener = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) { trySend(snapshot()) }
    }
    applicationContext.registerReceiver(listener, IntentFilter().apply {
      addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
      addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
    }, Context.RECEIVER_NOT_EXPORTED)
    trySend(snapshot())
    awaitClose { applicationContext.unregisterReceiver(listener) }
  }
}

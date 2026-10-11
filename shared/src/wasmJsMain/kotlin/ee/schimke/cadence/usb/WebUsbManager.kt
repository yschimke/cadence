@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.cadence.usb

import ee.schimke.cadence.browser.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class Snapshot(
  val available: Boolean,
  val message: String,
  val devices: List<WebDevice>,
)

@Serializable
private data class WebDevice(
  val id: Int,
  val name: String,
  val vendorId: Int,
  val productId: Int,
  val deviceClass: Int,
  val manufacturerName: String?,
  val productName: String?,
)

class WebUsbManager {
  private val devices = MutableStateFlow<List<UsbDevice>>(emptyList())
  private val status = MutableStateFlow(UsbConnection())
  private val dispose = watchBrowserUsb(::update)
  val manager =
    object : UsbManager {
      override val connection: Flow<UsbConnection> = status

      override fun listDevices(): Flow<List<UsbDevice>> = devices

      override fun requestConnection() {
        status.value =
          status.value.copy(
            busy = true,
            message = "Choose the USB storage device in the browser permission dialog.",
          )
        connectBrowserUsb(::update) { status.value = status.value.copy(busy = false, message = it) }
      }

      override fun disconnect() {
        disconnectBrowserUsb()
        status.value = status.value.copy(busy = false)
      }
    }

  private fun update(json: String) {
    val snapshot = Json.decodeFromString<Snapshot>(json)
    status.value = UsbConnection(snapshot.available, message = snapshot.message)
    devices.value =
      snapshot.devices.map {
        UsbDevice(
          it.id,
          it.name,
          it.deviceClass,
          it.vendorId,
          it.manufacturerName,
          it.productId,
          it.productName,
        )
      }
  }

  fun close() {
    manager.disconnect()
    disposeBrowserUsb(dispose)
  }
}

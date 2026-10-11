package ee.schimke.cadence.usb

import kotlinx.coroutines.flow.Flow

interface UsbManager {
  val connection: Flow<UsbConnection>
    get() = kotlinx.coroutines.flow.flowOf(UsbConnection())

  fun requestConnection() {}

  fun disconnect() {}

  fun listDevices(): Flow<List<UsbDevice>>
}

data class UsbDevice(
  val id: Int,
  val name: String,
  val deviceClass: Int,
  val vendorId: Int,
  val manufacturerName: String?,
  val productId: Int,
  val productName: String?,
)

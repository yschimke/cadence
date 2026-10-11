package ee.schimke.cadence.usb

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.usb4java.*

/** Descriptor discovery only. The OS retains ownership of mounted mass-storage interfaces. */
class DesktopUsbManager(scope: CoroutineScope) : UsbManager {
  override val connection =
    MutableStateFlow(
      UsbConnection(message = "USB storage: select its mounted directory with Manage New Device.")
    )
  private var watching = true

  override fun requestConnection() {
    watching = true
    connection.value =
      UsbConnection(
        message = "Scanning USB. Select the mounted volume with Manage New Device to access files."
      )
  }

  override fun disconnect() {
    watching = false
    connection.value =
      UsbConnection(
        message =
          "USB discovery paused. Mounted directories remain managed by the operating system."
      )
  }

  private val devices: SharedFlow<List<UsbDevice>> = flow {
    val context = Context()
    var initialized = false
    try {
      val result = LibUsb.init(context)
      check(result == LibUsb.SUCCESS) { "libusb: ${LibUsb.strError(result)}" }
      initialized = true
      while (currentCoroutineContext().isActive) {
        val devices = mutableListOf<UsbDevice>()
        if (watching) {
          val list = DeviceList()
          try {
            val count = LibUsb.getDeviceList(context, list)
            check(count >= 0) { "USB discovery failed: $count" }
            for (device in list) {
              val descriptor = DeviceDescriptor()
              if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) continue
              val vendor = descriptor.idVendor().toInt() and 0xffff
              val product = descriptor.idProduct().toInt() and 0xffff
              val id = LibUsb.getBusNumber(device) * 256 + LibUsb.getDeviceAddress(device)
              devices +=
                UsbDevice(
                  id,
                  "USB ${vendor.toString(16).padStart(4, '0')}:${product.toString(16).padStart(4, '0')}",
                  descriptor.bDeviceClass().toInt() and 0xff,
                  vendor,
                  null,
                  product,
                  if (CadenceUsb.isKnownStorage(vendor, product)) "Cadence USB Storage" else null,
                )
            }
          } finally {
            LibUsb.freeDeviceList(list, true)
          }
        }
        emit(devices)
        delay(1500)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      connection.value =
        UsbConnection(
          available = false,
          message =
            "USB discovery unavailable: ${e.message}. Mounted directory access still works.",
        )
      emit(emptyList())
    } finally {
      if (initialized) LibUsb.exit(context)
    }
  }
    .flowOn(Dispatchers.IO)
    .distinctUntilChanged()
    .shareIn(scope, SharingStarted.Eagerly, replay = 1)

  override fun listDevices(): Flow<List<UsbDevice>> = devices
}

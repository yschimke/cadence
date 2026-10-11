package ee.schimke.cadence.usb

data class UsbConnection(
  val available: Boolean = true,
  val busy: Boolean = false,
  val message: String = "USB storage uses the system directory picker.",
)

object CadenceUsb {
  const val VENDOR_ID = 0x05e3
  const val PRODUCT_ID = 0x0761
  const val MASS_STORAGE_CLASS = 0x08

  fun isKnownStorage(vendorId: Int, productId: Int) =
    vendorId == VENDOR_ID && productId == PRODUCT_ID

  fun webUsbCanClaim(interfaceClass: Int) =
    interfaceClass !in setOf(0x01, 0x03, 0x08, 0x0b, 0x0e, 0x10, 0xe0)
}

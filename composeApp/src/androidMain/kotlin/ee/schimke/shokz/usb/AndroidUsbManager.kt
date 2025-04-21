package ee.schimke.shokz.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesBinding(AppScope::class, binding = binding<ee.schimke.shokz.usb.UsbManager>())
@Inject
class AndroidUsbManager(
    private val applicationContext: Context,
) : ee.schimke.shokz.usb.UsbManager {
    val usbManager = applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager

    fun registerListener() {
        applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                val usbDevice =
                    intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE,
                        android.hardware.usb.UsbDevice::class.java
                    )
            }

        }, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        })
    }

    override fun listDevices(): Flow<List<ee.schimke.shokz.usb.UsbDevice>> = flow {
        val deviceList: Map<String, UsbDevice> = usbManager.deviceList
        emit(deviceList.values.map {
            UsbDevice(
                id = it.deviceId,
                name = it.deviceName,
                deviceClass = it.deviceClass,
                vendorId = it.vendorId,
                manufacturerName = it.manufacturerName,
                productId = it.productId,
                productName = it.productName
            )
        })
    }
}
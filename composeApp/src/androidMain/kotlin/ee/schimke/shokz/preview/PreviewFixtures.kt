package ee.schimke.shokz.preview

import ee.schimke.shokz.data.Volume
import ee.schimke.shokz.datastore.proto.Bookmark
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.usb.UsbDevice
import okio.Path.Companion.toPath

/**
 * Hand-rolled fixtures for `@Preview` composables. Kept off the production DI
 * graph so previews render without hitting USB/Bluetooth/DataStore.
 */
internal object PreviewFixtures {
    val devices: List<Device> = listOf(
        Device(
            id = "dev-1",
            name = "OpenSwim Pro",
            path = "content://com.android.externalstorage.documents/tree/0000-0000%3A",
        ),
        Device(
            id = "dev-2",
            name = "OpenRun Pro 2",
            path = "content://com.android.externalstorage.documents/tree/1111-1111%3A",
        ),
    )

    val usbDevices: List<UsbDevice> = listOf(
        UsbDevice(
            id = 1001,
            name = "/dev/bus/usb/001/005",
            deviceClass = 0,
            vendorId = 0x2FE3,
            manufacturerName = "Shokz",
            productId = 0x0100,
            productName = "OpenSwim Pro",
        ),
        UsbDevice(
            id = 1002,
            name = "/dev/bus/usb/001/006",
            deviceClass = 0,
            vendorId = 0x18D1,
            manufacturerName = "Generic",
            productId = 0x4EE7,
            productName = "Android Debug Bridge",
        ),
    )

    val bookmarks: List<Bookmark> = listOf(
        Bookmark(
            name = "Podbean",
            url = "https://www.podbean.com/all",
            favicon = "https://pbcdn1.podbean.com/fs1/site/images/favicon.ico",
        ),
        Bookmark(
            name = "BBC Sounds",
            url = "https://www.bbc.co.uk/sounds",
            favicon = "",
        ),
    )

    val volume: Volume = Volume(
        uuid = "0000-0000",
        volumeName = "SHOKZ",
        state = "mounted",
        description = "Shokz OpenSwim Pro",
        path = "/storage/0000-0000".toPath(),
        removable = true,
    )

    val device: Device = devices.first()
    val rootPath = "/storage/0000-0000".toPath()
}

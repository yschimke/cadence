package ee.schimke.cadence.devices

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsbEjectTest {
  private val app: android.app.Application = org.robolectric.RuntimeEnvironment.getApplication()

  private fun action(title: String): Notification.Action =
    Notification.Action.Builder(
        null,
        title,
        PendingIntent.getBroadcast(app, 0, Intent("x"), PendingIntent.FLAG_IMMUTABLE),
      )
      .build()

  @Test
  fun recognisesTheSystemsDriveNotification() {
    assertTrue(UsbEject.isUsbStorageNotification("STO", null))
    assertTrue(UsbEject.isUsbStorageNotification(null, "public:8,1"))
    assertFalse(UsbEject.isUsbStorageNotification("media", "now-playing"))
    assertFalse(UsbEject.isUsbStorageNotification(null, null))
  }

  @Test
  fun picksOnlyTheEjectAction() {
    val explore = action("Explore")
    val eject = action("Eject")
    assertEquals(eject, UsbEject.findEjectAction(arrayOf(explore, eject), null))
    assertNull(UsbEject.findEjectAction(arrayOf(explore), null))
    assertNull(UsbEject.findEjectAction(null, "Eject"))
  }

  @Test
  fun matchesTheSystemsTranslatedLabel() {
    val explore = action("Entdecken")
    val eject = action("Auswerfen")
    assertEquals(eject, UsbEject.findEjectAction(arrayOf(explore, eject), "Auswerfen"))
  }

  @Test
  fun reportsNotFoundWhenNothingIsListening() {
    var notFound = 0
    UsbEject.requestEject(app) { notFound++ }
    shadowOf(android.os.Looper.getMainLooper()).idle()
    assertEquals(1, notFound)
  }
}

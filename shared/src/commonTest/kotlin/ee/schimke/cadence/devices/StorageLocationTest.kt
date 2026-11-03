package ee.schimke.cadence.devices

import kotlin.test.Test
import kotlin.test.assertEquals

class StorageLocationTest {
  private val tree = "content://com.android.externalstorage.documents/tree/"

  @Test
  fun wholeUsbDrive() {
    assertEquals(
      StorageLocation("USB drive 1CFC-4AC3", null, removable = true),
      storageLocation("${tree}1CFC-4AC3%3A"),
    )
  }

  @Test
  fun folderOnAUsbDrive() {
    assertEquals(
      StorageLocation("USB drive 1CFC-4AC3", "Podcasts/Swim", removable = true),
      storageLocation("${tree}1CFC-4AC3%3APodcasts%2FSwim"),
    )
  }

  @Test
  fun phoneStorage() {
    assertEquals(
      StorageLocation("Phone storage", "Music", removable = false),
      storageLocation("${tree}primary%3AMusic"),
    )
  }

  @Test
  fun desktopFolder() {
    assertEquals("SWIM", storageLocation("/Volumes/SWIM").drive)
  }
}

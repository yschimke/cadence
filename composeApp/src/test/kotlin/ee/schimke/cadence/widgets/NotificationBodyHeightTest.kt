package ee.schimke.cadence.widgets

import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The height a notification body is held to: SystemUI's player takes every dp offered, so the
 * container must be about as tall as what the body draws, not the 256dp a custom body may have.
 */
class NotificationBodyHeightTest {
  private val downloading =
    NotificationBody(
      "Downloading podcasts",
      "Downloading 1 of 2: Liverpool vs Man United and the most hopeful clubs after 10 games · 51%",
      NotificationIcon.Download,
      progress = 0.51f,
    )
  private val ready = NotificationBody("Podcasts ready", "2 episodes", NotificationIcon.Done)

  @Test
  fun aDownloadIsFarShorterThanTheCap() {
    val height = notificationBodyHeightDp(downloading, screenWidthDp = 411, fontScale = 1f)
    assertTrue(height in 90..130, "${height}dp")
  }

  @Test
  fun aShortNoteIsShorterThanADownload() {
    assertTrue(
      notificationBodyHeightDp(ready, 411, 1f) < notificationBodyHeightDp(downloading, 411, 1f),
    )
  }

  @Test
  fun largerTextGetsMoreRoomButStaysUnderTheCap() {
    val normal = notificationBodyHeightDp(downloading, 411, 1f)
    val large = notificationBodyHeightDp(downloading, 411, 1.3f)
    assertTrue(large > normal, "$normal → $large")
    assertTrue(large < 200, "${large}dp")
  }
}

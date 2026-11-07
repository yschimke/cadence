package ee.schimke.cadence

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenNameTest {
  @Test
  fun screenNameIsTheRouteTypeWithoutArguments() {
    assertEquals("Home", screenName("ee.schimke.cadence.Home"))
    assertEquals("DeviceFiles", screenName("ee.schimke.cadence.DeviceFiles/{address}"))
    assertEquals("Browser", screenName("ee.schimke.cadence.Browser?url={url}"))
  }
}

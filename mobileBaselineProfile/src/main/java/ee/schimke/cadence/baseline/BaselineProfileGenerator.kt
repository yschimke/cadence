package ee.schimke.cadence.baseline

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule val rule = BaselineProfileRule()

  @Test
  fun startup() =
    rule.collect(packageName = "ee.schimke.cadence", includeInStartupProfile = true) {
      pressHome()
      startActivityAndWait()
    }

  @Test
  fun browse() =
    rule.collect(packageName = "ee.schimke.cadence") {
      pressHome()
      startActivityAndWait()
      val width = device.displayWidth
      val height = device.displayHeight
      device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
      device.waitForIdle()
    }
}

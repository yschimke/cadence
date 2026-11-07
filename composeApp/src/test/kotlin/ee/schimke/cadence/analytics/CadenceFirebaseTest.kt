package ee.schimke.cadence.analytics

import ee.schimke.cadence.platform.Analytics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CadenceFirebaseTest {
  @Test
  fun installsNothingWithoutTheSdk() {
    CadenceFirebase.install(RuntimeEnvironment.getApplication())
    assertEquals(Analytics.None, Analytics.current)
    assertTrue(CadenceFirebase.liverySinks().isEmpty())
  }
}

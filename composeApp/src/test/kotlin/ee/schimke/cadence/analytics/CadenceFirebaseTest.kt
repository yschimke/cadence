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
  /**
   * The SDK isn't on this classpath, as in local, PR and preview builds. Reflection over the
   * object's methods is what Robolectric does when it loads a class; a Firebase type in a signature
   * fails it with NoClassDefFoundError.
   */
  @Test
  fun loadsAndInstallsNothingWithoutTheSdk() {
    CadenceFirebase::class.java.declaredMethods
    CadenceFirebase::class.java.declaredFields
    CadenceFirebase.install(RuntimeEnvironment.getApplication())
    assertEquals(Analytics.None, Analytics.current)
    assertTrue(CadenceFirebase.liverySinks().isEmpty())
  }
}

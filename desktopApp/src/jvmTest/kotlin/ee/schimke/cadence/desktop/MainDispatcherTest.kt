package ee.schimke.cadence.desktop

import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The app's ViewModels start flows with `viewModelScope`, which runs on `Dispatchers.Main`. This
 * test runs on the same runtime classpath the distributable ships, so a missing
 * `kotlinx-coroutines-swing` fails here instead of on the user's "Error" window.
 */
class MainDispatcherTest {
  @Test
  fun mainDispatcherRunsOnTheAwtEventThread() {
    val onEventThread = runBlocking {
      withContext(Dispatchers.Main) { SwingUtilities.isEventDispatchThread() }
    }
    assertTrue(onEventThread, "Dispatchers.Main should dispatch to the AWT event thread")
  }
}

package ee.schimke.cadence.sync

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FileSyncUiStateTest {
  @Test
  fun recomputesWhenAnUpstreamFlowEmits() = transformTest {
    val sources = MutableStateFlow<List<SyncSource>>(emptyList())
    val preferences = MutableStateFlow<SyncPreferences?>(null)
    val state =
      transform(
        initialValue = FileSyncViewModel.UiState(),
        scope = backgroundScope,
        dispatcher = testDispatcher,
      ) {
        fileSyncUiState(
          sources = sources,
          profiles = flowOf(emptyList()),
          devices = flowOf(emptyList()),
          preferences = preferences,
          refresh = flowOf(RefreshProgress()),
          sync = flowOf(SyncProgress()),
        )
      }
    advanceUntilIdle()
    assertEquals(FileSyncViewModel.UiState(), state.value)

    val source = SyncSource(id = "s1", name = "Music")
    sources.value = listOf(source)
    preferences.value = SyncPreferences(usb_match = "Shokz")
    // advanceUntilIdle() alone is not enough: the flow collectors write snapshot state, and only
    // advance() sends the apply notification that schedules the recomposition.
    advance()

    assertEquals(listOf(source), state.value.sources)
    assertEquals("Shokz", state.value.preferences.usb_match)
  }
}

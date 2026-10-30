package ee.schimke.cadence.sync

import ee.schimke.cadence.data.SettingsStore
import ee.schimke.cadence.datastore.proto.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class HostIdTest {
  private class Store : SettingsStore {
    override val data = MutableStateFlow(Settings())

    override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings =
      transform(data.value).also { data.value = it }
  }

  @Test
  fun generatesOnceAndPersists() = runBlocking {
    val store = Store()
    val host = HostId(store)
    val first = host.get()
    assertTrue(first.isNotBlank(), "generated host id must not be blank")
    val second = host.get()
    assertEquals(first, second, "second call returns the same id")
    assertEquals(first, store.data.value.host_id, "id is persisted in settings")
  }

  @Test
  fun honoursExistingValue() = runBlocking {
    val store = Store()
    store.updateData { it.copy(host_id = "pre-existing") }
    assertEquals("pre-existing", HostId(store).get())
  }
}

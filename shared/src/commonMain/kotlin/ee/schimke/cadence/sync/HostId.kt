@file:OptIn(ExperimentalUuidApi::class)

package ee.schimke.cadence.sync

import ee.schimke.cadence.data.SettingsStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first

/** Stable per-install identifier used to attribute sync writes. Set once, read many. */
class HostId(private val store: SettingsStore) {
  suspend fun get(): String {
    val current = store.data.first().host_id
    if (current.isNotBlank()) return current
    val fresh = Uuid.random().toString()
    store.updateData { settings ->
      // Another caller may have won the race; prefer whatever landed first.
      if (settings.host_id.isBlank()) settings.copy(host_id = fresh) else settings
    }
    return store.data.first().host_id
  }
}

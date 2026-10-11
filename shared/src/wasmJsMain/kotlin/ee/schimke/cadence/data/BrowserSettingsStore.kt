package ee.schimke.cadence.data

import ee.schimke.cadence.browser.readSettings
import ee.schimke.cadence.browser.writeSettings
import ee.schimke.cadence.datastore.proto.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class BrowserSettingsStore : SettingsStore {
  private val lock = Mutex()
  override val data =
    MutableStateFlow(
      runCatching {
        readSettings()?.let { Settings.ADAPTER.decode(requireNotNull(it.decodeBase64())) }
          ?: Settings()
      }
        .getOrElse { Settings() }
    )

  override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings =
    lock.withLock {
      val next = transform(data.value)
      writeSettings(next.encode().toByteString().base64())
      data.value = next
      next
    }
}

package ee.schimke.cadence.data

import ee.schimke.cadence.datastore.proto.Settings
import kotlinx.coroutines.flow.Flow

/** Protobuf settings, with platform-owned durable storage. */
interface SettingsStore {
  val data: Flow<Settings>

  suspend fun updateData(transform: suspend (Settings) -> Settings): Settings
}

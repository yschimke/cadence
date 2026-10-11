package ee.schimke.cadence.data

import dev.zacsweers.metro.Inject
import ee.schimke.cadence.datastore.proto.AppearancePreferences
import ee.schimke.cadence.datastore.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
class AppearanceRepo(private val dataStore: SettingsStore) {

  val themeMode: Flow<ThemeMode> =
    dataStore.data.map { it.appearance?.theme_mode ?: ThemeMode.SYSTEM }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.updateData { settings ->
      val current = settings.appearance ?: AppearancePreferences()
      settings.copy(appearance = current.copy(theme_mode = mode))
    }
  }
}

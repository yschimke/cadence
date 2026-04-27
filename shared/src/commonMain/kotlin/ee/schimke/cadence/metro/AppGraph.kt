package ee.schimke.cadence.metro

import android.content.Context
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ee.schimke.cadence.data.createFilesDataStore
import ee.schimke.cadence.datastore.proto.Settings
import okio.Path.Companion.toOkioPath

interface AppGraph {

  @Provides
  @SingleIn(AppScope::class)
  fun provideFilesDataStore(context: Context): DataStore<Settings> {
    return createFilesDataStore { context.filesDir.resolve("devices-4.pb").toOkioPath() }
  }
}

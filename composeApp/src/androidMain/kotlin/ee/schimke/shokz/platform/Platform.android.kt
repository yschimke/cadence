package ee.schimke.shokz.platform

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.Inject
import okio.FileSystem

@Inject
class AndroidPlatform(val fileSystem: FileSystem, val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}
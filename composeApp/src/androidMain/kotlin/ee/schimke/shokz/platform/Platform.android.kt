package ee.schimke.shokz.platform

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import okio.FileSystem

@ContributesBinding(AppScope::class, binding = binding<Platform>())
@Inject
class AndroidPlatform(val fileSystem: FileSystem, val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}
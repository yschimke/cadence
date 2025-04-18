package ee.schimke.shokz.platform

import android.os.Build
import dev.zacsweers.metro.Inject

@Inject
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}
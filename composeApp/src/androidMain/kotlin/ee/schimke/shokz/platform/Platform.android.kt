package ee.schimke.shokz.platform

import android.content.Context
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.google.modernstorage.storage.toOkioPath
import com.google.modernstorage.storage.toUri
import dev.zacsweers.metro.Inject
import okio.FileSystem
import okio.Path

@Inject
class AndroidPlatform(val fileSystem: FileSystem, val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override fun listRecursively(path: Path): List<Path> {
        val f = DocumentFile.fromTreeUri(context, path.toUri())

        return f?.listFiles()?.map { it.uri.toOkioPath() }.orEmpty()
    }
}
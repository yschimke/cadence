package ee.schimke.shokz.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.google.modernstorage.storage.toUri
import okio.ForwardingFileSystem
import okio.Path
import kotlin.collections.addAll

class AndroidFixedFileSystem(val context: Context) : ForwardingFileSystem(AndroidFileSystem(context)) {
    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> {
        if (isPhysicalFile(dir)) {
            return super.listRecursively(dir, followSymlinks)
        } else {
            return sequence {
                val toList = mutableListOf(DocumentFile.fromTreeUri(context, dir.toUri()))

                while (toList.isNotEmpty()) {
                    val next = toList.removeAt(0)!!

                    if (next.isDirectory) {
                        toList.addAll(next.listFiles())
                    } else {
                        yield(next.uri.toOkioPath())
                    }
                }
            }
        }
    }

    override fun canonicalize(path: Path): Path {
        if (isPhysicalFile(path)) {
            return super.canonicalize(path)
        } else {
            if (DocumentFile.isDocumentUri(context, path.toUri())) {
                val directory = DocumentFile.fromTreeUri(context, path.toUri())

                if (directory != null) {
                    return directory.uri.toOkioPath()
                }
            }
            return path
        }
    }

    private fun isPhysicalFile(file: Path): Boolean {
        return file.toString().first() == '/'
    }
}
package ee.schimke.shokz.sync

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

@ContributesBinding(AppScope::class, binding = binding<StagingArea>())
@SingleIn(AppScope::class)
@Inject
class AndroidStagingArea(
    private val context: Context,
) : StagingArea {

    override val fileSystem: FileSystem = FileSystem.SYSTEM

    private val root: Path by lazy {
        val dir = context.filesDir.resolve("sync-staging")
        dir.mkdirs()
        dir.toOkioPath()
    }

    override fun rootDir(): Path = root

    override fun profileDir(profileSubpath: String): Path {
        val safe = profileSubpath.ifBlank { "default" }
        val dir = root / safe
        if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
        return dir
    }

    override fun listProfileFiles(profileSubpath: String): List<StagedEntry> {
        val base = profileDir(profileSubpath)
        if (!fileSystem.exists(base)) return emptyList()
        val out = mutableListOf<StagedEntry>()
        walk(base, base, out)
        return out
    }

    private fun walk(base: Path, current: Path, out: MutableList<StagedEntry>) {
        for (child in fileSystem.list(current)) {
            val md = fileSystem.metadataOrNull(child) ?: continue
            if (md.isDirectory) walk(base, child, out)
            else if (md.isRegularFile) {
                out += StagedEntry(
                    absolutePath = child,
                    relativePath = child.toString().removePrefix(base.toString()).trimStart('/'),
                    sizeBytes = md.size ?: 0L,
                )
            }
        }
    }

    override fun clearProfile(profileSubpath: String) {
        val dir = profileDir(profileSubpath)
        runCatching { fileSystem.deleteRecursively(dir) }
        runCatching { fileSystem.createDirectories(dir) }
    }

    override fun profileSize(profileSubpath: String): Long {
        return listProfileFiles(profileSubpath).sumOf { it.sizeBytes }
    }
}

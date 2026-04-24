package ee.schimke.shokz.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ee.schimke.shokz.datastore.proto.SourceKind
import ee.schimke.shokz.datastore.proto.SyncSource

@ContributesBinding(AppScope::class, binding = binding<SourceBrowser>())
@Inject
class AndroidSourceBrowser(
    private val context: Context,
    private val syncRepo: SyncRepo,
) : SourceBrowser {

    override suspend fun browse(sourceId: String): List<BrowsedItem> {
        val source = syncRepo.getSource(sourceId) ?: return emptyList()
        return when (source.kind) {
            SourceKind.LOCAL_DIRECTORY -> browseTreeUri(source)
            SourceKind.NFS_SHARE -> emptyList()
        }
    }

    private fun browseTreeUri(source: SyncSource): List<BrowsedItem> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(source.location)) ?: return emptyList()
        val start = source.subpath.split('/').filter { it.isNotBlank() }
            .fold(root) { dir, segment -> dir.findFile(segment) ?: return emptyList() }
        if (!start.isDirectory) return emptyList()

        val out = mutableListOf<BrowsedItem>()
        walk(start, "", source.id, out)
        return out
    }

    private fun walk(
        dir: DocumentFile,
        prefix: String,
        sourceId: String,
        out: MutableList<BrowsedItem>,
    ) {
        for (entry in dir.listFiles()) {
            val name = entry.name ?: continue
            val relative = if (prefix.isEmpty()) name else "$prefix/$name"
            if (entry.isDirectory) {
                walk(entry, relative, sourceId, out)
            } else if (entry.isFile) {
                out += BrowsedItem(
                    sourceId = sourceId,
                    displayName = name,
                    sourceUri = entry.uri.toString(),
                    sizeBytes = entry.length(),
                    relativePath = relative,
                )
            }
        }
    }
}

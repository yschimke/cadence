package ee.schimke.cadence.sync

import ee.schimke.cadence.datastore.proto.SourceKind

/** Lists a folder source on desktop and in the browser, where [DirectoryBackend] reads folders. */
class PortableSourceBrowser(private val repo: SyncRepo, private val backend: DirectoryBackend) :
  SourceBrowser {
  override suspend fun browse(sourceId: String): List<BrowsedItem> {
    val source = repo.getSource(sourceId) ?: return emptyList()
    if (source.kind != SourceKind.LOCAL_DIRECTORY) return emptyList()
    val root =
      if (source.subpath.isBlank()) source.location
      else joinDirectory(source.location, source.subpath)
    return backend.files(root).map {
      BrowsedItem(
        sourceId = source.id,
        displayName = it.relativePath.substringAfterLast('/'),
        sourceUri = it.location,
        sizeBytes = it.size,
        relativePath = it.relativePath,
      )
    }
  }
}

package ee.schimke.cadence.filelist

import ee.schimke.cadence.browser.browserCall
import ee.schimke.cadence.sync.BrowserFile
import ee.schimke.cadence.sync.browserArgs
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

actual suspend fun listDirectory(fileSystem: FileSystem?, path: Path): List<DirectoryEntry> =
  Json.decodeFromString<List<BrowserFile>>(
      browserCall("entries", browserArgs("root" to path.toString()))
    )
    .map { DirectoryEntry(it.location.toPath(), it.directory) }

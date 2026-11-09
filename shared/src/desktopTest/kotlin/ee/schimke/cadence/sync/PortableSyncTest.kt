package ee.schimke.cadence.sync

import ee.schimke.cadence.data.*
import ee.schimke.cadence.datastore.proto.*
import kotlin.random.Random
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path

class PortableSyncTest {
  private class Store : SettingsStore {
    override val data = MutableStateFlow(Settings())

    override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings =
      transform(data.value).also { data.value = it }
  }

  private val fs = FileSystem.SYSTEM

  private fun <T> withTempDir(prefix: String, block: suspend (Path) -> T): T = runBlocking {
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "$prefix-${Random.nextLong().toULong()}"
    fs.createDirectories(root, mustCreate = true)
    try {
      block(root)
    } finally {
      fs.deleteRecursively(root)
    }
  }

  private fun write(path: Path, text: String) {
    fs.createDirectories(requireNotNull(path.parent))
    fs.write(path) { writeUtf8(text) }
  }

  private fun read(path: Path): String = fs.read(path) { readUtf8() }

  @Test
  fun duplicateProfileNamesHaveIndependentStaging() = runBlocking {
    val repo = SyncRepo(Store())
    val first = repo.addProfile("Music", emptyList())
    val second = repo.addProfile("Music", emptyList())
    assertNotEquals(first.staging_subpath, second.staging_subpath)
  }

  @Test
  fun foldersAddedFromTheLibraryShareOneProfileAndListTheirFiles() =
    withTempDir("cadence-library") { root ->
      val swim = root / "swim"
      write(swim / "Intervals" / "Set 1.mp3", "one")
      val books = root / "books"
      write(books / "Chapter 1.mp3", "chapter")
      val repo = SyncRepo(Store())
      val library = repo.addFolderToLibrary("Swim", swim.toString())
      assertEquals(library, repo.addFolderToLibrary("Books", books.toString()))
      val profile = repo.listProfiles().single()
      assertEquals(LIBRARY_PROFILE, profile.name)
      assertEquals(2, profile.source_ids.size)
      assertTrue(profile.auto_refresh)

      val browser =
        PortableSourceBrowser(repo, DesktopDirectoryBackend((root / "staging").toString()))
      val items = browser.browse(profile.source_ids.first())
      assertEquals(listOf("Intervals/Set 1.mp3"), items.map { it.relativePath })
      assertEquals("Set 1.mp3", items.single().displayName)
      assertEquals(3, items.single().sizeBytes)
      assertTrue(browser.browse("missing").isEmpty())
    }

  @Test
  fun refreshStagesAndSyncsMountedVolume() =
    withTempDir("cadence-sync") { root ->
      val source = root / "source"
      write(source / "nested" / "track.mp3", "audio fixture")
      val target = (root / "mounted").also { fs.createDirectories(it) }
      val store = Store()
      val repo = SyncRepo(store)
      val devices = DevicesRepo(store)
      repo.addLocalDirectorySource("Source", source.toString())
      val profile = repo.addProfile("Podcasts", listOf(repo.sources.first().single().id))
      devices.addDevice(Device("device", "USB volume", target.toString()))
      repo.updatePreferences { it.copy(target_device_id = "device") }
      val backend = DesktopDirectoryBackend((root / "staging").toString())
      val refresh = PortableRefresh(repo, backend)
      refresh.refresh(profile.id)
      assertEquals(1, refresh.progress.value.filesCompleted)
      assertTrue(repo.getProfile(profile.id)!!.last_refreshed_at.isNotBlank())
      // Sync must use the refreshed staging snapshot, not changing source contents.
      write(source / "nested" / "track.mp3", "changed later")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      try {
        val sync = PortableSync(repo, devices, backend, scope)
        sync.startSync(null)
        withTimeout(5000) { sync.progress.first { !it.running && it.filesCompleted == 1 } }
        assertEquals("audio fixture", read(target / "podcasts" / "nested" / "track.mp3"))
        assertNull(sync.progress.value.lastError)
      } finally {
        scope.cancel()
      }
    }

  @Test
  fun duplicateSourcesFailBeforeReplacingStaging() =
    assertDuplicateRejected("track.mp3", "track.mp3")

  @Test
  fun caseOnlyDuplicatesFailBeforeReplacingStaging() =
    assertDuplicateRejected("Track.mp3", "track.mp3")

  private fun assertDuplicateRejected(firstName: String, secondName: String) =
    withTempDir("cadence-duplicate") { root ->
      val repo = SyncRepo(Store())
      for ((name, file) in listOf("one" to firstName, "two" to secondName)) {
        val source = root / name
        write(source / file, name)
        repo.addLocalDirectorySource(name, source.toString())
      }
      val profile = repo.addProfile("Music", repo.sources.first().map { it.id })
      val existing = root / "staging" / "music" / "existing.mp3"
      write(existing, "keep")
      val refresh = PortableRefresh(repo, DesktopDirectoryBackend((root / "staging").toString()))
      assertFailsWith<IllegalArgumentException> { refresh.refresh(profile.id) }
      assertEquals("keep", read(existing))
      assertTrue(refresh.progress.value.lastError!!.contains("duplicate"))
    }
}

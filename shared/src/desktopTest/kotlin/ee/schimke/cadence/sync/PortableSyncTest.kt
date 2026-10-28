package ee.schimke.cadence.sync

import ee.schimke.cadence.data.*
import ee.schimke.cadence.datastore.proto.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class PortableSyncTest {
  private class Store : SettingsStore {
    override val data = MutableStateFlow(Settings())

    override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings =
      transform(data.value).also { data.value = it }
  }

  @Test
  fun duplicateProfileNamesHaveIndependentStaging() = runBlocking {
    val repo = SyncRepo(Store())
    val first = repo.addProfile("Music", emptyList())
    val second = repo.addProfile("Music", emptyList())
    assertNotEquals(first.staging_subpath, second.staging_subpath)
  }

  @Test
  fun foldersAddedFromTheLibraryShareOneProfileAndListTheirFiles() = runBlocking {
    val root = Files.createTempDirectory("cadence-library").toFile()
    try {
      val swim = root.resolve("swim").apply { mkdirs() }
      swim.resolve("Intervals").mkdirs()
      swim.resolve("Intervals/Set 1.mp3").writeText("one")
      val books = root.resolve("books").apply { mkdirs() }
      books.resolve("Chapter 1.mp3").writeText("chapter")
      val repo = SyncRepo(Store())
      val library = repo.addFolderToLibrary("Swim", swim.path)
      assertEquals(library, repo.addFolderToLibrary("Books", books.path))
      val profile = repo.listProfiles().single()
      assertEquals(LIBRARY_PROFILE, profile.name)
      assertEquals(2, profile.source_ids.size)
      assertTrue(profile.auto_refresh)

      val browser =
        PortableSourceBrowser(repo, DesktopDirectoryBackend(root.resolve("staging").path))
      val items = browser.browse(profile.source_ids.first())
      assertEquals(listOf("Intervals/Set 1.mp3"), items.map { it.relativePath })
      assertEquals("Set 1.mp3", items.single().displayName)
      assertEquals(3, items.single().sizeBytes)
      assertTrue(browser.browse("missing").isEmpty())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun refreshStagesAndSyncsMountedVolume() = runBlocking {
    val root = Files.createTempDirectory("cadence-sync").toFile()
    try {
      val source = root.resolve("source").apply { mkdirs() }
      source.resolve("nested").mkdirs()
      source.resolve("nested/track.mp3").writeText("audio fixture")
      val target = root.resolve("mounted").apply { mkdirs() }
      val store = Store()
      val repo = SyncRepo(store)
      val devices = DevicesRepo(store)
      repo.addLocalDirectorySource("Source", source.path)
      val profile = repo.addProfile("Podcasts", listOf(repo.sources.first().single().id))
      devices.addDevice(Device("device", "USB volume", target.path))
      repo.updatePreferences { it.copy(target_device_id = "device") }
      val backend = DesktopDirectoryBackend(root.resolve("staging").path)
      val refresh = PortableRefresh(repo, backend)
      refresh.refresh(profile.id)
      assertEquals(1, refresh.progress.value.filesCompleted)
      assertTrue(repo.getProfile(profile.id)!!.last_refreshed_at.isNotBlank())
      // Sync must use the refreshed staging snapshot, not changing source contents.
      source.resolve("nested/track.mp3").writeText("changed later")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      try {
        val sync = PortableSync(repo, devices, backend, scope)
        sync.startSync(null)
        withTimeout(5000) { sync.progress.first { !it.running && it.filesCompleted == 1 } }
        assertEquals("audio fixture", target.resolve("podcasts/nested/track.mp3").readText())
        assertNull(sync.progress.value.lastError)
      } finally {
        scope.cancel()
      }
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun duplicateSourcesFailBeforeReplacingStaging() = runBlocking {
    val root = Files.createTempDirectory("cadence-duplicate").toFile()
    try {
      val store = Store()
      val repo = SyncRepo(store)
      for (name in listOf("one", "two")) {
        val source = root.resolve(name).apply { mkdirs() }
        source.resolve("track.mp3").writeText(name)
        repo.addLocalDirectorySource(name, source.path)
      }
      val profile = repo.addProfile("Music", repo.sources.first().map { it.id })
      val staging = root.resolve("staging/music").apply { mkdirs() }
      staging.resolve("existing.mp3").writeText("keep")
      val refresh = PortableRefresh(repo, DesktopDirectoryBackend(root.resolve("staging").path))
      assertFailsWith<IllegalArgumentException> { refresh.refresh(profile.id) }
      assertEquals("keep", staging.resolve("existing.mp3").readText())
      assertTrue(refresh.progress.value.lastError!!.contains("duplicate"))
    } finally {
      root.deleteRecursively()
    }
  }
}

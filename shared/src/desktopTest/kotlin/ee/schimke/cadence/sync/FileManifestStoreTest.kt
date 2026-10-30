package ee.schimke.cadence.sync

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

class FileManifestStoreTest {
  @Test
  fun recordsAndReadsBack() = runBlocking {
    val root = Files.createTempDirectory("cadence-manifest").toFile()
    try {
      val store = FileManifestStore(root.toOkioPath())
      store.record(
        deviceId = "device-1",
        relPath = "podcasts/ep42.mp3",
        entry =
          ManifestEntry(
            size = 1024,
            lastModifiedMillis = 1000,
            hostId = "host-a",
            syncedAt = "2026-10-30T07:00:00Z",
          ),
      )
      store.record(
        deviceId = "device-1",
        relPath = "music/track.flac",
        entry =
          ManifestEntry(
            size = 2048,
            lastModifiedMillis = 2000,
            hostId = "host-a",
            syncedAt = "2026-10-30T07:01:00Z",
          ),
      )

      val reopened = FileManifestStore(root.toOkioPath())
      val loaded = reopened.load("device-1")
      assertEquals(2, loaded.entries.size)
      assertEquals(1024, loaded.entries["podcasts/ep42.mp3"]?.size)
      assertEquals("host-a", loaded.entries["music/track.flac"]?.hostId)
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun separateDevicesSeparateFiles() = runBlocking {
    val root = Files.createTempDirectory("cadence-manifest").toFile()
    try {
      val store = FileManifestStore(root.toOkioPath())
      store.record("device-A", "a.mp3", entry("host-1"))
      store.record("device-B", "b.mp3", entry("host-2"))

      assertEquals(1, store.load("device-A").entries.size)
      assertEquals("host-2", store.load("device-B").entries["b.mp3"]?.hostId)
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun loadMissingReturnsEmpty() = runBlocking {
    val root = Files.createTempDirectory("cadence-manifest").toFile()
    try {
      val store = FileManifestStore(root.toOkioPath())
      assertEquals(0, store.load("unknown-device").entries.size)
    } finally {
      root.deleteRecursively()
    }
  }

  private fun entry(hostId: String) =
    ManifestEntry(size = 1, lastModifiedMillis = 0, hostId = hostId, syncedAt = "")
}

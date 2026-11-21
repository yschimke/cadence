package ee.schimke.cadence.podcasts

import kotlin.test.*

class ReplacedSourceTest {
  private val pocketCasts =
    PodcastLibrary(
      playlist = PlaylistSnapshot(name = "Up Next", episodes = emptyList()),
      source = PodcastSourceKind.PocketCasts,
    )

  @Test
  fun anotherServiceReplacesTheConnectedOne() {
    assertEquals("Pocket Casts", replacedSource(pocketCasts, PodcastProvider.LibroFm))
    assertEquals("Pocket Casts", replacedSource(pocketCasts, PodcastProvider.Rss))
  }

  @Test
  fun theSameServiceOrNothingConnectedReplacesNothing() {
    assertNull(replacedSource(pocketCasts, PodcastProvider.PocketCasts))
    assertNull(replacedSource(PodcastLibrary(), PodcastProvider.LibroFm))
    // A saved export and an Overcast paste both come in through the same option.
    val overcast = pocketCasts.copy(source = PodcastSourceKind.Overcast)
    assertNull(replacedSource(overcast, PodcastProvider.Export))
  }
}

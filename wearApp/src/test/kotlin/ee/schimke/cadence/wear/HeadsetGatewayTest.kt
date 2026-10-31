package ee.schimke.cadence.wear

import ee.schimke.rcsp.EqualizerPreset
import ee.schimke.rcsp.RcspCapabilities
import ee.schimke.rcsp.RcspCommand
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HeadsetGatewayTest {
  @Test
  fun `fake gateway independently gates each transport action`() = runBlocking {
    val gateway =
      FakeGateway(
        HeadsetSnapshot(
          capabilities =
            RcspCapabilities().withSupported(RcspCommand.Play.id, RcspCommand.NextTrack.id),
          media = HeadsetMedia(playing = false),
        )
      )

    assertIs<HeadsetActionResult.Unsupported>(gateway.execute(HeadsetAction.PreviousTrack))
    assertEquals(HeadsetActionResult.Sent, gateway.execute(HeadsetAction.Play))
    assertEquals(true, gateway.snapshot.value.media?.playing)
    assertIs<HeadsetActionResult.Unsupported>(gateway.execute(HeadsetAction.Pause))
    assertEquals(HeadsetActionResult.Sent, gateway.execute(HeadsetAction.NextTrack))
    assertEquals(listOf(HeadsetAction.Play, HeadsetAction.NextTrack), gateway.actions)
  }

  @Test
  fun `fake gateway exposes headset playlist only when list capability is present`() {
    val withPlaylist =
      HeadsetSnapshot(
        capabilities =
          RcspCapabilities()
            .withSupported(RcspCommand.ListLocalAudio.id, RcspCommand.PlayLocalAudio(2u).id),
        localAudio = listOf(LocalAudioTrack(podcastTrack(2u, "Morning Swim"), 2, true)),
      )
    val withoutPlaylist =
      withPlaylist.copy(
        capabilities = RcspCapabilities().withSupported(RcspCommand.PlayLocalAudio(2u).id)
      )

    assertEquals(true, withPlaylist.isSupported(RcspCommand.ListLocalAudio))
    assertEquals(false, withoutPlaylist.isSupported(RcspCommand.ListLocalAudio))
    assertEquals("Morning Swim", withPlaylist.localAudio.single().podcast.episodeTitle)
    assertEquals("segment-2.mp3", withPlaylist.localAudio.single().podcast.storage.fileName)
  }

  @Test
  fun `progress is displayable only with genuine timing and seek capability`() {
    val position = HeadsetMedia(positionMs = 10_000, durationMs = 60_000)
    val supported =
      HeadsetSnapshot(
        media = position,
        capabilities = RcspCapabilities().withSupported(HeadsetAction.SeekRelative(1).command.id),
      )
    val noSeek = supported.copy(capabilities = RcspCapabilities())
    val noDuration = supported.copy(media = position.copy(durationMs = null))

    assertEquals(true, supported.hasSeekableProgress())
    assertEquals(false, noSeek.hasSeekableProgress())
    assertEquals(false, noDuration.hasSeekableProgress())
  }

  @Test
  fun `equalizer action is capability gated through the public rcsp command`() = runBlocking {
    val action = HeadsetAction.SelectEqualizerPreset(EqualizerPreset.Swimming)
    // #441 adds supported OpenSwim controls to the profile default. This case must explicitly
    // model an adapter that has not reported equalizer support.
    val unsupported = FakeGateway(HeadsetSnapshot(capabilities = RcspCapabilities()))
    val supported =
      FakeGateway(
        HeadsetSnapshot(capabilities = RcspCapabilities().withSupported(action.command.id))
      )

    assertIs<HeadsetActionResult.Unsupported>(unsupported.execute(action))
    assertEquals(HeadsetActionResult.Sent, supported.execute(action))
    assertEquals(listOf<HeadsetAction>(action), supported.actions)
  }

  private class FakeGateway(initial: HeadsetSnapshot) : HeadsetGateway {
    private val mutableSnapshot = MutableStateFlow(initial)
    override val snapshot: StateFlow<HeadsetSnapshot> = mutableSnapshot
    val actions = mutableListOf<HeadsetAction>()

    override suspend fun execute(action: HeadsetAction): HeadsetActionResult {
      if (!isSupported(action.command)) {
        return HeadsetActionResult.Unsupported(action.command.id)
      }
      actions += action
      when (action) {
        HeadsetAction.Play ->
          mutableSnapshot.value =
            snapshot.value.copy(media = snapshot.value.media?.copy(playing = true))
        HeadsetAction.Pause ->
          mutableSnapshot.value =
            snapshot.value.copy(media = snapshot.value.media?.copy(playing = false))
        else -> Unit
      }
      return HeadsetActionResult.Sent
    }
  }

  private fun podcastTrack(trackId: UInt, episodeTitle: String) =
    SyncedPodcastTrack(
      episodeKey = "episode-$trackId",
      sourceRevision = "revision",
      audioVersion = "audio-version",
      episodeTitle = episodeTitle,
      podcastTitle = "Saltwater Daily",
      sectionId = "section-$trackId",
      sectionStartMs = 0,
      sectionEndMs = 60_000,
      storage = HeadsetAudioStorage(trackId, "segment-$trackId.mp3"),
    )
}

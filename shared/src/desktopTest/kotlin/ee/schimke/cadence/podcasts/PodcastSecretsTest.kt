package ee.schimke.cadence.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodcastSecretsTest {
  @Test
  fun splitsSignedInSessionsOutAndMergesThemBack() {
    val state =
      LocalPodcastState(
        target = "headset",
        pocketCasts = PocketCastsSession(accessToken = "a", refreshToken = "r", email = "e"),
        libroFm = LibroFmSession(accessToken = "l", email = "e"),
      )
    val split = PodcastSecrets.split(podcastJson.encodeToString(state))

    assertTrue(split.changed)
    assertFalse(split.library.contains("\"r\""))
    assertFalse(split.library.contains("pocketCasts"))
    val withoutSessions = podcastJson.decodeFromString<LocalPodcastState>(split.library)
    assertEquals(
      state.copy(pocketCasts = PocketCastsSession(), libroFm = LibroFmSession()),
      withoutSessions,
    )

    val merged = PodcastSecrets.merge(split.library, split.secrets)
    assertEquals(state, podcastJson.decodeFromString<LocalPodcastState>(merged))
  }

  @Test
  fun signedOutSessionsAreNotSecrets() {
    val split = PodcastSecrets.split(podcastJson.encodeToString(LocalPodcastState()))
    assertNull(split.secrets)
    assertTrue(split.changed)
    assertFalse(PodcastSecrets.split(split.library).changed)
  }

  @Test
  fun keepsOnlyTheSignedInSession() {
    val state = LocalPodcastState(libroFm = LibroFmSession(accessToken = "l"))
    val split = PodcastSecrets.split(podcastJson.encodeToString(state))
    assertFalse(split.secrets!!.contains("pocketCasts"))
    assertEquals(
      state,
      podcastJson.decodeFromString(PodcastSecrets.merge(split.library, split.secrets)),
    )
  }
}

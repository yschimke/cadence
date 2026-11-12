package ee.schimke.cadence.bluetooth

import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.PodcastSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BluetoothStateTest {
  private val openSwim = ConnectedDevice("Shokz OpenSwim Pro", "00:11", emptyList())

  private val pixelWatch =
    BluetoothDeviceCandidate(
      address = "B8:F4:A4:C1:D0:D3",
      name = "Goteria 40UH",
      serviceUuids =
        setOf(
          "0000111e-0000-1000-8000-00805f9b34fb", // HFP Handsfree
          "0000110a-0000-1000-8000-00805f9b34fb", // A2DP Source (not Sink)
          "0000110e-0000-1000-8000-00805f9b34fb", // AVRCP
        ),
      majorDeviceClass = BluetoothClassCodes.MajorWearable,
      deviceClass = BluetoothClassCodes.WearableWristWatch,
      connectedProfiles = setOf("HFP"),
    )

  private val pixelBuds =
    BluetoothDeviceCandidate(
      address = "AA:BB:CC:DD:EE:01",
      name = "Pixel Buds Pro",
      serviceUuids = setOf(A2dpAudioSinkUuid, "0000111e-0000-1000-8000-00805f9b34fb"),
      majorDeviceClass = BluetoothClassCodes.MajorAudioVideo,
      deviceClass = BluetoothClassCodes.AudioVideoHeadphones,
      connectedProfiles = setOf("A2DP", "HFP"),
    )

  private val renamedRcspHeadset =
    BluetoothDeviceCandidate(
      address = "AA:BB:CC:DD:EE:02",
      name = "Pool Headphones",
      serviceUuids =
        setOf(
          RcspVendorServiceUuid,
          A2dpAudioSinkUuid,
          "00001101-0000-1000-8000-00805f9b34fb",
        ),
      majorDeviceClass = BluetoothClassCodes.MajorAudioVideo,
      deviceClass = BluetoothClassCodes.AudioVideoWearableHeadset,
      connectedProfiles = setOf("A2DP", "HFP"),
    )

  private val pushedEpisode =
    Episode(
      backend = "pocket-casts",
      id = "ep-uuid-412",
      title = "Episode 412 — Tides of the Pacific",
      audioUrl = "https://example.com/412.mp3",
      durationMs = 1_800_000L,
      revision = "r1",
      audioVersion = "v1",
      podcastTitle = "Saltwater Daily",
    )

  @Test
  fun localAudioControlsNeedOpenSwimInMp3Mode() {
    assertTrue(
      BluetoothState(connectedDevice = openSwim, workingMode = WorkingMode.Mp3).localAudioControls,
    )
    assertFalse(BluetoothState(connectedDevice = openSwim).localAudioControls)
    assertFalse(
      BluetoothState(connectedDevice = openSwim.copy(name = "Buds"), workingMode = WorkingMode.Mp3)
        .localAudioControls,
    )
    assertFalse(BluetoothState(workingMode = WorkingMode.Mp3).localAudioControls)
  }

  @Test
  fun openSwimMatchSupportsNameOrVerifiedProtocol() {
    assertTrue(
      BluetoothState(connectedDevice = openSwim.copy(name = "openswim pro")).openSwimConnected,
    )
    val renamedVerified =
      ConnectedDevice("Pool Headphones", "00:22", listOf("A2DP"), verifiedOpenSwim = true)
    assertTrue(BluetoothState(connectedDevice = renamedVerified).openSwimConnected)
    assertEquals(
      "50% · ~3h MP3 · ~4h 30m BT",
      renamedVerified.copy(batteryPercent = 50).batterySummary(),
    )
  }

  @Test
  fun pixelWatchAndCarAudioAreExcludedUnderEveryFilter() {
    val carStereo =
      BluetoothDeviceCandidate(
        address = "11:22:33:44:55:66",
        name = "Car Multimedia",
        serviceUuids = setOf(A2dpAudioSinkUuid),
        majorDeviceClass = BluetoothClassCodes.MajorAudioVideo,
        deviceClass = BluetoothClassCodes.AudioVideoCarAudio,
        connectedProfiles = setOf("A2DP"),
      )
    assertEquals(BluetoothDeviceKind.Other, pixelWatch.kind())
    assertEquals(BluetoothDeviceKind.Other, carStereo.kind())

    val shokzSelection =
      selectConnectedBluetoothDevice(listOf(pixelWatch, carStereo), BluetoothDeviceFilter.ShokzOnly)
    assertNull(shokzSelection.selected)
    assertNull(shokzSelection.hiddenHeadphone)

    val allSelection =
      selectConnectedBluetoothDevice(
        listOf(pixelWatch, carStereo),
        BluetoothDeviceFilter.AllHeadphones,
      )
    assertNull(allSelection.selected)
  }

  @Test
  fun defaultShokzFilterMatchesRcspProtocolAndReportsHiddenHeadphones() {
    assertEquals(BluetoothDeviceKind.Shokz, renamedRcspHeadset.kind())
    assertTrue(renamedRcspHeadset.needsOpenSwimProbe())
    assertFalse(renamedRcspHeadset.copy(verifiedOpenSwim = true).needsOpenSwimProbe())

    val onlyGenericConnected =
      selectConnectedBluetoothDevice(
        listOf(pixelWatch, pixelBuds),
        BluetoothDeviceFilter.ShokzOnly,
      )
    assertNull(onlyGenericConnected.selected)
    assertEquals("Pixel Buds Pro", onlyGenericConnected.hiddenHeadphone?.name)

    val allHeadphones =
      selectConnectedBluetoothDevice(
        listOf(pixelWatch, pixelBuds),
        BluetoothDeviceFilter.AllHeadphones,
      )
    assertEquals("Pixel Buds Pro", allHeadphones.selected?.name)

    val bothConnected =
      selectConnectedBluetoothDevice(
        listOf(pixelWatch, pixelBuds, renamedRcspHeadset),
        BluetoothDeviceFilter.AllHeadphones,
      )
    assertEquals("Pool Headphones", bothConnected.selected?.name)
  }

  @Test
  fun mediaControlsIgnoreUnrelatedAppsAndScopeToSourceOrPushedItems() {
    val cadencePkg = "ee.schimke.cadence"
    val allowed =
      allowedMediaPackages(
        cadencePackage = cadencePkg,
        podcastSource = PodcastSourceKind.PocketCasts,
        pocketCastsConnected = true,
      )
    val talkSport =
      MediaSessionCandidate(
        packageName = "com.talksport.tsliveen",
        title = "Live Radio",
        artist = null,
        album = null,
        playing = true,
      )
    val pocketCastsOtherEpisode =
      MediaSessionCandidate(
        packageName = PocketCastsPackage,
        title = "Unrelated Show",
        artist = "Other",
        album = null,
        mediaId = "other-ep-999",
        playing = true,
      )
    val pocketCastsPushedEpisode =
      MediaSessionCandidate(
        packageName = PocketCastsPackage,
        title = "Episode 412 — Tides of the Pacific",
        artist = "Saltwater Daily",
        album = null,
        mediaId = "ep-uuid-412",
        playing = false,
      )

    // Unrelated app (com.talksport.tsliveen) is never controllable, even with headphones connected.
    assertNull(
      selectControllableMediaSession(
        sessions = listOf(talkSport),
        cadencePackage = cadencePkg,
        allowedPackages = allowed,
        hasConnectedSource = true,
        headphonesConnectedInBtMode = true,
        pushedEpisodes = listOf(pushedEpisode),
      ),
    )

    // Without headphones connected, only an episode Cadence is pushing is controllable (case c).
    assertNull(
      selectControllableMediaSession(
        sessions = listOf(talkSport, pocketCastsOtherEpisode),
        cadencePackage = cadencePkg,
        allowedPackages = allowed,
        hasConnectedSource = true,
        headphonesConnectedInBtMode = false,
        pushedEpisodes = listOf(pushedEpisode),
      ),
    )
    assertEquals(
      2,
      selectControllableMediaSession(
        sessions = listOf(talkSport, pocketCastsOtherEpisode, pocketCastsPushedEpisode),
        cadencePackage = cadencePkg,
        allowedPackages = allowed,
        hasConnectedSource = true,
        headphonesConnectedInBtMode = false,
        pushedEpisodes = listOf(pushedEpisode),
      ),
    )

    // With headphones connected in BT mode and a connected source (case b), the connected source
    // app is controllable and prefers the pushed episode when present.
    assertEquals(
      1,
      selectControllableMediaSession(
        sessions = listOf(talkSport, pocketCastsOtherEpisode),
        cadencePackage = cadencePkg,
        allowedPackages = allowed,
        hasConnectedSource = true,
        headphonesConnectedInBtMode = true,
        pushedEpisodes = listOf(pushedEpisode),
      ),
    )
  }
}

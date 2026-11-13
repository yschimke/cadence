package ee.schimke.cadence.bluetooth

import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.PodcastSourceKind

/** Which Bluetooth audio devices Cadence attaches to. */
enum class BluetoothDeviceFilter(val label: String) {
  /** Only RCSP (`0xFEF0`) / Shokz headphones. */
  ShokzOnly("Shokz only"),
  /** Any Bluetooth headphones (A2DP Sink or LE Audio), excluding watches and non-audio gear. */
  AllHeadphones("All headphones"),
}

/** Classification of a Bluetooth device from its advertised SDP protocols and device class. */
enum class BluetoothDeviceKind {
  /** Speaks RCSP (`0xFEF0`) on an audio sink, or is a named/verified Shokz headset. */
  Shokz,
  /** Standard Bluetooth headphones/earbuds/headset (A2DP Sink or LE Audio). */
  Headphones,
  /** Watch, phone, computer, peripheral, car/TV system, or HFP-only non-sink device. */
  Other,
}

/**
 * Protocol facts Android caches on a `BluetoothDevice` via SDP and Class of Device, without opening
 * an RFCOMM or GATT socket.
 */
data class BluetoothDeviceCandidate(
  val address: String,
  val name: String,
  val serviceUuids: Set<String> = emptySet(),
  val majorDeviceClass: Int? = null,
  val deviceClass: Int? = null,
  val connectedProfiles: Set<String> = emptySet(),
  val verifiedOpenSwim: Boolean = false,
) {
  private val normalizedUuids: Set<String> = serviceUuids.map { it.lowercase() }.toSet()

  val isOpenSwimPro: Boolean
    get() = verifiedOpenSwim || name.contains(OpenSwimProName, ignoreCase = true)

  val hasRcspProtocol: Boolean
    get() = RcspVendorServiceUuid in normalizedUuids

  val hasAudioSinkProtocol: Boolean
    get() =
      A2dpAudioSinkUuid in normalizedUuids ||
        normalizedUuids.any { it in LeAudioServiceUuids } ||
        "A2DP" in connectedProfiles ||
        "LE Audio" in connectedProfiles

  fun kind(): BluetoothDeviceKind {
    if (majorDeviceClass in NonHeadphoneMajorClasses) return BluetoothDeviceKind.Other
    if (deviceClass in NonHeadphoneAudioVideoClasses) return BluetoothDeviceKind.Other

    val audioCapable =
      hasAudioSinkProtocol ||
        (normalizedUuids.isEmpty() &&
          (deviceClass in HeadphoneDeviceClasses || isNamedShokz(name) || isOpenSwimPro))
    if (!audioCapable) return BluetoothDeviceKind.Other

    return if (isOpenSwimPro || hasRcspProtocol || isNamedShokz(name)) {
      BluetoothDeviceKind.Shokz
    } else {
      BluetoothDeviceKind.Headphones
    }
  }

  /**
   * True when the device advertises the `0xFEF0` vendor service on an audio sink, has not yet been
   * confirmed as an OpenSwim Pro, and should be probed with read-only `QueryMediaState` before any
   * vendor write commands are enabled.
   */
  fun needsOpenSwimProbe(): Boolean =
    !isOpenSwimPro && kind() == BluetoothDeviceKind.Shokz && hasRcspProtocol

  /** Lower rank wins when multiple devices are paired or connected simultaneously. */
  fun priorityRank(): Int =
    when {
      isOpenSwimPro -> 0
      kind() == BluetoothDeviceKind.Shokz && hasRcspProtocol -> 1
      kind() == BluetoothDeviceKind.Shokz -> 2
      kind() == BluetoothDeviceKind.Headphones -> 3
      else -> 4
    }
}

data class SelectedBluetoothDevices(
  val selected: BluetoothDeviceCandidate?,
  val hiddenHeadphone: BluetoothDeviceCandidate?,
)

fun selectConnectedBluetoothDevice(
  candidates: Collection<BluetoothDeviceCandidate>,
  filter: BluetoothDeviceFilter,
): SelectedBluetoothDevices {
  val ranked =
    candidates
      .filter { it.kind() != BluetoothDeviceKind.Other }
      .sortedWith(compareBy<BluetoothDeviceCandidate> { it.priorityRank() }.thenBy { it.address })
  return when (filter) {
    BluetoothDeviceFilter.ShokzOnly -> {
      val shokz = ranked.firstOrNull { it.kind() == BluetoothDeviceKind.Shokz }
      val otherHeadphone =
        if (shokz == null) ranked.firstOrNull { it.kind() == BluetoothDeviceKind.Headphones }
        else null
      SelectedBluetoothDevices(selected = shokz, hiddenHeadphone = otherHeadphone)
    }
    BluetoothDeviceFilter.AllHeadphones ->
      SelectedBluetoothDevices(selected = ranked.firstOrNull(), hiddenHeadphone = null)
  }
}

/** Vendor RCSP SPP service UUID (`0xFEF0`, matching `OpenSwimProProfile.serviceUuid`). */
const val RcspVendorServiceUuid = "0000fef0-0000-1000-8000-00805f9b34fb"

/**
 * Classic Bluetooth A2DP Audio Sink UUID (`0x110B`). Watches expose `0x110A` (Source), not Sink.
 */
const val A2dpAudioSinkUuid = "0000110b-0000-1000-8000-00805f9b34fb"

/** Bluetooth LE Audio service UUIDs (ASCS, PACS, CAS, HAS). */
val LeAudioServiceUuids: Set<String> =
  setOf(
    "0000184e-0000-1000-8000-00805f9b34fb",
    "00001850-0000-1000-8000-00805f9b34fb",
    "00001853-0000-1000-8000-00805f9b34fb",
    "00001854-0000-1000-8000-00805f9b34fb",
  )

// Standard Bluetooth SIG Baseband Class of Device constants (mirrors
// android.bluetooth.BluetoothClass).
object BluetoothClassCodes {
  const val MajorComputer = 0x0100
  const val MajorPhone = 0x0200
  const val MajorLanAccess = 0x0300
  const val MajorAudioVideo = 0x0400
  const val MajorPeripheral = 0x0500
  const val MajorImaging = 0x0600
  const val MajorWearable = 0x0700
  const val MajorToy = 0x0800
  const val MajorHealth = 0x0900

  const val AudioVideoWearableHeadset = 0x0404
  const val AudioVideoHandsfree = 0x0408
  const val AudioVideoHeadphones = 0x0418
  const val AudioVideoPortableAudio = 0x041C
  const val AudioVideoCarAudio = 0x0420
  const val AudioVideoSetTopBox = 0x0424
  const val AudioVideoHifiAudio = 0x0428
  const val AudioVideoVcr = 0x042C
  const val AudioVideoVideoCamera = 0x0430
  const val AudioVideoCamcorder = 0x0434
  const val AudioVideoVideoMonitor = 0x0438
  const val AudioVideoVideoDisplayAndLoudspeaker = 0x043C
  const val AudioVideoVideoConferencing = 0x0440
  const val AudioVideoVideoGamingToy = 0x0448

  const val WearableWristWatch = 0x0704
}

private val NonHeadphoneMajorClasses =
  setOf(
    BluetoothClassCodes.MajorComputer,
    BluetoothClassCodes.MajorPhone,
    BluetoothClassCodes.MajorLanAccess,
    BluetoothClassCodes.MajorPeripheral,
    BluetoothClassCodes.MajorImaging,
    BluetoothClassCodes.MajorWearable,
    BluetoothClassCodes.MajorToy,
    BluetoothClassCodes.MajorHealth,
  )

private val NonHeadphoneAudioVideoClasses =
  setOf(
    BluetoothClassCodes.AudioVideoCarAudio,
    BluetoothClassCodes.AudioVideoSetTopBox,
    BluetoothClassCodes.AudioVideoVcr,
    BluetoothClassCodes.AudioVideoVideoCamera,
    BluetoothClassCodes.AudioVideoCamcorder,
    BluetoothClassCodes.AudioVideoVideoMonitor,
    BluetoothClassCodes.AudioVideoVideoDisplayAndLoudspeaker,
    BluetoothClassCodes.AudioVideoVideoConferencing,
    BluetoothClassCodes.AudioVideoVideoGamingToy,
  )

private val HeadphoneDeviceClasses =
  setOf(
    BluetoothClassCodes.AudioVideoWearableHeadset,
    BluetoothClassCodes.AudioVideoHandsfree,
    BluetoothClassCodes.AudioVideoHeadphones,
    BluetoothClassCodes.AudioVideoPortableAudio,
    BluetoothClassCodes.AudioVideoHifiAudio,
  )

private fun isNamedShokz(name: String): Boolean =
  name.contains("Shokz", ignoreCase = true) ||
    name.contains("AfterShokz", ignoreCase = true) ||
    name.contains("OpenSwim", ignoreCase = true) ||
    name.contains("OpenRun", ignoreCase = true) ||
    name.contains("OpenFit", ignoreCase = true) ||
    name.contains("OpenMove", ignoreCase = true) ||
    name.contains("OpenComm", ignoreCase = true) ||
    name.contains("OpenDots", ignoreCase = true)

// ---- Media session & pushed-episode matching ----

const val PocketCastsPackage = "au.com.shiftyjelly.pocketcasts"
val AntennaPodPackages: Set<String> = setOf("de.danoeh.antennapod", "de.danoeh.antennapod.debug")

/** Packages whose media sessions belong to Cadence or the user's connected podcast source. */
fun allowedMediaPackages(
  cadencePackage: String,
  podcastSource: PodcastSourceKind?,
  pocketCastsConnected: Boolean,
): Set<String> = buildSet {
  add(cadencePackage)
  if (podcastSource == PodcastSourceKind.PocketCasts || pocketCastsConnected) {
    add(PocketCastsPackage)
  }
  if (podcastSource == PodcastSourceKind.Gpodder) {
    addAll(AntennaPodPackages)
  }
}

data class MediaSessionCandidate(
  val packageName: String,
  val title: String?,
  val artist: String?,
  val album: String?,
  val mediaId: String? = null,
  val playing: Boolean,
  val active: Boolean = playing || !title.isNullOrBlank(),
  val durationMs: Long? = null,
  val positionMs: Long? = null,
)

fun matchesPushedEpisode(
  session: MediaSessionCandidate,
  cadencePackage: String,
  pushedEpisodes: List<Episode>,
): Boolean {
  if (session.packageName == cadencePackage) return !session.title.isNullOrBlank()
  if (pushedEpisodes.isEmpty()) return false
  val mediaId = session.mediaId?.trim().orEmpty()
  if (mediaId.isNotEmpty()) {
    val byId = pushedEpisodes.any { ep ->
      (ep.id.isNotBlank() && mediaId.contains(ep.id, ignoreCase = true)) ||
        (ep.key.isNotBlank() && mediaId.equals(ep.key, ignoreCase = true))
    }
    if (byId) return true
  }
  val sessionTitle = normalizeMediaTitle(session.title) ?: return false
  return pushedEpisodes.any { ep ->
    val episodeTitle = normalizeMediaTitle(ep.title) ?: return@any false
    sessionTitle == episodeTitle
  }
}

/**
 * Returns the index of the media session Cadence should display and control, or `null` when none
 * qualifies under:
 * - (b) headphones connected in Bluetooth mode with a connected source, or
 * - (c) the connected app actively playing media Cadence is pushing.
 */
fun selectControllableMediaSession(
  sessions: List<MediaSessionCandidate>,
  cadencePackage: String,
  allowedPackages: Set<String>,
  hasConnectedSource: Boolean,
  headphonesConnectedInBtMode: Boolean,
  pushedEpisodes: List<Episode>,
): Int? {
  val eligible =
    sessions.withIndex().filter { (_, session) ->
      if (!session.active || session.packageName !in allowedPackages) return@filter false
      val isPushed = matchesPushedEpisode(session, cadencePackage, pushedEpisodes)
      when {
        isPushed -> true
        headphonesConnectedInBtMode && hasConnectedSource -> true
        else -> false
      }
    }
  return eligible
    .minWithOrNull(
      compareBy<IndexedValue<MediaSessionCandidate>>(
        { !matchesPushedEpisode(it.value, cadencePackage, pushedEpisodes) },
        { !it.value.playing },
        { it.index },
      ),
    )
    ?.index
}

fun friendlyMediaSourceLabel(packageName: String?, cadencePackage: String? = null): String? =
  when {
    packageName.isNullOrBlank() -> null
    packageName == cadencePackage || packageName == "ee.schimke.cadence" -> "Cadence"
    packageName == PocketCastsPackage -> "Pocket Casts"
    packageName in AntennaPodPackages -> "AntennaPod"
    else -> packageName
  }

private fun normalizeMediaTitle(value: String?): String? =
  value?.trim()?.replace(Regex("\\s+"), " ")?.lowercase()?.takeIf { it.isNotEmpty() }

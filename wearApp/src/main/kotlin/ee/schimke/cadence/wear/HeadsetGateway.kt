package ee.schimke.cadence.wear

import ee.schimke.rcsp.EqualizerPreset
import ee.schimke.rcsp.OpenSwimProProfile
import ee.schimke.rcsp.RcspCapabilities
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.RcspCommandId
import ee.schimke.rcsp.RcspDeviceProfile
import ee.schimke.rcsp.SeekDirection
import ee.schimke.rcsp.WorkingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Semantic boundary between Wear presentation and a headset implementation.
 *
 * This intentionally contains no Bluetooth sockets, packets, Android media-session commands, or
 * OpenSwim authentication. The authenticated OpenSwim adapter owns those details and projects
 * verified :rcsp facts into [HeadsetSnapshot].
 */
interface HeadsetGateway {
  val snapshot: StateFlow<HeadsetSnapshot>

  /** The authenticated adapter is the authority for each individual command check. */
  fun isSupported(command: RcspCommand): Boolean = snapshot.value.capabilities.isSupported(command)

  suspend fun execute(action: HeadsetAction): HeadsetActionResult
}

/** Semantic UI actions backed by the :rcsp public command catalogue. */
sealed interface HeadsetAction {
  val command: RcspCommand

  data class SelectMode(val mode: WorkingMode) : HeadsetAction {
    override val command = RcspCommand.SelectWorkingMode(mode)
  }

  data class SelectEqualizerPreset(val preset: EqualizerPreset) : HeadsetAction {
    override val command = RcspCommand.SelectEqualizerPreset(preset)
  }

  data object PreviousTrack : HeadsetAction {
    override val command = RcspCommand.PreviousTrack
  }

  data object Play : HeadsetAction {
    override val command = RcspCommand.Play
  }

  data object Pause : HeadsetAction {
    override val command = RcspCommand.Pause
  }

  data object NextTrack : HeadsetAction {
    override val command = RcspCommand.NextTrack
  }

  data class PlayLocalAudio(val trackId: UInt) : HeadsetAction {
    override val command = RcspCommand.PlayLocalAudio(trackId)
  }

  data class SeekRelative(val deltaMs: Long) : HeadsetAction {
    init {
      require(deltaMs != 0L) { "Relative seek must not be zero" }
      require(deltaMs != Long.MIN_VALUE) { "Relative seek is out of range" }
    }

    override val command =
      RcspCommand.SeekRelative(
        direction = if (deltaMs > 0) SeekDirection.Forward else SeekDirection.Backward,
        durationMs = kotlin.math.abs(deltaMs).toUInt(),
      )
  }

  data class SetVolume(val percent: Int) : HeadsetAction {
    init {
      require(percent in 0..100) { "Volume must be a percentage from 0 to 100" }
    }

    override val command = RcspCommand.SetVolume(percent.toUByte())
  }
}

data class HeadsetSnapshot(
  val profile: RcspDeviceProfile = OpenSwimProProfile,
  val connection: HeadsetConnection = HeadsetConnection.Disconnected,
  val mode: WorkingMode = WorkingMode.Unknown,
  val media: HeadsetMedia? = null,
  val localAudio: List<LocalAudioTrack> = emptyList(),
  val capabilities: RcspCapabilities = profile.capabilities,
  /** Null when the adapter cannot verify a headset-reported volume value. */
  val volumePercent: Int? = null,
  val sync: CompanionSyncStatus = CompanionSyncStatus.Unavailable,
) {
  fun isSupported(command: RcspCommand): Boolean = capabilities.isSupported(command)

  fun hasSeekableProgress(): Boolean =
    media?.let { it.positionMs != null && it.durationMs != null && it.durationMs > 0 } == true &&
      isSupported(HeadsetAction.SeekRelative(1).command)
}

sealed interface HeadsetConnection {
  data object Disconnected : HeadsetConnection

  data object Connecting : HeadsetConnection

  data class Authenticated(val deviceName: String) : HeadsetConnection

  /** Phone reports a matching OpenSwim connection; this is not a watch-side RCSP authentication. */
  data class PhoneConnected(val deviceName: String) : HeadsetConnection

  data class Unavailable(val reason: String) : HeadsetConnection
}

data class HeadsetMedia(
  /**
   * Canonical podcast identity projected by the sync layer.
   *
   * The headset's MP3 filename is deliberately only a storage locator in [SyncedPodcastTrack]; it
   * is never used as display metadata. The future authenticated adapter resolves this from the
   * source episode, transfer manifest, and the stored headset audio entry before publishing it.
   */
  val podcast: SyncedPodcastTrack? = null,
  val index: Int? = null,
  val total: Int? = null,
  val playing: Boolean? = null,
  /** Values are populated only from an actual headset report, never Android media state. */
  val positionMs: Long? = null,
  val durationMs: Long? = null,
)

/**
 * Metadata lineage for a podcast track that was prepared for, then stored on, the headset.
 *
 * [episodeKey], [sourceRevision], and [audioVersion] identify the source enclosure. [sectionId] and
 * its time range identify the synced segment. [storage] identifies the MP3 stored on the headset.
 * UI must use [episodeTitle], [podcastTitle], and [artworkUri] rather than [storage].
 */
data class SyncedPodcastTrack(
  val episodeKey: String,
  val sourceRevision: String,
  val audioVersion: String,
  val episodeTitle: String,
  val podcastTitle: String,
  val artworkUri: String? = null,
  val sectionId: String,
  val sectionStartMs: Long,
  val sectionEndMs: Long,
  val storage: HeadsetAudioStorage,
) {
  init {
    require(episodeKey.isNotBlank() && sourceRevision.isNotBlank() && audioVersion.isNotBlank())
    require(episodeTitle.isNotBlank() && podcastTitle.isNotBlank() && sectionId.isNotBlank())
    require(sectionStartMs >= 0 && sectionEndMs > sectionStartMs)
  }
}

/** Storage-only identity. Its [fileName] is not presentation text. */
data class HeadsetAudioStorage(val trackId: UInt, val fileName: String) {
  init {
    require(fileName.isNotBlank())
  }
}

data class LocalAudioTrack(
  val podcast: SyncedPodcastTrack,
  val index: Int,
  val playing: Boolean,
) {
  val id: UInt
    get() = podcast.storage.trackId
}

sealed interface CompanionSyncStatus {
  data object Unavailable : CompanionSyncStatus

  data object Idle : CompanionSyncStatus

  data class Running(val sourceName: String?) : CompanionSyncStatus

  data class Complete(val detail: String) : CompanionSyncStatus

  data class Failed(val message: String) : CompanionSyncStatus
}

sealed interface HeadsetActionResult {
  data object Sent : HeadsetActionResult

  data class Unsupported(val command: RcspCommandId) : HeadsetActionResult

  data class Failed(val reason: String) : HeadsetActionResult
}

/** Safe default until the authenticated :rcsp OpenSwim adapter is installed. */
class UnavailableHeadsetGateway : HeadsetGateway {
  private val state = MutableStateFlow(HeadsetSnapshot())
  override val snapshot: StateFlow<HeadsetSnapshot> = state.asStateFlow()

  override suspend fun execute(action: HeadsetAction): HeadsetActionResult =
    HeadsetActionResult.Unsupported(action.command.id)
}

/**
 * Process-local bridge used by the companion activity and Tiles.
 *
 * It deliberately starts unavailable. The future authenticated adapter is the only code allowed to
 * replace it; Tiles never create transport objects or issue raw writes.
 */
object WearCompanionServices {
  @Volatile
  var headsetGateway: HeadsetGateway = UnavailableHeadsetGateway()
    private set

  @Synchronized
  fun gateway(
    context: android.content.Context
  ): ee.schimke.cadence.wear.datalayer.DataLayerHeadsetGateway {
    (headsetGateway as? ee.schimke.cadence.wear.datalayer.DataLayerHeadsetGateway)?.let {
      return it
    }
    return ee.schimke.cadence.wear.datalayer
      .DataLayerHeadsetGateway(context.applicationContext)
      .also { headsetGateway = it }
  }
}

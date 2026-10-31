package ee.schimke.cadence.wear.tiles

import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import ee.schimke.cadence.wear.CompanionSyncStatus
import ee.schimke.cadence.wear.HeadsetConnection
import ee.schimke.cadence.wear.WearCompanionServices
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.WorkingMode

/**
 * Tile content is deliberately status-only until the authenticated :rcsp OpenSwim adapter exists.
 * It never opens a Bluetooth connection or routes a tap through Android media controls.
 */
abstract class CadenceTileService : Material3TileService() {
  protected abstract fun title(): String

  protected abstract fun body(): String

  override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile =
    tile(
      timeline =
        Timeline.fromLayoutElement(
          primaryLayout(
            titleSlot = { text(title().layoutString, typography = TITLE_SMALL) },
            mainSlot = { text(body().layoutString, typography = BODY_MEDIUM) },
          )
        )
    )
}

class ModeAndControlsTileService : CadenceTileService() {
  override fun title(): String = "Headphone controls"

  override fun body(): String {
    val snapshot = WearCompanionServices.headsetGateway.snapshot.value
    if (snapshot.connection !is HeadsetConnection.Authenticated) {
      return "Disconnected. Pair a headset and connect it to this watch."
    }
    val controls =
      listOf(
          RcspCommand.PreviousTrack to "Previous",
          RcspCommand.Play to "Play",
          RcspCommand.Pause to "Pause",
          RcspCommand.NextTrack to "Next",
        )
        .filter { (command, _) -> snapshot.isSupported(command) }
        .joinToString { (_, label) -> label }
    return if (controls.isEmpty()) {
      "${snapshot.mode.label}. No verified remote controls for this firmware."
    } else {
      "${snapshot.mode.label}. Available in companion: $controls"
    }
  }
}

class LocalAudioTileService : CadenceTileService() {
  override fun title(): String = "Local audio"

  override fun body(): String {
    val snapshot = WearCompanionServices.headsetGateway.snapshot.value
    if (!snapshot.isSupported(RcspCommand.ListLocalAudio)) {
      return "Playlist is not supported by this headset firmware."
    }
    val playing = snapshot.localAudio.firstOrNull { it.playing }
    return when {
      playing != null ->
        "${playing.index} of ${snapshot.localAudio.size}: ${playing.podcast.episodeTitle}"
      snapshot.localAudio.isEmpty() -> "No playlist reported by the headset."
      else -> "${snapshot.localAudio.size} tracks available"
    }
  }
}

class SyncStatusTileService : CadenceTileService() {
  override fun title(): String = "Sync status"

  override fun body(): String =
    when (val sync = WearCompanionServices.headsetGateway.snapshot.value.sync) {
      CompanionSyncStatus.Unavailable -> "No sync status from the paired phone."
      CompanionSyncStatus.Idle -> "Up to date"
      is CompanionSyncStatus.Running -> sync.sourceName?.let { "Syncing $it" } ?: "Syncing"
      is CompanionSyncStatus.Complete -> sync.detail
      is CompanionSyncStatus.Failed -> "Sync failed: ${sync.message}"
    }
}

private val WorkingMode.label: String
  get() =
    when (this) {
      WorkingMode.Bluetooth -> "Bluetooth"
      WorkingMode.LocalAudio -> "Local Audio"
      WorkingMode.LineIn -> "Line In"
      WorkingMode.Usb -> "USB"
      WorkingMode.Unknown -> "Unknown"
    }

@file:Suppress("RestrictedApi")

package ee.schimke.cadence.wear.widgets

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.WearWidgetDocument
import androidx.glance.wear.color
import androidx.glance.wear.core.ContainerInfo
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.tooling.preview.RectangularAllWidgetPreviewParams
import androidx.glance.wear.tooling.preview.RoundAllWidgetPreviewParams
import androidx.glance.wear.tooling.preview.WearWidgetPreview
import androidx.wear.compose.remote.material3.RemoteButtonGroup
import androidx.wear.compose.remote.material3.RemoteIcon
import androidx.wear.compose.remote.material3.RemoteIconButton
import androidx.wear.compose.remote.material3.RemoteIconButtonDefaults
import androidx.wear.compose.remote.material3.RemoteMaterialTheme
import androidx.wear.compose.remote.material3.RemoteText
import ee.schimke.cadence.wear.CompanionSyncStatus
import ee.schimke.cadence.wear.HeadsetAction
import ee.schimke.cadence.wear.HeadsetAudioStorage
import ee.schimke.cadence.wear.HeadsetSnapshot
import ee.schimke.cadence.wear.LocalAudioTrack
import ee.schimke.cadence.wear.SyncedPodcastTrack
import ee.schimke.cadence.wear.WearCompanionServices
import ee.schimke.cadence.wear.preview.GalaxyWatch44Preview
import ee.schimke.cadence.wear.preview.PixelWatch45Preview
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.WorkingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A real Wear Widget, not a Tile: it is served by [GlanceWearWidgetService] and rendered by the
 * system from a Remote Compose document. The host owns its masking and safe insets.
 */
class ModeAndControlsWearWidget : GlanceWearWidget() {
  override suspend fun provideWidgetData(context: Context, params: WearWidgetParams) =
    WearWidgetDocument(background = WearWidgetBrush.color(WidgetColors.background.rc)) {
      RemoteMaterialTheme {
        ModeAndControlsWidgetContent(
          snapshot = WearCompanionServices.gateway(context).snapshot.value,
          compact = params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
        )
      }
    }
}

@AssociateWithGlanceWearWidget(ModeAndControlsWearWidget::class)
class ModeAndControlsWearWidgetService : GlanceWearWidgetService() {
  override val widget = ModeAndControlsWearWidget()
}

class LocalAudioPlaylistWearWidget : GlanceWearWidget() {
  override suspend fun provideWidgetData(context: Context, params: WearWidgetParams) =
    WearWidgetDocument(background = WearWidgetBrush.color(WidgetColors.background.rc)) {
      RemoteMaterialTheme {
        LocalAudioPlaylistWidgetContent(
          snapshot = WearCompanionServices.gateway(context).snapshot.value,
          itemLimit = if (params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL) 1 else 2,
        )
      }
    }
}

@AssociateWithGlanceWearWidget(LocalAudioPlaylistWearWidget::class)
class LocalAudioPlaylistWearWidgetService : GlanceWearWidgetService() {
  override val widget = LocalAudioPlaylistWearWidget()
}

class SyncStatusWearWidget : GlanceWearWidget() {
  override suspend fun provideWidgetData(context: Context, params: WearWidgetParams) =
    WearWidgetDocument(background = WearWidgetBrush.color(WidgetColors.background.rc)) {
      RemoteMaterialTheme {
        SyncStatusWidgetContent(
          snapshot = WearCompanionServices.gateway(context).snapshot.value,
          compact = params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
        )
      }
    }
}

@AssociateWithGlanceWearWidget(SyncStatusWearWidget::class)
class SyncStatusWearWidgetService : GlanceWearWidgetService() {
  override val widget = SyncStatusWearWidget()
}

@Composable
@RemoteComposable
private fun ModeAndControlsWidgetContent(snapshot: HeadsetSnapshot, compact: Boolean) {
  val playing = snapshot.media?.playing == true
  // Both candidates are source-to-storage records. Do not substitute an MP3 filename or Android
  // media-session metadata here: neither identifies the episode synced onto OpenSwim Pro.
  val currentTrack =
    snapshot.media?.podcast ?: snapshot.localAudio.firstOrNull { it.playing }?.podcast
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize(),
    verticalArrangement = RemoteArrangement.SpaceBetween,
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    RemoteText(
      text = "OpenSwim Pro · ${snapshot.mode.widgetLabel}".rs,
      style = RemoteMaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    RemoteText(
      text = (currentTrack?.episodeTitle ?: "No current track reported").rs,
      style =
        if (compact) RemoteMaterialTheme.typography.bodySmall
        else RemoteMaterialTheme.typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (!compact) {
      RemoteText(
        text = (currentTrack?.podcastTitle ?: snapshot.connection.widgetStatus).rs,
        style = RemoteMaterialTheme.typography.bodyExtraSmall,
        color = WidgetColors.supporting.rc,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    RemoteButtonGroup {
      RemoteIconButton(
        onClick = widgetAction(WidgetAction.Previous),
        modifier = RemoteModifier.size(48.rdp),
        enabled = snapshot.isSupported(RcspCommand.PreviousTrack).rb,
        colors = RemoteIconButtonDefaults.iconButtonColors(containerColor = WidgetColors.tonal.rc),
      ) {
        RemoteIcon(Icons.Filled.SkipPrevious, "Previous".rs)
      }
      RemoteIconButton(
        onClick = widgetAction(if (playing) WidgetAction.Pause else WidgetAction.Play),
        modifier = RemoteModifier.size(48.rdp),
        enabled = snapshot.isSupported(if (playing) RcspCommand.Pause else RcspCommand.Play).rb,
        colors =
          RemoteIconButtonDefaults.iconButtonColors(containerColor = WidgetColors.primary.rc),
      ) {
        if (playing) {
          RemoteIcon(Icons.Filled.Pause, "Pause".rs)
        } else {
          RemoteIcon(Icons.Filled.PlayArrow, "Play".rs)
        }
      }
      RemoteIconButton(
        onClick = widgetAction(WidgetAction.Next),
        modifier = RemoteModifier.size(48.rdp),
        enabled = snapshot.isSupported(RcspCommand.NextTrack).rb,
        colors = RemoteIconButtonDefaults.iconButtonColors(containerColor = WidgetColors.tonal.rc),
      ) {
        RemoteIcon(Icons.Filled.SkipNext, "Next".rs)
      }
    }
    if (!compact) {
      val nextMode =
        if (snapshot.mode == WorkingMode.LocalAudio) WorkingMode.Bluetooth
        else WorkingMode.LocalAudio
      RemoteButtonGroup {
        RemoteIconButton(
          onClick = widgetAction(WidgetAction.ToggleMode),
          modifier = RemoteModifier.size(40.rdp),
          enabled = snapshot.isSupported(RcspCommand.SelectWorkingMode(nextMode)).rb,
          colors =
            RemoteIconButtonDefaults.iconButtonColors(containerColor = WidgetColors.tonal.rc),
        ) {
          if (nextMode == WorkingMode.Bluetooth) {
            RemoteIcon(Icons.Filled.Bluetooth, "Switch to Bluetooth".rs)
          } else {
            RemoteIcon(Icons.Filled.MusicNote, "Switch to MP3".rs)
          }
        }
        RemoteIconButton(
          onClick = settingsAction(),
          modifier = RemoteModifier.size(40.rdp),
          colors =
            RemoteIconButtonDefaults.iconButtonColors(containerColor = WidgetColors.tonal.rc),
        ) {
          RemoteIcon(Icons.Filled.Settings, "More settings".rs)
        }
      }
    }
  }
}

@Composable
@RemoteComposable
private fun LocalAudioPlaylistWidgetContent(snapshot: HeadsetSnapshot, itemLimit: Int) {
  val tracks = snapshot.localAudio.take(itemLimit)
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize(),
    verticalArrangement = RemoteArrangement.spacedBy(4.rdp),
  ) {
    RemoteText("MP3 playlist".rs, style = RemoteMaterialTheme.typography.titleSmall, maxLines = 1)
    if (tracks.isEmpty()) {
      RemoteText(
        text = "No headset playlist reported".rs,
        style = RemoteMaterialTheme.typography.bodySmall,
        color = WidgetColors.supporting.rc,
        maxLines = 2,
      )
    } else {
      tracks.forEach { track ->
        RemoteText(
          text = "${if (track.playing) "Playing · " else ""}${track.podcast.episodeTitle}".rs,
          style = RemoteMaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        RemoteText(
          text = track.podcast.podcastTitle.rs,
          style = RemoteMaterialTheme.typography.bodyExtraSmall,
          color = WidgetColors.supporting.rc,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
@RemoteComposable
private fun SyncStatusWidgetContent(snapshot: HeadsetSnapshot, compact: Boolean) {
  val sync = snapshot.sync
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize(),
    verticalArrangement = RemoteArrangement.SpaceBetween,
  ) {
    RemoteText("Podcast sync".rs, style = RemoteMaterialTheme.typography.titleSmall, maxLines = 1)
    RemoteText(
      text = sync.widgetLabel.rs,
      style =
        if (compact) RemoteMaterialTheme.typography.bodyMedium
        else RemoteMaterialTheme.typography.titleMedium,
      maxLines = if (compact) 2 else 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (sync is CompanionSyncStatus.Running && !compact) {
      RemoteText(
        text = (sync.progressLabel ?: snapshot.connection.widgetStatus).rs,
        style = RemoteMaterialTheme.typography.bodyExtraSmall,
        color = WidgetColors.supporting.rc,
        maxLines = 1,
      )
    }
  }
}

/** Broadcast actions make widgets independently operable without creating a headset transport. */
class WearWidgetActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.getStringExtra(ExtraAction)?.let(WidgetAction::fromWire) ?: return
    val pendingResult = goAsync()
    widgetScope.launch {
      try {
        val gateway = WearCompanionServices.gateway(context)
        gateway.execute(
          if (action == WidgetAction.ToggleMode) {
            HeadsetAction.SelectMode(
              if (gateway.snapshot.value.mode == WorkingMode.LocalAudio) WorkingMode.Bluetooth
              else WorkingMode.LocalAudio
            )
          } else {
            action.toHeadsetAction()
          }
        )
      } finally {
        WearWidgets.updateAll(context.applicationContext)
        pendingResult.finish()
      }
    }
  }
}

/** Refreshes every active instance after Data Layer state or a widget command changes. */
object WearWidgets {
  fun updateAll(context: Context) {
    widgetScope.launch {
      ModeAndControlsWearWidget().triggerUpdateAll(context)
      LocalAudioPlaylistWearWidget().triggerUpdateAll(context)
      SyncStatusWearWidget().triggerUpdateAll(context)
    }
  }
}

@Composable
@RemoteComposable
private fun widgetAction(action: WidgetAction) = pendingIntentAction { context ->
  PendingIntent.getBroadcast(
    context,
    action.ordinal,
    Intent(context, WearWidgetActionReceiver::class.java).putExtra(ExtraAction, action.wireName),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

@Composable
@RemoteComposable
private fun settingsAction() = pendingIntentAction { context ->
  PendingIntent.getActivity(
    context,
    SettingsRequestCode,
    Intent(context, ee.schimke.cadence.wear.WearMainActivity::class.java)
      .putExtra(ee.schimke.cadence.wear.WearMainActivity.ExtraOpenSettings, true)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

private enum class WidgetAction(val wireName: String) {
  Previous("previous"),
  Play("play"),
  Pause("pause"),
  Next("next"),
  ToggleMode("toggle_mode");

  fun toHeadsetAction() =
    when (this) {
      Previous -> HeadsetAction.PreviousTrack
      Play -> HeadsetAction.Play
      Pause -> HeadsetAction.Pause
      Next -> HeadsetAction.NextTrack
      ToggleMode -> error("Mode uses the current gateway snapshot")
    }

  companion object {
    fun fromWire(value: String): WidgetAction? = entries.firstOrNull { it.wireName == value }
  }
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private const val ExtraAction = "ee.schimke.cadence.wear.widgets.ACTION"
private const val SettingsRequestCode = 100

private object WidgetColors {
  val background = Color(0xFF17161E)
  val supporting = Color(0xFFCBC3D7)
  val tonal = Color(0xFF343044)
  val primary = Color(0xFFD7BFFF)
}

private val WorkingMode.widgetLabel: String
  get() =
    when (this) {
      WorkingMode.Bluetooth -> "Bluetooth"
      WorkingMode.LocalAudio -> "MP3"
      WorkingMode.LineIn -> "Line in"
      WorkingMode.Usb -> "USB"
      WorkingMode.Unknown -> "Mode unavailable"
    }

private val ee.schimke.cadence.wear.HeadsetConnection.widgetStatus: String
  get() =
    when (this) {
      ee.schimke.cadence.wear.HeadsetConnection.Disconnected -> "Disconnected"
      ee.schimke.cadence.wear.HeadsetConnection.Connecting -> "Connecting"
      is ee.schimke.cadence.wear.HeadsetConnection.Authenticated -> deviceName
      is ee.schimke.cadence.wear.HeadsetConnection.PhoneConnected -> "$deviceName via phone"
      is ee.schimke.cadence.wear.HeadsetConnection.Unavailable -> reason
    }

private val CompanionSyncStatus.widgetLabel: String
  get() =
    when (this) {
      CompanionSyncStatus.Unavailable -> "Waiting for phone"
      CompanionSyncStatus.Idle -> "Up to date"
      is CompanionSyncStatus.Running -> sourceName?.let { "Syncing $it" } ?: "Syncing"
      is CompanionSyncStatus.Complete -> detail
      is CompanionSyncStatus.Failed -> "Sync failed: $message"
    }

@PixelWatch45Preview
@GalaxyWatch44Preview
@Composable
fun ModeAndControlsWearWidgetPreview(
  @PreviewParameter(RoundAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      ModeAndControlsWidgetContent(
        previewSnapshot,
        params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
      )
    }
  }
}

@PixelWatch45Preview
@GalaxyWatch44Preview
@Composable
fun LocalAudioPlaylistWearWidgetPreview(
  @PreviewParameter(RoundAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      LocalAudioPlaylistWidgetContent(
        previewSnapshot,
        if (params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL) 1 else 2,
      )
    }
  }
}

@PixelWatch45Preview
@GalaxyWatch44Preview
@Composable
fun SyncStatusWearWidgetPreview(
  @PreviewParameter(RoundAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      SyncStatusWidgetContent(
        previewSnapshot,
        params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
      )
    }
  }
}

@Preview(name = "Mode controls · picker asset", device = "spec:width=1000dp,height=1000dp,dpi=320")
@Composable
fun ModeAndControlsWearWidgetPickerPreview(
  @PreviewParameter(RectangularAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      ModeAndControlsWidgetContent(
        previewSnapshot,
        params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
      )
    }
  }
}

@Preview(name = "MP3 playlist · picker asset", device = "spec:width=1000dp,height=1000dp,dpi=320")
@Composable
fun LocalAudioPlaylistWearWidgetPickerPreview(
  @PreviewParameter(RectangularAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      LocalAudioPlaylistWidgetContent(
        previewSnapshot,
        if (params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL) 1 else 2,
      )
    }
  }
}

@Preview(name = "Sync status · picker asset", device = "spec:width=1000dp,height=1000dp,dpi=320")
@Composable
fun SyncStatusWearWidgetPickerPreview(
  @PreviewParameter(RectangularAllWidgetPreviewParams::class) params: WearWidgetParams
) {
  WearWidgetPreview(params, background = WearWidgetBrush.color(WidgetColors.background.rc)) {
    RemoteMaterialTheme {
      SyncStatusWidgetContent(
        previewSnapshot,
        params.containerType == ContainerInfo.CONTAINER_TYPE_SMALL,
      )
    }
  }
}

private val previewSnapshot =
  HeadsetSnapshot(
    connection = ee.schimke.cadence.wear.HeadsetConnection.Authenticated("OpenSwim Pro"),
    mode = WorkingMode.LocalAudio,
    localAudio =
      listOf(
        LocalAudioTrack(
          podcast =
            SyncedPodcastTrack(
              episodeKey = "morning-swim",
              sourceRevision = "rev-1",
              audioVersion = "mp3-1",
              episodeTitle = "Morning Swim",
              podcastTitle = "Saltwater Daily",
              sectionId = "whole-episode",
              sectionStartMs = 0,
              sectionEndMs = 1_800_000,
              storage = HeadsetAudioStorage(trackId = 1u, fileName = "storage-only.mp3"),
            ),
          index = 1,
          playing = true,
        ),
        LocalAudioTrack(
          podcast =
            SyncedPodcastTrack(
              episodeKey = "tide-pool",
              sourceRevision = "rev-2",
              audioVersion = "mp3-2",
              episodeTitle = "Tide Pool Notes",
              podcastTitle = "Saltwater Daily",
              sectionId = "whole-episode",
              sectionStartMs = 0,
              sectionEndMs = 1_800_000,
              storage = HeadsetAudioStorage(trackId = 2u, fileName = "storage-only-2.mp3"),
            ),
          index = 2,
          playing = false,
        ),
      ),
    sync = CompanionSyncStatus.Running("Morning Swim", filesCompleted = 2, filesTotal = 4),
  )

@file:Suppress("RestrictedApi")

package ee.schimke.cadence.wear.widgets

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import androidx.wear.compose.remote.material3.RemoteButton
import androidx.wear.compose.remote.material3.RemoteButtonDefaults
import androidx.wear.compose.remote.material3.RemoteCircularProgressIndicator
import androidx.wear.compose.remote.material3.RemoteIcon
import androidx.wear.compose.remote.material3.RemoteIconButton
import androidx.wear.compose.remote.material3.RemoteIconButtonDefaults
import androidx.wear.compose.remote.material3.RemoteMaterialTheme
import androidx.wear.compose.remote.material3.RemoteProgressIndicatorDefaults
import androidx.wear.compose.remote.material3.RemoteText
import ee.schimke.cadence.wear.CompanionSyncStatus
import ee.schimke.cadence.wear.HeadsetAction
import ee.schimke.cadence.wear.HeadsetAudioStorage
import ee.schimke.cadence.wear.HeadsetMedia
import ee.schimke.cadence.wear.HeadsetSnapshot
import ee.schimke.cadence.wear.LocalAudioTrack
import ee.schimke.cadence.wear.SyncedPodcastTrack
import ee.schimke.cadence.wear.WearCompanionServices
import ee.schimke.cadence.wear.preview.GalaxyWatch44Preview
import ee.schimke.cadence.wear.preview.PixelWatch45Preview
import ee.schimke.rcsp.RcspCapabilities
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

/**
 * Now playing: the current episode and transport controls, nothing else. Large adds the podcast
 * name. Mode switching and settings live in the app, not on the widget.
 */
@Composable
@RemoteComposable
private fun ModeAndControlsWidgetContent(snapshot: HeadsetSnapshot, compact: Boolean) {
  val colors = RemoteMaterialTheme.colorScheme
  val playing = snapshot.media?.playing == true
  // Both candidates are source-to-storage records. Do not substitute an MP3 filename or Android
  // media-session metadata here: neither identifies the episode synced onto OpenSwim Pro.
  val currentTrack =
    snapshot.media?.podcast ?: snapshot.localAudio.firstOrNull { it.playing }?.podcast
  val episode = currentTrack?.episodeTitle ?: "Nothing playing"
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxSize().semantics { contentDescription = "Now playing, $episode".rs },
    verticalArrangement =
      RemoteArrangement.spacedBy(if (compact) 6.rdp else 10.rdp, RemoteAlignment.CenterVertically),
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
      RemoteText(
        text = episode.rs,
        style =
          if (compact) RemoteMaterialTheme.typography.titleSmall
          else RemoteMaterialTheme.typography.titleMedium,
        color = if (currentTrack == null) colors.onSurfaceVariant else colors.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!compact && currentTrack != null) {
        RemoteText(
          text = currentTrack.podcastTitle.rs,
          style = RemoteMaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    RemoteRow(
      horizontalArrangement = RemoteArrangement.spacedBy(8.rdp),
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      TransportButton(
        action = widgetAction(WidgetAction.Previous),
        icon = Icons.Filled.SkipPrevious,
        label = "Previous",
        enabled = snapshot.isSupported(RcspCommand.PreviousTrack),
        primary = false,
        compact = compact,
      )
      TransportButton(
        action = widgetAction(if (playing) WidgetAction.Pause else WidgetAction.Play),
        icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        label = if (playing) "Pause" else "Play",
        enabled = snapshot.isSupported(if (playing) RcspCommand.Pause else RcspCommand.Play),
        primary = true,
        compact = compact,
      )
      TransportButton(
        action = widgetAction(WidgetAction.Next),
        icon = Icons.Filled.SkipNext,
        label = "Next",
        enabled = snapshot.isSupported(RcspCommand.NextTrack),
        primary = false,
        compact = compact,
      )
    }
  }
}

@Composable
@RemoteComposable
private fun TransportButton(
  action: Action,
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  primary: Boolean,
  compact: Boolean,
) {
  val colors = RemoteMaterialTheme.colorScheme
  val size =
    when {
      primary && compact -> 52.rdp
      primary -> 60.rdp
      compact -> 44.rdp
      else -> 48.rdp
    }
  RemoteIconButton(
    onClick = action,
    modifier = RemoteModifier.size(size),
    enabled = enabled.rb,
    colors =
      if (primary) {
        RemoteIconButtonDefaults.iconButtonColors(
          containerColor = colors.primary,
          contentColor = colors.onPrimary,
        )
      } else {
        RemoteIconButtonDefaults.iconButtonColors(
          containerColor = colors.surfaceContainerHigh,
          contentColor = colors.onSurface,
        )
      },
  ) {
    RemoteIcon(icon, label.rs)
  }
}

/**
 * The headset MP3 playlist as play buttons: tapping an episode starts it on the headset. Episodes
 * are named from their source/sync record, never from the storage filename.
 */
@Composable
@RemoteComposable
private fun LocalAudioPlaylistWidgetContent(snapshot: HeadsetSnapshot, itemLimit: Int) {
  val colors = RemoteMaterialTheme.colorScheme
  val tracks = snapshot.localAudio.take(itemLimit)
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize(),
    verticalArrangement = RemoteArrangement.spacedBy(6.rdp, RemoteAlignment.CenterVertically),
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    if (tracks.isEmpty()) {
      RemoteText(
        text = "No episodes on your headphones".rs,
        style = RemoteMaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    } else {
      tracks.forEach { track ->
        PlaylistButton(track, enabled = snapshot.isSupported(RcspCommand.PlayLocalAudio(track.id)))
      }
    }
  }
}

@Composable
@RemoteComposable
private fun PlaylistButton(track: LocalAudioTrack, enabled: Boolean) {
  val colors = RemoteMaterialTheme.colorScheme
  val content = if (track.playing) colors.onPrimaryContainer else colors.onSurface
  RemoteButton(
    onClick = playTrackAction(track.id),
    modifier =
      RemoteModifier.fillMaxWidth().semantics {
        contentDescription =
          "${if (track.playing) "Playing" else "Play"} ${track.podcast.episodeTitle}".rs
      },
    enabled = enabled.rb,
    colors =
      RemoteButtonDefaults.buttonColors(
        containerColor = if (track.playing) colors.primaryContainer else colors.surfaceContainer,
        contentColor = content,
        iconColor = content,
      ),
  ) {
    RemoteIcon(
      if (track.playing) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
      null,
      modifier = RemoteModifier.size(RemoteButtonDefaults.SmallIconSize),
    )
    RemoteText(
      text = track.podcast.episodeTitle.rs,
      modifier = RemoteModifier.padding(start = 8.rdp).weight(1f),
      style = RemoteMaterialTheme.typography.labelMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Podcast sync from the phone: a progress ring and one status line. Large adds a single detail line
 * (file count or connection).
 */
@Composable
@RemoteComposable
private fun SyncStatusWidgetContent(snapshot: HeadsetSnapshot, compact: Boolean) {
  val sync = snapshot.sync
  val colors = RemoteMaterialTheme.colorScheme
  val detail =
    when (sync) {
      is CompanionSyncStatus.Running -> sync.progressLabel
      CompanionSyncStatus.Unavailable -> "Open Cadence on your phone"
      else -> null
    }
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxSize().semantics {
        contentDescription = "Podcast sync, ${sync.widgetLabel}".rs
      },
    verticalArrangement = RemoteArrangement.spacedBy(6.rdp, RemoteAlignment.CenterVertically),
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    if (compact) {
      RemoteRow(verticalAlignment = RemoteAlignment.CenterVertically) {
        SyncRing(sync, compact = true)
        RemoteText(
          text = sync.widgetLabel.rs,
          modifier = RemoteModifier.padding(start = 10.rdp),
          style = RemoteMaterialTheme.typography.titleSmall,
          color = colors.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    } else {
      SyncRing(sync, compact = false)
      RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
        RemoteText(
          text = sync.widgetLabel.rs,
          style = RemoteMaterialTheme.typography.titleSmall,
          color = colors.onSurface,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
          RemoteText(
            text = detail.rs,
            style = RemoteMaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
@RemoteComposable
private fun SyncRing(sync: CompanionSyncStatus, compact: Boolean) {
  val colors = RemoteMaterialTheme.colorScheme
  val accent = sync.accent()
  RemoteBox(
    modifier = RemoteModifier.size(if (compact) 48.rdp else 72.rdp),
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteCircularProgressIndicator(
      progress = sync.progressFraction.rf,
      modifier = RemoteModifier.fillMaxSize(),
      colors =
        RemoteProgressIndicatorDefaults.colors(
          indicatorColor = accent,
          trackColor = colors.surfaceContainerHigh,
        ),
      strokeWidth = if (compact) 4.rdp else 6.rdp,
    )
    RemoteIcon(
      sync.widgetIcon,
      null,
      modifier = RemoteModifier.size(if (compact) 22.rdp else 32.rdp),
      tint = accent,
    )
  }
}

@Composable
@RemoteComposable
private fun CompanionSyncStatus.accent(): RemoteColor {
  val colors = RemoteMaterialTheme.colorScheme
  return when (this) {
    is CompanionSyncStatus.Failed -> colors.error
    CompanionSyncStatus.Unavailable -> colors.onSurfaceVariant
    is CompanionSyncStatus.Running -> colors.primary
    CompanionSyncStatus.Idle,
    is CompanionSyncStatus.Complete -> colors.tertiary
  }
}

/** Broadcast actions make widgets independently operable without creating a headset transport. */
class WearWidgetActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.getStringExtra(ExtraAction)?.let(WidgetAction::fromWire) ?: return
    val trackId = intent.getLongExtra(ExtraTrackId, -1L)
    if (action == WidgetAction.PlayTrack && trackId !in 0L..UInt.MAX_VALUE.toLong()) return
    val pendingResult = goAsync()
    widgetScope.launch {
      try {
        val gateway = WearCompanionServices.gateway(context)
        gateway.execute(
          when (action) {
            WidgetAction.Previous -> HeadsetAction.PreviousTrack
            WidgetAction.Play -> HeadsetAction.Play
            WidgetAction.Pause -> HeadsetAction.Pause
            WidgetAction.Next -> HeadsetAction.NextTrack
            WidgetAction.PlayTrack -> HeadsetAction.PlayLocalAudio(trackId.toUInt())
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
private fun playTrackAction(trackId: UInt) = pendingIntentAction { context ->
  PendingIntent.getBroadcast(
    context,
    // One PendingIntent per track: request codes must differ or extras collapse onto one intent.
    PlayTrackRequestCodeBase + (trackId.toLong() % PlayTrackRequestCodeSpan).toInt(),
    Intent(context, WearWidgetActionReceiver::class.java)
      .putExtra(ExtraAction, WidgetAction.PlayTrack.wireName)
      .putExtra(ExtraTrackId, trackId.toLong()),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

private enum class WidgetAction(val wireName: String) {
  Previous("previous"),
  Play("play"),
  Pause("pause"),
  Next("next"),
  PlayTrack("play_track");

  companion object {
    fun fromWire(value: String): WidgetAction? = entries.firstOrNull { it.wireName == value }
  }
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private const val ExtraAction = "ee.schimke.cadence.wear.widgets.ACTION"
private const val ExtraTrackId = "ee.schimke.cadence.wear.widgets.TRACK_ID"
private const val PlayTrackRequestCodeBase = 1_000
private const val PlayTrackRequestCodeSpan = 100_000L

/** Matches the Wear Material 3 dark surface the widget content is themed against. */
private object WidgetColors {
  val background = Color(0xFF0E0E11)
}

private val CompanionSyncStatus.widgetLabel: String
  get() =
    when (this) {
      CompanionSyncStatus.Unavailable -> "Waiting for phone"
      CompanionSyncStatus.Idle -> "Up to date"
      is CompanionSyncStatus.Running -> sourceName?.let { "Syncing $it" } ?: "Syncing"
      is CompanionSyncStatus.Complete -> detail
      is CompanionSyncStatus.Failed -> "Sync failed"
    }

private val CompanionSyncStatus.widgetIcon: ImageVector
  get() =
    when (this) {
      CompanionSyncStatus.Unavailable -> Icons.Filled.PhonelinkErase
      CompanionSyncStatus.Idle,
      is CompanionSyncStatus.Complete -> Icons.Filled.Check
      is CompanionSyncStatus.Running -> Icons.Filled.Sync
      is CompanionSyncStatus.Failed -> Icons.Filled.PriorityHigh
    }

/** Ring fill: transfer progress while running, full once done, empty otherwise. */
private val CompanionSyncStatus.progressFraction: Float
  get() =
    when (this) {
      is CompanionSyncStatus.Running ->
        if (filesTotal > 0) (filesCompleted.toFloat() / filesTotal).coerceIn(0f, 1f) else 0f
      CompanionSyncStatus.Idle,
      is CompanionSyncStatus.Complete -> 1f
      is CompanionSyncStatus.Failed -> 1f
      CompanionSyncStatus.Unavailable -> 0f
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
    media = HeadsetMedia(playing = true),
    capabilities =
      RcspCapabilities()
        .withSupported(
          RcspCommand.Play.id,
          RcspCommand.Pause.id,
          RcspCommand.NextTrack.id,
          RcspCommand.PreviousTrack.id,
          RcspCommand.PlayLocalAudio(1u).id,
        ),
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
              episodeTitle = "Tide Pool Notes: Reading the Shoreline at Dawn",
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

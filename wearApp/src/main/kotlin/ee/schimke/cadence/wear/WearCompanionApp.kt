package ee.schimke.cadence.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.ui.material3.components.MediaControlButtons
import com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen
import ee.schimke.rcsp.EqualizerPreset
import ee.schimke.rcsp.RcspCapabilities
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.WorkingMode
import kotlinx.coroutines.launch

@Composable
fun WearCompanionApp(gateway: HeadsetGateway, modifier: Modifier = Modifier) {
  val snapshot by gateway.snapshot.collectAsState()
  val scope = rememberCoroutineScope()
  MaterialTheme {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      WearCompanionContent(
        snapshot = snapshot,
        onAction = { action ->
          if (gateway.isSupported(action.command)) {
            scope.launch { gateway.execute(action) }
          }
        },
      )
    }
  }
}

@Composable
fun WearCompanionContent(
  snapshot: HeadsetSnapshot,
  modifier: Modifier = Modifier,
  onAction: (HeadsetAction) -> Unit = {},
) {
  val pagerState = rememberPagerState(pageCount = { 3 })
  AppScaffold(modifier = modifier.fillMaxSize()) {
    HorizontalPagerScaffold(pagerState = pagerState, modifier = Modifier.fillMaxSize()) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
          0 ->
            HorologistHeadsetPlayerScreen(
              snapshot = snapshot,
              modifier = Modifier.fillMaxSize(),
              onAction = onAction,
            )
          1 ->
            LocalAudioPlaylistScreen(
              snapshot = snapshot,
              modifier = Modifier.fillMaxSize(),
              onAction = onAction,
            )
          else ->
            DeviceSettingsScreen(
              snapshot = snapshot,
              modifier = Modifier.fillMaxSize(),
              onAction = onAction,
            )
        }
      }
    }
  }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun HorologistHeadsetPlayerScreen(
  snapshot: HeadsetSnapshot,
  modifier: Modifier,
  onAction: (HeadsetAction) -> Unit,
) {
  val media = snapshot.media
  val playing = media?.playing == true
  val mediaSubtitle =
    listOfNotNull(
        media?.podcast?.podcastTitle,
        media?.index?.let { index -> media.total?.let { total -> "$index of $total" } },
      )
      .joinToString(" · ")
  PlayerScreen(
    mediaDisplay = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = media?.podcast?.episodeTitle ?: "No episode metadata",
          style = MaterialTheme.typography.titleLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
        if (mediaSubtitle.isNotBlank()) {
          Text(
            text = mediaSubtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    controlButtons = {
      MediaControlButtons(
        onPlayButtonClick = { onAction(HeadsetAction.Play) },
        onPauseButtonClick = { onAction(HeadsetAction.Pause) },
        playPauseButtonEnabled =
          if (playing) {
            snapshot.isSupported(RcspCommand.Pause)
          } else {
            snapshot.isSupported(RcspCommand.Play)
          },
        playing = playing,
        onSeekToPreviousButtonClick = { onAction(HeadsetAction.PreviousTrack) },
        seekToPreviousButtonEnabled = snapshot.isSupported(RcspCommand.PreviousTrack),
        onSeekToNextButtonClick = { onAction(HeadsetAction.NextTrack) },
        seekToNextButtonEnabled = snapshot.isSupported(RcspCommand.NextTrack),
      )
    },
    buttons = {},
    modifier = modifier,
    background = {
      Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    },
  )
}

@Composable
private fun LocalAudioPlaylistScreen(
  snapshot: HeadsetSnapshot,
  modifier: Modifier = Modifier,
  onAction: (HeadsetAction) -> Unit,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()
  ScreenScaffold(scrollState = columnState, modifier = modifier) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        ListHeader(
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = "MP3 playlist")
        }
      }
      if (snapshot.localAudio.isEmpty()) {
        item {
          ListHeader(
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            transformation = SurfaceTransformation(transformationSpec),
          ) {
            Text(text = "No synced podcast tracks")
          }
        }
      } else {
        items(snapshot.localAudio, key = { it.id.toString() }) { track ->
          PlaylistRow(
            track = track,
            enabled = snapshot.isSupported(RcspCommand.PlayLocalAudio(track.id)),
            onClick = { onAction(HeadsetAction.PlayLocalAudio(track.id)) },
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            transformation = SurfaceTransformation(transformationSpec),
          )
        }
      }
    }
  }
}

@Composable
private fun PlaylistRow(
  track: LocalAudioTrack,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  transformation: SurfaceTransformation? = null,
) {
  Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    transformation = transformation,
    secondaryLabel = {
      Text(text = track.podcast.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    label = {
      Text(
        text = track.podcast.episodeTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
  )
}

@Composable
private fun DeviceSettingsScreen(
  snapshot: HeadsetSnapshot,
  modifier: Modifier = Modifier,
  onAction: (HeadsetAction) -> Unit,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()
  ScreenScaffold(scrollState = columnState, modifier = modifier) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        ListHeader(
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = "Device settings")
        }
      }
      item {
        ListHeader(
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = "Mode")
        }
      }
      items(WorkingMode.entries.filter { it != WorkingMode.Unknown }) { mode ->
        SettingsRow(
          text = mode.label,
          selected = snapshot.mode == mode,
          enabled = snapshot.isSupported(RcspCommand.SelectWorkingMode(mode)),
          onClick = { onAction(HeadsetAction.SelectMode(mode)) },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          transformation = SurfaceTransformation(transformationSpec),
        )
      }
      item {
        ListHeader(
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = "Equalizer")
        }
      }
      items(EqualizerPreset.entries) { preset ->
        SettingsRow(
          text = preset.label,
          enabled = snapshot.isSupported(RcspCommand.SelectEqualizerPreset(preset)),
          onClick = { onAction(HeadsetAction.SelectEqualizerPreset(preset)) },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          transformation = SurfaceTransformation(transformationSpec),
        )
      }
      item {
        ListHeader(
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = syncStatus(snapshot.sync))
        }
      }
    }
  }
}

@Composable
private fun SettingsRow(
  text: String,
  selected: Boolean = false,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  transformation: SurfaceTransformation? = null,
) {
  Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    transformation = transformation,
    label = { Text(text = if (selected) "$text · selected" else text) },
  )
}

private val EqualizerPreset.label: String
  get() =
    when (this) {
      EqualizerPreset.Standard -> "Standard"
      EqualizerPreset.Vocal -> "Vocal"
      EqualizerPreset.BassBoost -> "Bass boost"
      EqualizerPreset.TrebleBoost -> "Treble boost"
      EqualizerPreset.Swimming -> "Swimming"
      EqualizerPreset.Custom -> "Custom"
    }

private fun syncStatus(sync: CompanionSyncStatus): String =
  when (sync) {
    CompanionSyncStatus.Unavailable -> "Sync unavailable"
    CompanionSyncStatus.Idle -> "Sync idle"
    is CompanionSyncStatus.Running -> "Syncing ${sync.sourceName ?: "podcasts"}"
    is CompanionSyncStatus.Complete -> sync.detail
    is CompanionSyncStatus.Failed -> "Sync failed: ${sync.message}"
  }

private fun connectionStatus(connection: HeadsetConnection): String =
  when (connection) {
    HeadsetConnection.Disconnected -> "Disconnected"
    HeadsetConnection.Connecting -> "Connecting"
    is HeadsetConnection.Authenticated -> "Connected"
    is HeadsetConnection.PhoneConnected -> "Connected via phone"
    is HeadsetConnection.Unavailable -> "Unavailable"
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

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun ConnectedWearCompanionPreview() {
  MaterialTheme {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      WearCompanionContent(
        snapshot =
          HeadsetSnapshot(
            connection = HeadsetConnection.Authenticated("OpenSwim Pro"),
            mode = WorkingMode.LocalAudio,
            media =
              HeadsetMedia(
                podcast = previewPodcastTrack(trackId = 2u, episodeTitle = "Morning Swim"),
                index = 2,
                total = 4,
                playing = true,
              ),
            localAudio =
              listOf(
                LocalAudioTrack(previewPodcastTrack(1u, "Warm up"), 1, false),
                LocalAudioTrack(previewPodcastTrack(2u, "Morning Swim"), 2, true),
              ),
            capabilities =
              RcspCapabilities()
                .withSupported(
                  RcspCommand.PreviousTrack.id,
                  RcspCommand.Pause.id,
                  RcspCommand.NextTrack.id,
                  RcspCommand.ListLocalAudio.id,
                  RcspCommand.PlayLocalAudio(1u).id,
                  RcspCommand.SelectWorkingMode(WorkingMode.LocalAudio).id,
                  RcspCommand.SelectEqualizerPreset(EqualizerPreset.Standard).id,
                ),
            sync = CompanionSyncStatus.Complete("Last synced 4 tracks"),
          )
      )
    }
  }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun LocalAudioPlaylistPreview() {
  MaterialTheme {
    LocalAudioPlaylistScreen(
      snapshot =
        HeadsetSnapshot(
          localAudio =
            listOf(
              LocalAudioTrack(previewPodcastTrack(1u, "Warm up"), 1, false),
              LocalAudioTrack(previewPodcastTrack(2u, "Morning Swim"), 2, true),
            ),
          capabilities = RcspCapabilities().withSupported(RcspCommand.PlayLocalAudio(1u).id),
        ),
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      onAction = {},
    )
  }
}

private fun previewPodcastTrack(trackId: UInt, episodeTitle: String) =
  SyncedPodcastTrack(
    episodeKey = "preview-episode-$trackId",
    sourceRevision = "preview-revision",
    audioVersion = "preview-audio",
    episodeTitle = episodeTitle,
    podcastTitle = "Saltwater Daily",
    sectionId = "preview-section-$trackId",
    sectionStartMs = 0,
    sectionEndMs = 3_600_000,
    storage = HeadsetAudioStorage(trackId, "storage-only-$trackId.mp3"),
  )

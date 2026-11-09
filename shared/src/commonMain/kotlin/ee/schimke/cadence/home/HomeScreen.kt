package ee.schimke.cadence.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.bluetooth.MediaInfo
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.devices.rememberFileExplorerOpenLauncher
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.podcasts.CloudSyncHomeCard
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.EpisodeDetails
import ee.schimke.cadence.podcasts.KeepPodcastScreenAwake
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.POCKET_CASTS_UP_NEXT
import ee.schimke.cadence.podcasts.PodcastController
import ee.schimke.cadence.podcasts.PodcastPlayerUi
import ee.schimke.cadence.podcasts.PodcastSyncHomeCard
import ee.schimke.cadence.podcasts.PodcastWorkUi
import ee.schimke.cadence.podcasts.cloudSyncStatus
import ee.schimke.cadence.podcasts.episodePosition
import ee.schimke.cadence.podcasts.formatTime
import ee.schimke.cadence.podcasts.loadEpisodeDetails
import ee.schimke.cadence.podcasts.plainShowNotes
import ee.schimke.cadence.podcasts.podcastWidgetBattery
import ee.schimke.cadence.podcasts.podcastWidgetSummary
import ee.schimke.cadence.podcasts.readyForDevice
import ee.schimke.cadence.podcasts.rememberPodcastPlayer
import ee.schimke.cadence.podcasts.rememberPodcastWork
import ee.schimke.cadence.sync.BrowsedItem
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.formatLastRefreshed
import ee.schimke.cadence.sync.rememberDirectorySourceLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the home screen can ask for; defaults keep previews short. */
data class HomeActions(
  val onAddFolder: () -> Unit = {},
  val onAddPodcasts: () -> Unit = {},
  val onOpenPodcasts: () -> Unit = {},
  /** Each reports whether it worked, so a failed swipe springs back. */
  val onArchiveEpisode: suspend (episodeKey: String) -> Boolean = { true },
  val onAddFresh: suspend (episodeKey: String) -> Boolean = { true },
  val onDismissFresh: suspend (episodeKey: String) -> Boolean = { true },
  /** Show notes and transcript for the episode sheet; previews use what the episode carries. */
  val onLoadDetails: suspend (Episode) -> EpisodeDetails = {
    EpisodeDetails(plainShowNotes(it.summary), it.transcriptUrl.takeIf(String::isNotBlank))
  },
  val onRefreshFolder: (profileId: String) -> Unit = {},
  val onFindApp: (SourceSuggestion) -> Unit = {},
  val onCopyToHeadphones: () -> Unit = {},
  val onSyncPodcasts: () -> Unit = {},
  val onPreparePodcasts: () -> Unit = {},
  val onChooseHeadphoneStorage: () -> Unit = {},
  val onBluetoothControls: () -> Unit = {},
  val onManageSources: () -> Unit = {},
  val onFileExplorer: () -> Unit = {},
  val onPrevious: () -> Unit = {},
  val onPlayPause: () -> Unit = {},
  val onNext: () -> Unit = {},
)

@Composable
fun HomeScreen(
  podcasts: PodcastController,
  onAddPodcasts: () -> Unit,
  onOpenPodcasts: () -> Unit,
  onCopyToHeadphones: () -> Unit,
  onBluetoothControls: () -> Unit,
  onManageSources: () -> Unit,
  onFileExplorer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = metroViewModel<HomeViewModel>()
  val state by viewModel.collectState()
  val suggestions by viewModel.suggestions.collectAsState()
  val podcastState by podcasts.state.collectAsState()
  val podcastBusy by podcasts.busy.collectAsState()
  val podcastStatus by podcasts.status.collectAsState()
  val podcastWork =
    rememberPodcastWork(podcasts).let {
      if (podcastBusy && it.status == null) it.copy(status = podcastStatus) else it
    }
  val rawPodcastPlayer = rememberPodcastPlayer(podcasts, state.bluetooth.mediaAccessGranted)
  KeepPodcastScreenAwake(podcastBusy && !podcastWork.active)
  val podcastsLoaded by podcasts.loaded.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val podcastPlayer =
    rawPodcastPlayer.copy(
      playEpisode = { episode ->
        scope.launch {
          if (!state.bluetooth.localAudioControls || viewModel.switchToBluetoothAudio())
            rawPodcastPlayer.playEpisode(episode)
          else snackbar.showSnackbar("Couldn't switch the headphones to Bluetooth mode")
        }
      },
      playPause = {
        scope.launch {
          if (
            rawPodcastPlayer.playing ||
              !state.bluetooth.localAudioControls ||
              viewModel.switchToBluetoothAudio()
          )
            rawPodcastPlayer.playPause()
          else snackbar.showSnackbar("Couldn't switch the headphones to Bluetooth mode")
        }
      },
    )
  val addFolder = rememberDirectorySourceLauncher(viewModel::addFolder)
  /** Runs a swipe's action and always says what happened, since the row itself just vanishes. */
  suspend fun podcastResult(block: suspend PodcastController.() -> Unit): Boolean {
    // In the screen's scope, not the row's: the action removes its own row (an archive, an
    // add), and work it does afterwards must not be cancelled with it.
    return scope
      .async {
        podcasts.run(block).also { scope.launch { snackbar.showSnackbar(podcasts.status.value) } }
      }
      .await()
  }
  fun podcastAction(block: suspend PodcastController.() -> Unit) {
    scope.launch {
      // Failures are reported here; successes show up in the list itself.
      if (!podcasts.run(block)) snackbar.showSnackbar(podcasts.status.value)
    }
  }
  val chooseHeadphoneStorage = rememberFileExplorerOpenLauncher { _, path ->
    podcastAction { setTarget(path.toString()) }
  }
  /** A copy that failed because the saved storage is gone offers to choose it again. */
  suspend fun showSyncResult(ok: Boolean) {
    val message = podcasts.status.value
    if (!ok && needsStorageChoice(message)) {
      if (
        snackbar.showSnackbar(message, actionLabel = "Choose storage", withDismissAction = true) ==
          SnackbarResult.ActionPerformed
      )
        chooseHeadphoneStorage()
    } else snackbar.showSnackbar(message)
  }
  CopyWhenHeadphonesConnect(
    podcasts = podcasts,
    step =
      headphoneSyncStep(podcastState, state.sources.isNotEmpty(), state.attachedDevice != null),
    attachedDeviceId = state.attachedDevice?.id,
    onResult = ::showSyncResult,
  )
  HomeContent(
    state = state,
    podcasts = podcastState,
    canRefreshPodcasts = podcasts.canRefresh,
    canArchive = podcasts.canArchive,
    canAddFresh = podcasts.canAddFresh,
    podcastBusy = podcastBusy,
    podcastWork = podcastWork,
    podcastPlayer = podcastPlayer,
    loading = !state.loaded || !podcastsLoaded,
    suggestions = suggestions,
    snackbar = snackbar,
    actions =
      HomeActions(
        onAddFolder = addFolder,
        onAddPodcasts = onAddPodcasts,
        onOpenPodcasts = onOpenPodcasts,
        onArchiveEpisode = { key -> podcastResult { archiveEpisode(key) } },
        onAddFresh = { key -> podcastResult { addFresh(key) } },
        onDismissFresh = { key -> podcastResult { dismissFresh(key) } },
        onLoadDetails = { loadEpisodeDetails(podcasts.platform, it) },
        onRefreshFolder = viewModel::refreshNow,
        onFindApp = viewModel::openSuggestion,
        onCopyToHeadphones = onCopyToHeadphones,
        onSyncPodcasts = {
          scope.launch {
            val ok = podcasts.run { copyToDevice() }
            // Successes show up in the sync card itself.
            if (!ok) showSyncResult(ok)
          }
        },
        onPreparePodcasts = podcastWork.process,
        onChooseHeadphoneStorage = chooseHeadphoneStorage,
        onBluetoothControls = onBluetoothControls,
        onManageSources = onManageSources,
        onFileExplorer = onFileExplorer,
        onPrevious = viewModel::previousTrack,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::nextTrack,
      ),
    modifier = modifier,
  )
}

/**
 * The daily routine: plugging in prepared headphones copies to them without another tap. Runs once
 * per connection, and only when the copy is clearly the next step (set up, prepared, not yet
 * copied).
 */
@Composable
private fun CopyWhenHeadphonesConnect(
  podcasts: PodcastController,
  step: HeadphoneSyncStep,
  attachedDeviceId: Int?,
  onResult: suspend (ok: Boolean) -> Unit,
) {
  // Outlives the effect: the copy itself changes the step, which would otherwise cancel it.
  val scope = rememberCoroutineScope()
  // Saved only once an attempt finishes, so leaving Home mid-copy (which cancels it) retries on
  // return instead of skipping this connection.
  var handledFor by rememberSaveable { mutableStateOf<Int?>(null) }
  var attempting by remember { mutableStateOf(false) }
  val copyNow = shouldCopyOnConnect(step)
  LaunchedEffect(attachedDeviceId, copyNow) {
    if (attachedDeviceId == null) {
      handledFor = null
      return@LaunchedEffect
    }
    if (!copyNow || handledFor == attachedDeviceId || attempting) return@LaunchedEffect
    attempting = true
    scope.launch {
      var ok = false
      try {
        // The volume mounts a moment after the USB device appears, so retry briefly.
        for (wait in listOf(1_000L, 2_000L, 4_000L, 8_000L)) {
          delay(wait)
          ok = podcasts.run { copyToDevice() }
          if (ok) break
        }
        handledFor = attachedDeviceId
      } finally {
        attempting = false
      }
      // After clearing the guard: a result with an action waits for the user, and a reconnect
      // meanwhile must still copy.
      onResult(ok)
    }
  }
}

/**
 * The library: what each source currently holds, newest source last. With no sources it shows a
 * short welcome that only offers ways to add one, and gets out of the way once anything is added.
 */
@Composable
fun HomeContent(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  actions: HomeActions,
  modifier: Modifier = Modifier,
  canRefreshPodcasts: Boolean = false,
  canArchive: Boolean = false,
  canAddFresh: Boolean = false,
  podcastBusy: Boolean = false,
  podcastWork: PodcastWorkUi = PodcastWorkUi(),
  podcastPlayer: PodcastPlayerUi = PodcastPlayerUi(),
  loading: Boolean = false,
  suggestions: List<SourceSuggestion> = emptyList(),
  snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
  val playlist = podcasts.library.playlist
  val empty = state.sources.isEmpty() && playlist == null
  var adding by rememberSaveable { mutableStateOf(false) }
  var opened by remember { mutableStateOf<EpisodeInfo?>(null) }
  opened?.let { info ->
    EpisodeSheet(info, actions.onLoadDetails, actions.onOpenPodcasts) { opened = null }
  }
  if (adding)
    AddSourceSheet(
      suggestions = suggestions,
      actions = actions,
      onDismiss = { adding = false },
    )
  Scaffold(
    modifier = modifier,
    topBar = { LibraryTopBar(state, actions, empty) },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    if (empty && loading) return@Scaffold
    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
      if (empty) {
        item(key = "welcome") { WelcomeCard(suggestions, actions) }
        return@LazyColumn
      }
      // The order follows the morning routine: what's on the headphones and syncing them first,
      // then playback, then what this phone has fetched and prepared for the next sync.
      item(key = "headphones") {
        HeadphonesSection(state, podcasts, podcastBusy, podcastWork, actions)
      }
      val headphonesConnected =
        state.attachedDevice != null || state.bluetooth.connectedDevice != null
      val playingOnPhone = podcastPlayer.title != null && !state.bluetooth.localAudioControls
      // The phone's own player (with seeking) stays whenever something plays here; the headphone
      // transport joins it over Bluetooth, or on USB explains why there is none.
      if (headphonesConnected || podcastPlayer.title != null)
        item(key = "controls") {
          AudioControlsSection(state, podcastPlayer, playingOnPhone, actions)
        }
      item(key = "phone") {
        PhoneSection(state, podcasts, canRefreshPodcasts, podcastWork, actions) { adding = true }
      }
      if (podcastPlayer.suggestMediaAccess)
        item(key = "media-access") {
          OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
              Text("Connect your media apps", style = MaterialTheme.typography.titleMedium)
              Text(
                "Allow notification access to show and control playback from installed media apps.",
                style = MaterialTheme.typography.bodyMedium,
              )
              TextButton(onClick = podcastPlayer.requestMediaAccess) { Text("Allow media access") }
            }
          }
        }
      if (playlist != null)
        item(key = "podcasts") {
          PodcastSection(podcasts, canArchive, actions, podcastPlayer) { opened = it }
        }
      val fresh = if (podcasts.showFresh) podcasts.freshToReview else emptyList()
      if (fresh.isNotEmpty())
        item(key = "fresh") {
          // Folded away until asked for: the playlist above is what gets synced.
          var expanded by rememberSaveable { mutableStateOf(false) }
          if (expanded)
            FreshSection(
              fresh,
              addTo =
                if (!canAddFresh) null
                else if (podcasts.library.sourceList == POCKET_CASTS_UP_NEXT) "Up Next"
                else playlist?.name,
              onAdd = actions.onAddFresh,
              onDismiss = actions.onDismissFresh,
              onOpen = { opened = EpisodeInfo(it, status = "New release") },
              onCollapse = { expanded = false },
            )
          else FindMoreContent(fresh.size) { expanded = true }
        }
      items(state.sources, key = { it.id }, contentType = { "source" }) { source ->
        // One scan of the profiles per source instead of three.
        val profile = state.profileFor(source)
        FolderSection(
          source = source,
          contents = state.contents[source.id] ?: HomeViewModel.SourceContents(loading = true),
          lastRefreshed = profile?.last_refreshed_at.orEmpty(),
          refreshing = state.refresh.running && state.refresh.profileId == profile?.id,
          onRefresh = profile?.let { { actions.onRefreshFolder(it.id) } },
        )
      }
    }
  }
}

@Composable
private fun LibraryTopBar(
  state: HomeViewModel.UiState,
  actions: HomeActions,
  /** Only the welcome shows, without the headphones section and its Bluetooth button. */
  empty: Boolean,
) {
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        ),
  ) {
    Row(
      Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Home",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.weight(1f),
      )
      // Where Home's settings live, in plain sight rather than behind an overflow menu.
      TopBarAction(Icons.Filled.Source, "Manage sources", actions.onManageSources)
      TopBarAction(Icons.Filled.SdStorage, "Storage", actions.onFileExplorer)
      if (empty && state.bluetoothSupported && state.bluetooth.connectedDevice == null)
        TopBarAction(Icons.Filled.Bluetooth, "Bluetooth controls", actions.onBluetoothControls)
    }
  }
}

/** An icon button whose label also shows as a tooltip on long-press or hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = { PlainTooltip { Text(label) } },
    state = rememberTooltipState(),
  ) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
  }
}

@Composable
private fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      title,
      Modifier.weight(1f).semantics { heading() },
      style = MaterialTheme.typography.titleMedium,
    )
    action?.invoke()
  }
}

/** What the headphones hold, whether that matches this phone, and one big button to sync. */
@Composable
private fun HeadphonesSection(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  podcastBusy: Boolean,
  podcastWork: PodcastWorkUi,
  actions: HomeActions,
) {
  val device = state.bluetooth.connectedDevice
  val pluggedIn = state.attachedDevice != null
  val step = headphoneSyncStep(podcasts, state.sources.isNotEmpty(), pluggedIn)
  // Downloads and processing report under "On this phone"; here, busy means a copy.
  val copying = podcastBusy && !podcastWork.active
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(
      "On your headphones",
      action =
        if (state.bluetoothSupported && device == null) {
          { TextButton(onClick = actions.onBluetoothControls) { Text("Bluetooth controls") } }
        } else null,
    )
    if (podcasts.library.playlist != null) {
      val summary = podcastWidgetSummary(podcasts)
      PodcastSyncHomeCard(
        copied = summary.copied,
        available = summary.available,
        battery = podcastWidgetBattery(device, podcasts.target),
        ready = podcasts.readyForDevice(),
        showSwimming = state.bluetooth.localAudioControls,
      )
    }
    HeadphoneSyncPrompt(
      step = step,
      busy = copying,
      enabled = !podcastWork.active,
      progress = podcastWork.status.takeIf { copying },
      actions = actions,
      // Only meaningful once there is something prepared to copy.
      changes =
        if (step is HeadphoneSyncStep.ReadyToCopy) headphoneSyncChanges(podcasts) else emptyList(),
    )
  }
}

/** Playback on the connected headphones, or on this phone while it plays through them. */
@Composable
private fun AudioControlsSection(
  state: HomeViewModel.UiState,
  podcastPlayer: PodcastPlayerUi,
  playingOnPhone: Boolean,
  actions: HomeActions,
) {
  val bluetooth =
    if (playingOnPhone)
      state.bluetooth.copy(
        mediaInfo =
          MediaInfo(
            title = podcastPlayer.title,
            artist = null,
            album = null,
            playing = podcastPlayer.playing,
            durationMs = podcastPlayer.durationMs,
            positionMs = podcastPlayer.positionMs,
            packageName = "ee.schimke.cadence",
          ),
      )
    else state.bluetooth
  val bluetoothConnected = state.bluetooth.connectedDevice != null
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle("Audio controls") {
      if (bluetoothConnected)
        TextButton(onClick = actions.onBluetoothControls) { Text("All controls") }
    }
    if (podcastPlayer.title != null) PhonePodcastPlayer(podcastPlayer)
    // Over Bluetooth this drives the headphones; plugged in over USB with nothing playing on the
    // phone, it says why there are no controls.
    if (bluetoothConnected || podcastPlayer.title == null)
      HomeMiniPlayer(
        bluetooth,
        actions.onPrevious,
        if (playingOnPhone) podcastPlayer.playPause else actions.onPlayPause,
        actions.onNext,
      )
  }
}

/** What this phone has fetched and prepared for the next sync, and the buttons to get more. */
@Composable
private fun PhoneSection(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  canRefreshPodcasts: Boolean,
  podcastWork: PodcastWorkUi,
  actions: HomeActions,
  onConnectSource: () -> Unit,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle("On this phone")
    val cloudStatus =
      cloudSyncStatus(
        podcasts,
        canRefreshPodcasts,
        podcastWork.supportsDownload,
        busy = podcastWork.active,
        // Shown once, with its progress bar, just below the card.
        progress = null,
      )
    CloudSyncHomeCard(
      status = cloudStatus,
      onOpen = if (cloudStatus.hasSource) actions.onOpenPodcasts else onConnectSource,
      onRefresh = podcastWork.refresh,
      onDownload = podcastWork.download,
      onProcess = podcastWork.process,
    )
    if (podcastWork.active) WorkProgress(podcastWork)
    else podcastWork.error?.let { WorkError(it, podcastWork) }
    if (state.profiles.isNotEmpty() || podcasts.library.playlist != null)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (podcasts.library.playlist != null)
          OutlinedButton(onClick = actions.onOpenPodcasts) {
            Icon(Icons.Filled.Podcasts, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("All episodes")
          }
        if (state.profiles.isNotEmpty())
          OutlinedButton(
            enabled = !state.refresh.running,
            onClick = { state.profiles.forEach { actions.onRefreshFolder(it.id) } },
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.refresh.running) "Refreshing…" else "Refresh folders")
          }
      }
  }
}

/** The running download or processing, with a real percentage once there is one. */
@Composable
private fun WorkProgress(work: PodcastWorkUi) {
  Column(
    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      work.status ?: "Working…",
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    val fraction = work.fraction
    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
  }
}

/** Why the last download or processing stopped, with a way to try again. */
@Composable
private fun WorkError(message: String, work: PodcastWorkUi) {
  val colors = MaterialTheme.colorScheme
  Surface(
    color = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(
          message,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      Row(Modifier.align(Alignment.End)) {
        TextButton(onClick = work.dismissError) { Text("Dismiss", color = colors.onErrorContainer) }
        val retry =
          when (work.failedTask) {
            "process" -> work.process
            "refresh" -> work.refresh
            else -> work.download
          }
        TextButton(onClick = retry) { Text("Try again", color = colors.onErrorContainer) }
      }
    }
  }
}

/** New Releases, folded to one row until the user goes looking for something new. */
@Composable
private fun FindMoreContent(releases: Int, onExpand: () -> Unit) {
  OutlinedCard(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
    ListItem(
      leadingContent = {
        Icon(
          Icons.Filled.NewReleases,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      headlineContent = { Text("Find more content") },
      supportingContent = { Text(count(releases, "new release") + " from your subscriptions") },
      trailingContent = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
  }
}

/** One status line and, at most, one button: whatever [step] says to do next. */
@Composable
private fun HeadphoneSyncPrompt(
  step: HeadphoneSyncStep,
  busy: Boolean,
  enabled: Boolean,
  progress: String?,
  actions: HomeActions,
  changes: List<HeadphoneSyncChange> = emptyList(),
) {
  val colors = MaterialTheme.colorScheme
  // The moment to act stands out; everything else is a calm, tonal status.
  val (targetContainer, targetContent) =
    when {
      busy -> colors.surfaceContainerHigh to colors.onSurface
      step is HeadphoneSyncStep.ReadyToCopy && step.pluggedIn ->
        colors.primaryContainer to colors.onPrimaryContainer
      step == HeadphoneSyncStep.ChooseStorage ->
        colors.primaryContainer to colors.onPrimaryContainer
      step is HeadphoneSyncStep.UpToDate -> colors.tertiaryContainer to colors.onTertiaryContainer
      else -> colors.surfaceContainerHigh to colors.onSurface
    }
  val container by animateColorAsState(targetContainer)
  val content by animateColorAsState(targetContent)
  Surface(
    color = container,
    contentColor = content,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth().animateContentSize(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(40.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(step.icon(busy), contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
          if (busy) progress ?: "Syncing…" else step.message,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      val wide = Modifier.fillMaxWidth().heightIn(min = 56.dp)
      when (step) {
        HeadphoneSyncStep.NoSource,
        HeadphoneSyncStep.PlugInToSetUp -> Unit
        HeadphoneSyncStep.ChooseStorage ->
          Button(
            enabled = enabled && !busy,
            onClick = actions.onChooseHeadphoneStorage,
            modifier = wide,
          ) {
            Text("Choose headphone storage")
          }
        is HeadphoneSyncStep.Prepare ->
          if (!busy)
            FilledTonalButton(
              enabled = enabled,
              onClick = actions.onPreparePodcasts,
              modifier = wide,
            ) {
              Text("Process now")
            }
        is HeadphoneSyncStep.ReadyToCopy ->
          if (step.pluggedIn)
            Button(enabled = enabled && !busy, onClick = actions.onSyncPodcasts, modifier = wide) {
              Icon(Icons.Filled.Sync, contentDescription = null, Modifier.size(20.dp))
              Spacer(Modifier.width(8.dp))
              Text("Sync now", style = MaterialTheme.typography.titleMedium)
            }
        is HeadphoneSyncStep.UpToDate ->
          if (step.pluggedIn && !busy)
            TextButton(enabled = enabled, onClick = actions.onSyncPodcasts) {
              Text("Sync again", color = content)
            }
        is HeadphoneSyncStep.Folders ->
          if (step.pluggedIn)
            Button(
              enabled = enabled && !busy,
              onClick = actions.onCopyToHeadphones,
              modifier = wide,
            ) {
              Text("Sync folders")
            }
      }
      if (changes.isNotEmpty() && !busy) SyncChanges(changes, content)
    }
  }
}

/** The episodes the next sync adds, updates and drops, folded behind a one-line summary. */
@Composable
private fun SyncChanges(changes: List<HeadphoneSyncChange>, content: Color) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Column {
    Row(
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClickLabel = if (expanded) "Hide changes" else "Show changes") {
          expanded = !expanded
        }
        .heightIn(min = 48.dp)
        .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text("What will change", style = MaterialTheme.typography.labelLarge)
        Text(
          changes.summary(),
          style = MaterialTheme.typography.bodySmall,
          color = content.copy(alpha = 0.8f),
        )
      }
      Icon(
        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        contentDescription = null,
      )
    }
    if (expanded)
      Column(
        Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        changes.forEach { change ->
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              when (change.kind) {
                HeadphoneSyncChange.Kind.Add -> Icons.Filled.Add
                HeadphoneSyncChange.Kind.Update -> Icons.Filled.Sync
                HeadphoneSyncChange.Kind.Remove -> Icons.Filled.Remove
              },
              contentDescription =
                when (change.kind) {
                  HeadphoneSyncChange.Kind.Add -> "New"
                  HeadphoneSyncChange.Kind.Update -> "Updated"
                  HeadphoneSyncChange.Kind.Remove -> "Removed"
                },
              modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(
                change.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                listOf(change.podcast, change.detail)
                  .filter { it.isNotBlank() }
                  .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
  }
}

private fun HeadphoneSyncStep.icon(busy: Boolean): ImageVector =
  when {
    busy -> Icons.Filled.Sync
    this == HeadphoneSyncStep.NoSource -> Icons.Filled.Add
    this == HeadphoneSyncStep.PlugInToSetUp -> Icons.Filled.Usb
    this == HeadphoneSyncStep.ChooseStorage -> Icons.Filled.SdStorage
    this is HeadphoneSyncStep.Prepare -> Icons.Filled.Bedtime
    this is HeadphoneSyncStep.ReadyToCopy ->
      if (pluggedIn) Icons.Filled.Headphones else Icons.Filled.Usb
    this is HeadphoneSyncStep.UpToDate -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Folder
  }

/**
 * First run only: what Cadence does, in three steps, and the ways to add something to sync.
 * Replaced by the library as soon as a source exists.
 */
@Composable
private fun WelcomeCard(suggestions: List<SourceSuggestion>, actions: HomeActions) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Surface(
      color = colors.primaryContainer,
      contentColor = colors.onPrimaryContainer,
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          Modifier.size(56.dp).clip(CircleShape).background(colors.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Headphones,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(30.dp),
          )
        }
        Text(
          "Your podcasts, ready every morning",
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          "Set up once. After that, the daily routine is just plugging in.",
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        WelcomeStep(Icons.Filled.Podcasts, "Choose what to listen to")
        WelcomeStep(Icons.Filled.Bedtime, "Episodes are prepared overnight while charging")
        WelcomeStep(Icons.Filled.Usb, "Plug in your headphones and they're copied")
      }
    }
    Text(
      "Start with",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
    SourceOptions(suggestions, actions, onChosen = {}, tiles = true)
  }
}

@Composable
private fun WelcomeStep(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceSheet(
  suggestions: List<SourceSuggestion>,
  actions: HomeActions,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      "Add a source",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    SourceOptions(suggestions, actions, onChosen = onDismiss)
    Spacer(Modifier.height(24.dp))
  }
}

/**
 * The three kinds of source: a folder, an app that provides files, or a podcast service. As [tiles]
 * (the first-run welcome) podcasts come first and stand out, since that is what most people want.
 */
@Composable
private fun SourceOptions(
  suggestions: List<SourceSuggestion>,
  actions: HomeActions,
  onChosen: () -> Unit,
  tiles: Boolean = false,
) {
  var findingApps by remember { mutableStateOf(false) }
  val podcastOption =
    @Composable {
      SourceOption(
        Icons.Filled.Podcasts,
        "Podcasts",
        "Pocket Casts, a podcast feed, or apps that sync with gpodder",
        tile = tiles,
        highlighted = tiles,
      ) {
        onChosen()
        actions.onAddPodcasts()
      }
    }
  Column(verticalArrangement = Arrangement.spacedBy(if (tiles) 8.dp else 0.dp)) {
    if (tiles) podcastOption()
    SourceOption(
      Icons.Filled.Folder,
      "Folder",
      "Music or audiobooks already on this device",
      tile = tiles,
    ) {
      onChosen()
      actions.onAddFolder()
    }
    // Apps such as Drive or Nextcloud appear in the same folder picker once installed.
    if (suggestions.isNotEmpty())
      SourceOption(
        Icons.Filled.Cloud,
        "Files from an app",
        "Google Drive, Dropbox, Nextcloud and other storage apps",
        tile = tiles,
      ) {
        if (suggestions.any { it.installed }) {
          onChosen()
          actions.onAddFolder()
        } else findingApps = !findingApps
      }
    if (findingApps)
      suggestions.forEach { app ->
        ListItem(
          headlineContent = { Text("Get ${app.displayName}") },
          supportingContent = { Text(app.description) },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          modifier = Modifier.clickable { actions.onFindApp(app) }.padding(start = 40.dp),
        )
      }
    if (!tiles) podcastOption()
  }
}

@Composable
private fun SourceOption(
  icon: ImageVector,
  title: String,
  detail: String,
  tile: Boolean = false,
  highlighted: Boolean = false,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val item =
    @Composable {
      ListItem(
        leadingContent = {
          if (tile)
            Box(
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(if (highlighted) colors.primary else colors.secondaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
              )
            }
          else Icon(icon, contentDescription = null)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent =
          if (tile) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
          } else null,
        // Takes the colour of whatever it sits on: the tile or the sheet.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (tile) Modifier else Modifier.clickable(onClick = onClick),
      )
    }
  if (tile)
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(20.dp),
      color = if (highlighted) colors.secondaryContainer else colors.surfaceContainerLow,
      border = if (highlighted) null else BorderStroke(1.dp, colors.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      item()
    }
  else item()
}

@Composable
private fun SectionHeader(
  icon: ImageVector,
  title: String,
  detail: String,
  onClick: (() -> Unit)? = null,
  /** One visible action at the end of the header, such as refreshing this folder. */
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    action?.invoke()
  }
}

@Composable
private fun PodcastSection(
  podcasts: LocalPodcastState,
  canArchive: Boolean,
  actions: HomeActions,
  player: PodcastPlayerUi,
  onOpen: (EpisodeInfo) -> Unit,
) {
  val library = podcasts.library
  val playlist = library.playlist ?: return
  val ready = playlist.episodes.count { it.key in podcasts.downloadedEpisodeKeys }
  OutlinedCard(Modifier.fillMaxWidth()) {
    SectionHeader(
      Icons.Filled.Podcasts,
      playlist.name,
      buildString {
        append(count(playlist.episodes.size, "episode"))
        if (ready > 0) append(" · $ready downloaded")
        if (library.upcoming.isNotEmpty()) append(" · ${library.upcoming.size} next")
      },
      // Refresh is on the cloud sync card above; removing the source is on the Podcasts screen.
      onClick = actions.onOpenPodcasts,
    )
    if (canArchive)
      Text(
        "Swipe right on an episode you've finished to archive it",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
      )
    @Composable
    fun row(episode: Episode, next: Boolean) {
      key(episode.key) {
        HorizontalDivider()
        val downloaded = episode.key in podcasts.downloadedEpisodeKeys
        val position = episodePosition(episode, library)
        val onPlay =
          if (
            player.supported &&
              (downloaded || (episode.backend == "pocket-casts" && player.pocketCastsAvailable))
          ) {
            { player.playEpisode(episode) }
          } else null
        SwipeActionRow(
          onSwipeRight =
            if (canArchive) archiveAction { actions.onArchiveEpisode(episode.key) } else null,
          onSwipeLeft = null,
        ) {
          EpisodeRow(
            episode,
            position,
            downloaded = downloaded,
            onClick = {
              onOpen(
                EpisodeInfo(
                  episode,
                  position,
                  downloaded,
                  status = if (next) "Next in ${playlist.name}" else "In ${playlist.name}",
                  onPlay = onPlay,
                ),
              )
            },
            onPlay = onPlay,
          )
        }
      }
    }
    playlist.episodes.forEach { row(it, next = false) }
    if (library.upcoming.isNotEmpty()) {
      HorizontalDivider()
      Text(
        "Next in ${playlist.name} · moves up as you archive",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
          Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics {
            heading()
          },
      )
      library.upcoming.forEach { row(it, next = true) }
    }
  }
}

@Composable
private fun EpisodeRow(
  episode: Episode,
  position: Long,
  downloaded: Boolean,
  onClick: () -> Unit,
  onPlay: (() -> Unit)?,
) {
  ListItem(
    leadingContent = { PodcastArtwork(episode) },
    headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          when {
            position <= 0 -> formatTime(episode.durationMs)
            else -> "${formatTime(episode.durationMs - position)} left"
          } + if (downloaded) " · Downloaded" else "",
        )
        if (position > 0)
          LinearProgressIndicator(
            progress = { position.toFloat() / episode.durationMs },
            modifier = Modifier.fillMaxWidth(),
          )
      }
    },
    trailingContent =
      if (onPlay == null) null
      else {
        {
          IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${episode.title} on phone")
          }
        }
      },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.clickable(onClickLabel = "Episode details", onClick = onClick),
  )
}

@Composable
private fun PhonePodcastPlayer(player: PodcastPlayerUi) {
  val title = player.title ?: return
  var draft by remember(title) { mutableStateOf<Float?>(null) }
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Text(
        if (player.playing) "Playing on phone" else "Paused on phone",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(title, style = MaterialTheme.typography.bodyMedium)
      val duration = player.durationMs.coerceAtLeast(1)
      Slider(
        value = draft ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f),
        onValueChange = { draft = it },
        onValueChangeFinished = {
          draft?.let { player.seek((it * duration).toLong()) }
          draft = null
        },
      )
      Text(
        "${formatTime((draft?.let { it * duration }?.toLong() ?: player.positionMs))} / ${formatTime(player.durationMs)}",
        style = MaterialTheme.typography.bodySmall,
      )
      TextButton(onClick = player.playPause) { Text(if (player.playing) "Pause" else "Play") }
    }
  }
}

private const val FOLDER_PREVIEW = 5

@Composable
private fun FolderSection(
  source: SyncSource,
  contents: HomeViewModel.SourceContents,
  lastRefreshed: String,
  refreshing: Boolean,
  onRefresh: (() -> Unit)?,
) {
  var expanded by rememberSaveable(source.id) { mutableStateOf(false) }
  val items = contents.items
  OutlinedCard(Modifier.fillMaxWidth()) {
    SectionHeader(
      if (source.fromApp) Icons.Filled.Cloud else Icons.Filled.Folder,
      source.name,
      when {
        contents.error != null -> contents.error
        contents.loading && items.isEmpty() -> "Reading…"
        refreshing -> "Staging for headphones…"
        else ->
          count(items.size, "file") +
            " · " +
            formatSize(items.sumOf { it.sizeBytes }) +
            if (lastRefreshed.isNotBlank()) " · staged " + formatLastRefreshed(lastRefreshed)
            else ""
      },
      // Removing a folder is in Manage sources, away from the daily routine.
      action =
        onRefresh?.let { refresh ->
          {
            IconButton(enabled = !refreshing, onClick = refresh) {
              Icon(Icons.Filled.Refresh, contentDescription = "Refresh ${source.name}")
            }
          }
        },
    )
    if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    val shown = if (expanded) items else items.take(FOLDER_PREVIEW)
    shown.forEach { item ->
      HorizontalDivider()
      FileRow(item)
    }
    if (items.size > FOLDER_PREVIEW) {
      HorizontalDivider()
      TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.padding(horizontal = 8.dp),
      ) {
        Text(if (expanded) "Show fewer" else "Show all ${items.size}")
      }
    }
  }
}

@Composable
private fun FileRow(item: BrowsedItem) {
  val folder = item.relativePath.substringBeforeLast('/', "")
  ListItem(
    leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
    headlineContent = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      Text(
        (if (folder.isNotEmpty()) "$folder · " else "") + formatSize(item.sizeBytes),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
  )
}

/** A folder picked through another app's storage provider rather than device storage. */
private val SyncSource.fromApp: Boolean
  get() =
    kind == SourceKind.NFS_SHARE ||
      (location.startsWith("content://") &&
        !location.startsWith("content://com.android.externalstorage.documents"))

private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

internal fun formatSize(bytes: Long): String =
  when {
    bytes >= 1_000_000_000 -> "${(bytes / 100_000_000) / 10.0} GB"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
  }

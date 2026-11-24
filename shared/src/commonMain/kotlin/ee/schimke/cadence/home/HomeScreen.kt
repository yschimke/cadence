@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ee.schimke.cadence.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.devices.rememberEjectUsb
import ee.schimke.cadence.devices.rememberFileExplorerOpenLauncher
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.EpisodeDetails
import ee.schimke.cadence.podcasts.KeepPodcastScreenAwake
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.POCKET_CASTS_UP_NEXT
import ee.schimke.cadence.podcasts.PodcastController
import ee.schimke.cadence.podcasts.PodcastPlayerUi
import ee.schimke.cadence.podcasts.PodcastSourceKind
import ee.schimke.cadence.podcasts.PodcastSyncHomeCard
import ee.schimke.cadence.podcasts.PodcastWorkUi
import ee.schimke.cadence.podcasts.StatusCardExtras
import ee.schimke.cadence.podcasts.cloudSyncStatus
import ee.schimke.cadence.podcasts.episodePosition
import ee.schimke.cadence.podcasts.formatTime
import ee.schimke.cadence.podcasts.fromSecond
import ee.schimke.cadence.podcasts.loadEpisodeDetails
import ee.schimke.cadence.podcasts.plainShowNotes
import ee.schimke.cadence.podcasts.podcastWidgetBattery
import ee.schimke.cadence.podcasts.podcastWidgetSummary
import ee.schimke.cadence.podcasts.readyForDevice
import ee.schimke.cadence.podcasts.rememberPodcastPlayer
import ee.schimke.cadence.podcasts.rememberPodcastWork
import ee.schimke.cadence.podcasts.sourceBackend
import ee.schimke.cadence.podcasts.title
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
  val onEjectUsb: (() -> Unit)? = null,
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
  val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
  val podcastState by podcasts.state.collectAsStateWithLifecycle()
  val podcastBusy by podcasts.busy.collectAsStateWithLifecycle()
  val podcastStatus by podcasts.status.collectAsStateWithLifecycle()
  val podcastWork =
    rememberPodcastWork(podcasts).let {
      if (podcastBusy && it.status == null) it.copy(status = podcastStatus) else it
    }
  val rawPodcastPlayer = rememberPodcastPlayer(podcasts, state.bluetooth.mediaAccessGranted)
  KeepPodcastScreenAwake(podcastBusy && !podcastWork.active)
  val podcastsLoaded by podcasts.loaded.collectAsStateWithLifecycle()
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
  val ejectUsb = rememberEjectUsb()
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
        onEjectUsb =
          ejectUsb?.let { eject ->
            {
              if (!eject()) {
                scope.launch {
                  snackbar.showSnackbar("Open Storage in Settings to eject USB storage.")
                }
              }
            }
          },
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
      // One card says where things stand (headphones, this phone, the source) and what to do
      // next, so the episodes themselves start high on the screen; playback follows it.
      item(key = "status") {
        StatusSection(state, podcasts, canRefreshPodcasts, podcastBusy, podcastWork, actions) {
          adding = true
        }
      }
      // Headphone transport, volume and media-app access live on the Audio controls screen
      // (the icon on the status row). Only an episode playing on this phone keeps a player here,
      // so it can be paused without leaving Home.
      if (podcastPlayer.title != null && !state.bluetooth.localAudioControls)
        item(key = "phone-player") {
          Box(Modifier.animateItem()) { PhonePodcastPlayer(podcastPlayer) }
        }
      if (playlist != null)
        item(key = "podcasts") {
          Box(Modifier.animateItem()) {
            PodcastSection(podcasts, canArchive, actions, podcastPlayer) { opened = it }
          }
        }
      val fresh = if (podcasts.showFresh) podcasts.freshToReview else emptyList()
      if (fresh.isNotEmpty())
        item(key = "fresh") {
          // Folded away until asked for: the playlist above is what gets synced.
          var expanded by rememberSaveable { mutableStateOf(false) }
          val motionScheme = MaterialTheme.motionScheme
          AnimatedContent(
            targetState = expanded,
            transitionSpec = {
              fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                fadeOut(motionScheme.fastEffectsSpec())
            },
            label = "freshSection",
            modifier = Modifier.animateItem(),
          ) { isExpanded ->
            if (isExpanded)
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
        }
      items(state.sources, key = { it.id }, contentType = { "source" }) { source ->
        // One scan of the profiles per source instead of three.
        val profile = state.profileFor(source)
        Box(Modifier.animateItem()) {
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
/**
 * Where things stand, in one card shared with the launcher widget: the next step as a headline,
 * what the headphones hold, what this phone has ready, the battery and the source, with the step's
 * buttons and what the next sync changes inside it. Below it, only the running task's progress or
 * the last failure.
 */
@Composable
private fun StatusSection(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  canRefresh: Boolean,
  podcastBusy: Boolean,
  podcastWork: PodcastWorkUi,
  actions: HomeActions,
  onConnectSource: () -> Unit,
) {
  val device = state.bluetooth.connectedDevice
  val pluggedIn = state.attachedDevice != null
  val step = headphoneSyncStep(podcasts, state.sources.isNotEmpty(), pluggedIn)
  // Downloads and processing report through [podcastWork]; otherwise, busy means a copy.
  val copying = podcastBusy && !podcastWork.active
  val cloud =
    cloudSyncStatus(podcasts, canRefresh, podcastWork.supportsDownload, busy = podcastWork.active)
  val headline = syncHeadline(step, cloud)
  // Nothing to tap while a task runs: ejecting or syncing again mid-copy would cut off its files.
  val buttons =
    if (podcastWork.active || copying) emptyList()
    else
      statusButtons(
        step,
        cloud,
        ejectable = actions.onEjectUsb != null,
        // Folders copy on their own screen; alongside episodes they need their own button too.
        syncFolders = pluggedIn && state.sources.isNotEmpty(),
      )
  fun act(id: String) {
    when (id) {
      StatusCardExtras.REFRESH -> if (cloud.canRefresh) podcastWork.refresh()
      StatusCardExtras.AUDIO_CONTROLS -> actions.onBluetoothControls()
      "add" -> onConnectSource()
      "storage" -> actions.onChooseHeadphoneStorage()
      "download" -> podcastWork.download()
      "process" -> actions.onPreparePodcasts()
      "sync",
      "sync-again" -> actions.onSyncPodcasts()
      "eject" -> actions.onEjectUsb?.invoke()
      "folders" -> actions.onCopyToHeadphones()
    }
  }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (podcasts.library.playlist != null) {
      val summary = podcastWidgetSummary(podcasts)
      val changes =
        if (step is HeadphoneSyncStep.ReadyToCopy && !copying) headphoneSyncChanges(podcasts)
        else emptyList()
      var changesExpanded by rememberSaveable { mutableStateOf(false) }
      val extras =
        StatusCardExtras(
          buttons = buttons,
          changes = changes.takeIf { it.isNotEmpty() }?.summary(),
          changeLines = if (changesExpanded) changeLines(changes) else emptyList(),
          refresh = cloud.hasSource,
          audioControls = state.bluetoothSupported,
        )
      // The card is a Remote Compose document that names itself and its buttons.
      Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
        PodcastSyncHomeCard(
          copied = summary.copied,
          available = summary.available,
          battery = podcastWidgetBattery(device, podcasts.target),
          ready = podcasts.readyForDevice(),
          showSwimming = state.bluetooth.localAudioControls,
          headline = headline,
          source = syncSourceLine(cloud),
          attention = step.needsAttention,
          extras = extras,
        ) { id ->
          if (id == StatusCardExtras.CHANGES) changesExpanded = !changesExpanded else act(id)
        }
      }
    } else {
      // Only folders, or nothing yet: no card, just the step and its buttons.
      Text(
        step.message,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        buttons.forEach { button ->
          if (button.primary) Button(onClick = { act(button.id) }) { Text(button.label) }
          else TextButton(onClick = { act(button.id) }) { Text(button.label) }
        }
        if (state.bluetoothSupported)
          TopBarAction(Icons.Filled.Headphones, "Audio controls", actions.onBluetoothControls)
      }
    }
    when {
      copying -> WorkProgress(PodcastWorkUi(status = podcastWork.status ?: "Syncing…"))
      podcastWork.active -> WorkProgress(podcastWork)
      else -> podcastWork.error?.let { WorkError(it, podcastWork) }
    }
  }
}

/** One line per change for the expanded card, the first few with a count of the rest. */
private fun changeLines(changes: List<HeadphoneSyncChange>, limit: Int = 6): List<String> {
  val lines =
    changes.take(limit).map { change ->
      val mark =
        when (change.kind) {
          HeadphoneSyncChange.Kind.Add -> "+"
          HeadphoneSyncChange.Kind.Update -> "↻"
          HeadphoneSyncChange.Kind.Remove -> "−"
        }
      "$mark ${change.title} · ${change.detail}"
    }
  val more = changes.size - lines.size
  return if (more > 0) lines + "and $more more" else lines
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
    if (fraction == null) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
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
      modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
    )
    Text(
      "Add as many folders as you like, plus one podcast or audiobook service. Both go on " +
        "the same headphones: plug them in, then sync episodes and folders from Home.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
    )
    SourceOptions(suggestions, actions, onChosen = onDismiss)
    Spacer(Modifier.height(24.dp))
  }
}

/**
 * The three kinds of source: a folder, an app that provides files, or one podcast or audiobook
 * service. Folders and the service sit side by side; a second service replaces the first. As
 * [tiles] (the first-run welcome) podcasts come first and stand out, since that is what most people
 * want.
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
        "Podcasts or audiobooks",
        "Pocket Casts, Libro.fm, a podcast feed, or apps that sync with gpodder",
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
      "Music or audiobooks already on this device, such as Bandcamp downloads",
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
    val second = library.second
    // Beside a second service, each side is counted in its own words: "3 episodes · 1 audiobook".
    val (beside, main) = playlist.episodes.partition { library.fromSecond(it) }
    fun noun(kind: PodcastSourceKind?) =
      when (kind) {
        PodcastSourceKind.LibroFm -> "audiobook"
        PodcastSourceKind.Subsonic -> "track"
        PodcastSourceKind.Audiobookshelf,
        PodcastSourceKind.Jellyfin,
        PodcastSourceKind.Plex -> "item"
        else -> "episode"
      }
    val icon =
      when (library.source) {
        PodcastSourceKind.LibroFm,
        PodcastSourceKind.Audiobookshelf -> Icons.Filled.Headphones
        PodcastSourceKind.Subsonic,
        PodcastSourceKind.Jellyfin,
        PodcastSourceKind.Plex -> Icons.Filled.LibraryMusic
        else -> Icons.Filled.Podcasts
      }
    val waiting = library.upcoming.size + second?.upcoming.orEmpty().size
    SectionHeader(
      icon,
      listOfNotNull(playlist.name, second?.name?.ifBlank { second.kind.title }).joinToString(" + "),
      buildString {
        append(count(main.size, noun(library.source)))
        if (second != null) append(" · ").append(count(beside.size, noun(second.kind)))
        if (ready > 0) append(" · $ready downloaded")
        if (waiting > 0) append(" · $waiting next")
      },
      // Refresh is on the cloud sync card above; removing the source is on the Podcasts screen.
      onClick = actions.onOpenPodcasts,
    )
    if (canArchive)
      Text(
        "Swipe left on an episode you've finished to archive it",
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
          onSwipeRight = null,
          onSwipeLeft =
            if (canArchive && episode.backend == sourceBackend(PodcastSourceKind.PocketCasts))
              archiveAction { actions.onArchiveEpisode(episode.key) }
            else null,
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
    if (refreshing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
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

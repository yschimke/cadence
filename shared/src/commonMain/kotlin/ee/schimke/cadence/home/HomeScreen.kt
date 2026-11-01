package ee.schimke.cadence.home

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.bluetooth.MediaInfo
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.podcasts.CloudSyncHomeCard
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.KeepPodcastScreenAwake
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.PodcastController
import ee.schimke.cadence.podcasts.PodcastPlayerUi
import ee.schimke.cadence.podcasts.PodcastSyncHomeCard
import ee.schimke.cadence.podcasts.PodcastWorkUi
import ee.schimke.cadence.podcasts.cloudSyncStatus
import ee.schimke.cadence.podcasts.episodePosition
import ee.schimke.cadence.podcasts.formatTime
import ee.schimke.cadence.podcasts.podcastWidgetBattery
import ee.schimke.cadence.podcasts.podcastWidgetSummary
import ee.schimke.cadence.podcasts.readyForDevice
import ee.schimke.cadence.podcasts.rememberPodcastPlayer
import ee.schimke.cadence.podcasts.rememberPodcastWork
import ee.schimke.cadence.sync.BrowsedItem
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.formatLastRefreshed
import ee.schimke.cadence.sync.rememberDirectorySourceLauncher
import kotlinx.coroutines.launch

/** Everything the home screen can ask for; defaults keep previews short. */
data class HomeActions(
  val onAddFolder: () -> Unit = {},
  val onAddPodcasts: () -> Unit = {},
  val onOpenPodcasts: () -> Unit = {},
  val onRefreshPodcasts: () -> Unit = {},
  val onRemovePodcasts: () -> Unit = {},
  val onRefreshFolder: (profileId: String) -> Unit = {},
  val onRemoveSource: (sourceId: String) -> Unit = {},
  val onFindApp: (SourceSuggestion) -> Unit = {},
  val onCopyToHeadphones: () -> Unit = {},
  val onSyncPodcasts: () -> Unit = {},
  val onCloudSync: () -> Unit = {},
  val onBluetoothControls: () -> Unit = {},
  val onManageSources: () -> Unit = {},
  val onFileExplorer: () -> Unit = {},
  val onBookmarks: () -> Unit = {},
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
  onCloudSync: () -> Unit,
  onBluetoothControls: () -> Unit,
  onManageSources: () -> Unit,
  onFileExplorer: () -> Unit,
  onBookmarks: () -> Unit,
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
  fun podcastAction(block: suspend PodcastController.() -> Unit) {
    scope.launch {
      // Failures are reported here; successes show up in the list itself.
      if (!podcasts.run(block)) snackbar.showSnackbar(podcasts.status.value)
    }
  }
  HomeContent(
    state = state,
    podcasts = podcastState,
    canRefreshPodcasts = podcasts.canRefresh,
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
        onRefreshPodcasts = podcastWork.refresh,
        onRemovePodcasts = { podcastAction { removeSource() } },
        onRefreshFolder = viewModel::refreshNow,
        onRemoveSource = viewModel::removeSource,
        onFindApp = viewModel::openSuggestion,
        onCopyToHeadphones = onCopyToHeadphones,
        onSyncPodcasts = { podcastAction { copyToDevice() } },
        onCloudSync = onCloudSync,
        onBluetoothControls = onBluetoothControls,
        onManageSources = onManageSources,
        onFileExplorer = onFileExplorer,
        onBookmarks = onBookmarks,
        onPrevious = viewModel::previousTrack,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::nextTrack,
      ),
    modifier = modifier,
  )
}

/**
 * The library: what each source currently holds, newest source last. With no sources it only offers
 * ways to add one.
 */
@Composable
fun HomeContent(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  actions: HomeActions,
  modifier: Modifier = Modifier,
  canRefreshPodcasts: Boolean = false,
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
  if (adding)
    AddSourceSheet(
      suggestions = suggestions,
      actions = actions,
      onDismiss = { adding = false },
    )
  Scaffold(
    modifier = modifier,
    topBar = { LibraryTopBar(state, actions) },
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = {
      if (state.attachedDevice != null || state.bluetooth.connectedDevice != null)
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
          val playingOnPhone = podcastPlayer.title != null && !state.bluetooth.localAudioControls
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
                  )
              )
            else state.bluetooth
          HomeMiniPlayer(
            bluetooth,
            actions.onPrevious,
            if (playingOnPhone) podcastPlayer.playPause else actions.onPlayPause,
            actions.onNext,
          )
        }
    },
  ) { padding ->
    if (empty && loading) return@Scaffold
    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
      item(key = "overview") {
        HomeOverview(
          state,
          podcasts,
          canRefreshPodcasts,
          podcastBusy,
          podcastWork,
          actions,
        ) {
          adding = true
        }
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
      if (podcastPlayer.title != null)
        item(key = "phone-player") { PhonePodcastPlayer(podcastPlayer) }
      if (playlist != null)
        item(key = "podcasts") {
          PodcastSection(podcasts, canRefreshPodcasts, actions, podcastPlayer)
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
          onRemove = { actions.onRemoveSource(source.id) },
        )
      }
    }
  }
}

@Composable
private fun LibraryTopBar(
  state: HomeViewModel.UiState,
  actions: HomeActions,
) {
  var menu by remember { mutableStateOf(false) }
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        )
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
      IconButton(onClick = { menu = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        fun item(label: String, action: () -> Unit) =
          @Composable {
            DropdownMenuItem(
              text = { Text(label) },
              onClick = {
                menu = false
                action()
              },
            )
          }
        item("Manage sources", actions.onManageSources)()
        if (state.bluetoothSupported && state.bluetooth.connectedDevice == null)
          item("Bluetooth controls", actions.onBluetoothControls)()
        item("File explorer", actions.onFileExplorer)()
        item("Bookmarks", actions.onBookmarks)()
      }
    }
  }
}

@Composable
private fun HomeOverview(
  state: HomeViewModel.UiState,
  podcasts: LocalPodcastState,
  canRefreshPodcasts: Boolean,
  podcastBusy: Boolean,
  podcastWork: PodcastWorkUi,
  actions: HomeActions,
  onConnectSource: () -> Unit,
) {
  val device = state.bluetooth.connectedDevice
  var showUsbPrompt by rememberSaveable { mutableStateOf(false) }
  if (showUsbPrompt) {
    AlertDialog(
      onDismissRequest = { showUsbPrompt = false },
      title = { Text("Connect your headphones") },
      text = { Text("Connect your headphones to this phone by USB, then return here to sync.") },
      confirmButton = { TextButton(onClick = { showUsbPrompt = false }) { Text("OK") } },
    )
  }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val summary = podcastWidgetSummary(podcasts)
    val battery = podcastWidgetBattery(device, podcasts.target)
    Column(Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Sync with headphones",
          Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
        )
        if (device != null)
          TextButton(onClick = actions.onBluetoothControls) { Text("All controls") }
      }
      PodcastSyncHomeCard(
        copied = summary.copied,
        available = summary.available,
        battery = battery,
        ready = podcasts.readyForDevice(),
        showSwimming = state.bluetooth.localAudioControls,
      )
      if (podcastBusy && !podcastWork.active)
        Text(
          "Working…",
          style = MaterialTheme.typography.bodySmall,
        )
      Row {
        TextButton(
          enabled = !podcastBusy,
          onClick = {
            if (state.attachedDevice == null) showUsbPrompt = true
            else if (podcasts.readyForDevice() && podcasts.target.isNotBlank())
              actions.onSyncPodcasts()
            else if (podcasts.library.playlist != null) actions.onOpenPodcasts()
            else actions.onCopyToHeadphones()
          },
        ) {
          Text("Sync")
        }
      }
    }
    val cloudStatus =
      cloudSyncStatus(
        podcasts,
        canRefreshPodcasts,
        podcastWork.supportsDownload,
        busy = podcastBusy || podcastWork.active,
        progress = podcastWork.status,
      )
    CloudSyncHomeCard(
      status = cloudStatus,
      onOpen = onConnectSource,
      onRefresh = podcastWork.refresh,
      onDownload = podcastWork.download,
      onProcess = podcastWork.process,
    )
    if (state.profiles.isNotEmpty()) {
      TextButton(
        enabled = !state.refresh.running,
        onClick = { state.profiles.forEach { actions.onRefreshFolder(it.id) } },
      ) {
        Text(if (state.refresh.running) "Refreshing folders…" else "Refresh folders")
      }
    }
    if (podcasts.library.playlist != null)
      TextButton(onClick = actions.onOpenPodcasts) { Text("Podcasts") }
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

/** The three kinds of source: a folder, an app that provides files, or a podcast service. */
@Composable
private fun SourceOptions(
  suggestions: List<SourceSuggestion>,
  actions: HomeActions,
  onChosen: () -> Unit,
) {
  var findingApps by remember { mutableStateOf(false) }
  Column {
    SourceOption(
      Icons.Filled.Folder,
      "Folder",
      "Music or audiobooks already on this device",
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
    SourceOption(
      Icons.Filled.Podcasts,
      "Podcasts",
      "Pocket Casts, a podcast feed, or apps that sync with gpodder",
    ) {
      onChosen()
      actions.onAddPodcasts()
    }
  }
}

@Composable
private fun SourceOption(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
  ListItem(
    leadingContent = { Icon(icon, contentDescription = null) },
    headlineContent = { Text(title) },
    supportingContent = { Text(detail) },
    // Takes the colour of whatever it sits on: the empty-state card or the sheet.
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(onClick = onClick),
  )
}

@Composable
private fun SectionHeader(
  icon: ImageVector,
  title: String,
  detail: String,
  menu: List<Pair<String, () -> Unit>>,
  onClick: (() -> Unit)? = null,
) {
  var open by remember { mutableStateOf(false) }
  Row(
    Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (menu.isNotEmpty())
      Box {
        IconButton(
          onClick = { open = true },
          modifier = Modifier.semantics { contentDescription = "Options for $title" },
        ) {
          Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
          menu.forEach { (label, action) ->
            DropdownMenuItem(
              text = { Text(label) },
              onClick = {
                open = false
                action()
              },
            )
          }
        }
      }
  }
}

@Composable
private fun PodcastSection(
  podcasts: LocalPodcastState,
  canRefresh: Boolean,
  actions: HomeActions,
  player: PodcastPlayerUi,
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
      },
      buildList {
        if (canRefresh) add("Refresh" to actions.onRefreshPodcasts)
        add("Remove" to actions.onRemovePodcasts)
      },
      onClick = actions.onOpenPodcasts,
    )
    playlist.episodes.forEach { episode ->
      HorizontalDivider()
      EpisodeRow(
        episode,
        episodePosition(episode, library),
        downloaded = episode.key in podcasts.downloadedEpisodeKeys,
        onClick = actions.onOpenPodcasts,
        onPlay =
          if (
            player.supported &&
              (episode.key in podcasts.downloadedEpisodeKeys ||
                (episode.backend == "pocket-casts" && player.pocketCastsAvailable))
          ) {
            { player.playEpisode(episode) }
          } else null,
      )
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
    headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          when {
            position <= 0 -> formatTime(episode.durationMs)
            else -> "${formatTime(episode.durationMs - position)} left"
          } + if (downloaded) " · Downloaded" else ""
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
    modifier = Modifier.clickable(onClick = onClick),
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
  onRemove: () -> Unit,
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
      buildList {
        onRefresh?.let { add("Refresh" to it) }
        add("Remove" to onRemove)
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

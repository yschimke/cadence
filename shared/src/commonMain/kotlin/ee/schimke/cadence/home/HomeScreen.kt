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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.PodcastController
import ee.schimke.cadence.podcasts.advancedPosition
import ee.schimke.cadence.podcasts.formatTime
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
  val onBluetoothControls: () -> Unit = {},
  val onManageSources: () -> Unit = {},
  val onFileExplorer: () -> Unit = {},
  val onBookmarks: () -> Unit = {},
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
  onBookmarks: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = metroViewModel<HomeViewModel>()
  val state by viewModel.state.collectAsState()
  val suggestions by viewModel.suggestions.collectAsState()
  val podcastState by podcasts.state.collectAsState()
  val podcastsLoaded by podcasts.loaded.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
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
    loading = !state.loaded || !podcastsLoaded,
    suggestions = suggestions,
    snackbar = snackbar,
    actions =
      HomeActions(
        onAddFolder = addFolder,
        onAddPodcasts = onAddPodcasts,
        onOpenPodcasts = onOpenPodcasts,
        onRefreshPodcasts = { podcastAction { refreshSource() } },
        onRemovePodcasts = { podcastAction { removeSource() } },
        onRefreshFolder = viewModel::refreshNow,
        onRemoveSource = viewModel::removeSource,
        onFindApp = viewModel::openSuggestion,
        onCopyToHeadphones = onCopyToHeadphones,
        onBluetoothControls = onBluetoothControls,
        onManageSources = onManageSources,
        onFileExplorer = onFileExplorer,
        onBookmarks = onBookmarks,
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
    floatingActionButton = {
      if (!empty && !loading)
        ExtendedFloatingActionButton(
          onClick = { adding = true },
          icon = { Icon(Icons.Filled.Add, contentDescription = null) },
          text = { Text("Add source") },
        )
    },
    bottomBar = { HeadphonesBar(state, actions) },
  ) { padding ->
    if (empty && loading) return@Scaffold
    if (empty) {
      EmptyLibrary(
        suggestions = suggestions,
        actions = actions,
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }
    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
      if (playlist != null)
        item(key = "podcasts") { PodcastSection(podcasts, canRefreshPodcasts, actions) }
      items(state.sources, key = { it.id }) { source ->
        FolderSection(
          source = source,
          contents = state.contents[source.id] ?: HomeViewModel.SourceContents(loading = true),
          lastRefreshed = state.profileFor(source)?.last_refreshed_at.orEmpty(),
          refreshing =
            state.refresh.running && state.refresh.profileId == state.profileFor(source)?.id,
          onRefresh =
            state.profileFor(source)?.let { profile -> { actions.onRefreshFolder(profile.id) } },
          onRemove = { actions.onRemoveSource(source.id) },
        )
      }
    }
  }
}

@Composable
private fun LibraryTopBar(state: HomeViewModel.UiState, actions: HomeActions) {
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
        "Library",
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
        if (state.bluetooth.connectedDevice == null)
          item("Bluetooth controls", actions.onBluetoothControls)()
        item("File explorer", actions.onFileExplorer)()
        item("Bookmarks", actions.onBookmarks)()
      }
    }
  }
}

/** Only shown while headphones are connected, with the one thing to do with them. */
@Composable
private fun HeadphonesBar(state: HomeViewModel.UiState, actions: HomeActions) {
  val usb = state.attachedDevice
  val bluetooth = state.bluetooth.connectedDevice
  if (usb == null && bluetooth == null) return
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier =
      Modifier.fillMaxWidth()
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ),
  ) {
    Row(
      Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(if (usb != null) Icons.Filled.Usb else Icons.Filled.Bluetooth, contentDescription = null)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          usb?.productName ?: bluetooth?.name ?: "Headphones",
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (usb == null && bluetooth?.batteryPercent != null)
          Text("${bluetooth.batteryPercent}% battery", style = MaterialTheme.typography.bodySmall)
      }
      if (usb != null) Button(onClick = actions.onCopyToHeadphones) { Text("Copy") }
      else TextButton(onClick = actions.onBluetoothControls) { Text("Controls") }
    }
  }
}

@Composable
private fun EmptyLibrary(
  suggestions: List<SourceSuggestion>,
  actions: HomeActions,
  modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      Modifier.widthIn(max = 480.dp).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Icon(
        Icons.Filled.LibraryMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
      )
      Text("Add your first source", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Music, audiobooks and podcasts you add here are ready to copy to your headphones.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedCard(Modifier.fillMaxWidth()) { SourceOptions(suggestions, actions) {} }
    }
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
) {
  val library = podcasts.library
  val playlist = library.playlist ?: return
  val ready = library.manifests.count { m -> m.sections.all { it.id in podcasts.readySectionIds } }
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
        advancedPosition(episode, library.completions),
        downloaded =
          library.manifests
            .firstOrNull { it.episode.key == episode.key }
            ?.sections
            ?.all { it.id in podcasts.readySectionIds } == true,
        onClick = actions.onOpenPodcasts,
      )
    }
  }
}

@Composable
private fun EpisodeRow(episode: Episode, position: Long, downloaded: Boolean, onClick: () -> Unit) {
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
    modifier = Modifier.clickable(onClick = onClick),
  )
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

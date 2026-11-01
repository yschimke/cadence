package ee.schimke.cadence.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ee.schimke.cadence.devices.rememberFileExplorerOpenLauncher
import kotlinx.coroutines.launch

@Composable
fun rememberPodcastController(): PodcastController {
  val platform = rememberPodcastPlatform()
  val controller = remember(platform) { platform.controller() }
  LaunchedEffect(controller) { controller.run { if (!loaded.value) load() } }
  return controller
}

/** The podcast services you can add, one at a time. */
enum class PodcastProvider(val title: String, val detail: String, val icon: ImageVector) {
  PocketCasts("Pocket Casts", "Up Next, in-progress episodes or a playlist", Icons.Filled.Podcasts),
  Rss("Podcast feed", "The newest episodes of any public RSS feed", Icons.Filled.RssFeed),
  Gpodder(
    "gpodder or Nextcloud",
    "Episodes in progress in AntennaPod, Kasts and other apps that sync",
    Icons.Filled.Sync,
  ),
  Export(
    "Overcast or a saved export",
    "Paste an Overcast All data export",
    Icons.Filled.ContentPaste,
  ),
}

/** Runs [block], reports anything worth saying, and returns whether it worked. */
private suspend fun PodcastController.attempt(
  snackbar: SnackbarHostState,
  block: suspend PodcastController.() -> Unit,
): Boolean {
  val ok = run(block)
  if (!ok || status.value != "Saved") snackbar.showSnackbar(status.value)
  return ok
}

/** Pick one podcast service, fill in only what it needs, then go back to the library. */
@Composable
fun AddPodcastsScreen(
  controller: PodcastController,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  initialProvider: PodcastProvider? = null,
) {
  val local by controller.state.collectAsState()
  val busy by controller.busy.collectAsState()
  val status by controller.status.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var provider by rememberSaveable { mutableStateOf(initialProvider) }
  var latest by rememberSaveable { mutableStateOf(local.library.feedLatest) }
  var pending by remember { mutableStateOf<String?>(null) }
  val working = busy || pending != null
  fun import(block: suspend PodcastController.() -> Unit) {
    if (pending != null || controller.busy.value) return
    pending = "Adding podcasts…"
    scope.launch {
      try {
        if (controller.run(block)) onDone() else snackbar.showSnackbar(controller.status.value)
      } finally {
        pending = null
      }
    }
  }
  fun action(block: suspend PodcastController.() -> Unit) {
    if (pending != null || controller.busy.value) return
    pending = "Connecting to Pocket Casts…"
    scope.launch {
      val ok: Boolean
      try {
        ok = controller.run(block)
      } finally {
        pending = null
      }
      if (!ok || controller.status.value != "Saved") snackbar.showSnackbar(controller.status.value)
    }
  }
  Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      if (working) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(pending ?: status, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
      }
      val chosen = provider
      if (chosen == null) {
        Text(
          "Add podcasts",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(16.dp),
        )
        PodcastProvider.entries
          .filter { it != PodcastProvider.PocketCasts || controller.platform.pocketCastsApi }
          .forEach { option ->
            ListItem(
              leadingContent = { Icon(option.icon, contentDescription = null) },
              headlineContent = { Text(option.title) },
              supportingContent = { Text(option.detail) },
              modifier = Modifier.clickable { provider = option },
            )
          }
        return@Column
      }
      Row(
        Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(enabled = !working, onClick = { provider = null }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Other podcast sources")
        }
        Text(chosen.title, style = MaterialTheme.typography.headlineSmall)
      }
      Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (chosen != PodcastProvider.Export)
          EpisodeCount(latest, enabled = !working) { latest = it }
        when (chosen) {
          PodcastProvider.PocketCasts ->
            PocketCastsForm(controller, local, working, pending, latest, ::import, ::action)
          PodcastProvider.Rss ->
            RssForm(local, working) { url -> import { importRss(url, latest) } }
          PodcastProvider.Gpodder ->
            GpodderForm(local, working) { account, password ->
              import { importGpodder(account, password, latest) }
            }
          PodcastProvider.Export ->
            ExportForm(working) { text -> import { importText(text, latest) } }
        }
      }
    }
  }
}

@Composable
private fun EpisodeCount(latest: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Up to $latest episodes", Modifier.weight(1f))
    TextButton(enabled = enabled && latest > 1, onClick = { onChange(latest - 1) }) {
      Text("Fewer")
    }
    TextButton(
      enabled = enabled && latest < MAX_FEED_EPISODES,
      onClick = { onChange(latest + 1) },
    ) {
      Text("More")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PocketCastsForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val lists by controller.pocketCastsLists.collectAsState()
  val loaded by controller.loaded.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(local.library.sourceList.ifBlank { POCKET_CASTS_UP_NEXT })
  }
  var web by rememberSaveable { mutableStateOf(false) }
  var email by rememberSaveable { mutableStateOf(local.pocketCasts.email) }
  var password by remember { mutableStateOf("") }
  // A session saved earlier needs its lists; signing in loads them itself, so this isn't keyed on
  // the session (a second load queued behind a failed sign-in would hide its message).
  LaunchedEffect(loaded) {
    if (controller.state.value.pocketCasts.signedIn && controller.pocketCastsLists.value.isEmpty())
      controller.run { refreshPocketCastsLists() }
  }
  if (web)
    Dialog(
      onDismissRequest = { web = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(Modifier.fillMaxSize()) {
        Column {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Sign in to Pocket Casts",
              Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { web = false }) { Text("Cancel") }
          }
          PocketCastsWebSignIn(
            onCookie = {
              web = false
              action { usePocketCastsCookie(it) }
            },
            onCapture = {
              web = false
              action { usePocketCastsCapture(it) }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )
        }
      }
    }
  if (local.pocketCasts.signedIn || lists.isNotEmpty()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (local.pocketCasts.signedIn)
          "Signed in" +
            local.pocketCasts.email.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty()
        else "Read from the Pocket Casts web player",
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(enabled = !busy, onClick = { action { signOutPocketCasts() } }) {
        Text("Sign out")
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      lists.forEach {
        FilterChip(list == it.id, { list = it.id }, enabled = !busy, label = { Text(it.title) })
      }
    }
    Button(
      enabled = !busy && lists.any { it.id == list },
      onClick = { import { importPocketCasts(list, latest) } },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    return
  }
  Text(
    "Cadence reads your account through the Pocket Casts web player, which isn't a public API and may change.",
    style = MaterialTheme.typography.bodyMedium,
  )
  if (controller.platform.pocketCastsServer != null)
    Text(
      "Your Wasmo signs in and keeps the sign-in, so Cadence is signed in on every browser you open it in. Your password isn't stored.",
      style = MaterialTheme.typography.bodyMedium,
    )
  if (pocketCastsWebSignIn)
    Button(enabled = !busy, onClick = { web = true }) { Text("Sign in on pocketcasts.com") }
  OutlinedTextField(
    email,
    { email = it },
    enabled = !busy,
    label = { Text("Email") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    password,
    { password = it },
    enabled = !busy,
    label = { Text("Password") },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedButton(
    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
    onClick = {
      val supplied = password
      password = ""
      action { signInPocketCasts(email, supplied) }
    },
  ) {
    Text(if (pending != null) "Signing in…" else "Sign in with password")
  }
  Text(
    if (pocketCastsWebSignIn)
      "Google blocks its sign-in inside apps. If you use Continue with Google, set a Pocket Casts password with Forgot password first."
    else
      "Accounts created with Google or Apple have no password until you set one with Forgot password on pocketcasts.com.",
    style = MaterialTheme.typography.bodySmall,
  )
}

@Composable
private fun RssForm(local: LocalPodcastState, busy: Boolean, onImport: (String) -> Unit) {
  var url by rememberSaveable { mutableStateOf(local.library.feedUrl) }
  OutlinedTextField(
    url,
    { url = it },
    label = { Text("Feed URL (https://…)") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  Text(
    "Feeds have no listening position, so episodes start from the beginning.",
    style = MaterialTheme.typography.bodySmall,
  )
  Button(enabled = !busy && url.isNotBlank(), onClick = { onImport(url.trim()) }) { Text("Add") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GpodderForm(
  local: LocalPodcastState,
  busy: Boolean,
  onImport: (GpodderAccount, String) -> Unit,
) {
  var flavor by rememberSaveable { mutableStateOf(local.gpodder.flavor) }
  var server by rememberSaveable { mutableStateOf(local.gpodder.server) }
  var user by rememberSaveable { mutableStateOf(local.gpodder.user) }
  var password by remember { mutableStateOf("") }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    GpodderFlavor.entries.forEach {
      FilterChip(
        flavor == it,
        { flavor = it },
        label = {
          Text(if (it == GpodderFlavor.Nextcloud) "Nextcloud" else "gpodder.net or oPodSync")
        },
      )
    }
  }
  OutlinedTextField(
    server,
    { server = it },
    label = {
      Text(
        if (flavor == GpodderFlavor.Nextcloud) "Nextcloud address (https://…)"
        else "Server, e.g. https://gpodder.net"
      )
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    user,
    { user = it },
    label = { Text("User name") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    password,
    { password = it },
    label = { Text(if (flavor == GpodderFlavor.Nextcloud) "App password" else "Password") },
    supportingText = { Text("Kept only until you close the app") },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier.fillMaxWidth(),
  )
  Button(
    enabled = !busy && server.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
    onClick = {
      val supplied = password
      password = ""
      onImport(GpodderAccount(flavor, server.trim(), user.trim()), supplied)
    },
  ) {
    Text("Add")
  }
}

@Composable
private fun ExportForm(busy: Boolean, onImport: (String) -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  Text(
    "In Overcast choose Account → Export OPML (All data), then paste the file here. A Cadence or Pocket Casts helper export works too.",
    style = MaterialTheme.typography.bodyMedium,
  )
  OutlinedTextField(
    text,
    { text = it },
    label = { Text("Export") },
    maxLines = 6,
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Export" },
  )
  Button(enabled = !busy && text.isNotBlank(), onClick = { onImport(text) }) { Text("Add") }
}

/** One podcast source's episodes: download them, mark what you've heard, copy to headphones. */
@Composable
fun PodcastScreen(
  controller: PodcastController,
  onChangeSource: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val local by controller.state.collectAsState()
  val busy by controller.busy.collectAsState()
  val status by controller.status.collectAsState()
  KeepPodcastScreenAwake(busy)
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  fun action(block: suspend PodcastController.() -> Unit) {
    scope.launch { controller.attempt(snackbar, block) }
  }
  val selectTarget = rememberFileExplorerOpenLauncher { _, path ->
    action { setTarget(path.toString()) }
  }
  val library = local.library
  val playlist = library.playlist
  Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (busy)
        item {
          LinearProgressIndicator(Modifier.fillMaxWidth())
          Text(status, style = MaterialTheme.typography.bodySmall)
        }
      if (playlist == null) {
        item {
          Text("No podcasts yet", style = MaterialTheme.typography.headlineSmall)
          Spacer(Modifier.height(8.dp))
          Button(onClick = onChangeSource) { Text("Add podcasts") }
        }
        return@LazyColumn
      }
      item {
        Text(playlist.name, style = MaterialTheme.typography.headlineMedium)
        Text(
          "Updated ${library.lastBackendSync.take(16).replace('T', ' ')}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        val prepared = library.manifests.isNotEmpty() && local.readyForDevice()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!prepared)
            Button(enabled = !busy, onClick = { action { prepare() } }) { Text("Download") }
          else
            Button(
              enabled = !busy,
              onClick = {
                if (local.target.isBlank()) selectTarget() else action { copyToDevice() }
              },
            ) {
              Text("Copy to headphones")
            }
          if (controller.canRefresh)
            OutlinedButton(enabled = !busy, onClick = { action { refreshSource() } }) {
              Text("Refresh")
            }
        }
        if (prepared && local.target.isBlank())
          Text(
            "You'll pick the headphones' storage first.",
            style = MaterialTheme.typography.bodySmall,
          )
      }
      items(playlist.episodes, key = { it.key }, contentType = { "episode" }) { episode ->
        EpisodeCard(
          episode,
          library.manifests.firstOrNull { it.episode.key == episode.key },
          local,
          busy,
          controller.platform,
          ::action,
        )
      }
      item { AudioOptions(controller, library, busy, ::action) }
      item {
        Expandable("Advanced settings") {
          OvernightPodcastOptions()
          if (local.target.isNotBlank()) {
            Text("Headphone storage selected", style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !busy, onClick = selectTarget) { Text("Change headphone storage") }
            HeadphoneAssociation(local.target)
          }
          if (controller.platform.splitFormats.isNotEmpty())
            PodcastTrimming(library, busy, ::action)
        }
      }
      item { TextButton(onClick = onChangeSource) { Text("Use a different podcast source") } }
    }
  }
}

@Composable
private fun PodcastTrimming(
  library: PodcastLibrary,
  busy: Boolean,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val shows =
    library.playlist
      ?.episodes
      .orEmpty()
      .filter { it.podcastId.isNotBlank() }
      .distinctBy { it.podcastId }
  Expandable("Trim podcast intros and outros") {
    Text(
      "Skip a fixed number of seconds for every episode of a podcast. Changes apply when audio is next prepared."
    )
    if (shows.isEmpty()) Text("Refresh your podcast source to load show information.")
    shows.forEach { episode ->
      key(episode.podcastId) {
        val saved = library.podcastTrims[episode.podcastId] ?: PodcastTrim()
        var start by
          rememberSaveable(episode.podcastId, saved.startSeconds) {
            mutableStateOf(saved.startSeconds.toString())
          }
        var end by
          rememberSaveable(episode.podcastId, saved.endSeconds) {
            mutableStateOf(saved.endSeconds.toString())
          }
        Text(
          episode.podcastTitle.ifBlank { "Podcast: ${episode.title}" },
          style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
          start,
          { start = it },
          label = { Text("Skip at start (seconds)") },
          singleLine = true,
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          end,
          { end = it },
          label = { Text("Skip at end (seconds)") },
          singleLine = true,
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        )
        val first = start.toIntOrNull()
        val last = end.toIntOrNull()
        Button(
          enabled =
            !busy &&
              first != null &&
              last != null &&
              first in 0..3600 &&
              last in 0..3600 &&
              PodcastTrim(first, last) != saved,
          onClick = {
            if (first != null && last != null)
              action { configurePodcastTrim(episode.podcastId, PodcastTrim(first, last)) }
          },
        ) {
          Text("Save trim")
        }
      }
    }
  }
}

@Composable
private fun EpisodeCard(
  episode: Episode,
  manifest: Manifest?,
  local: LocalPodcastState,
  busy: Boolean,
  platform: PodcastPlatform,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val library = local.library
  val position = episodePosition(episode, library, manifest?.config)
  var showParts by rememberSaveable(episode.key) { mutableStateOf(false) }
  var showDetails by rememberSaveable(episode.key) { mutableStateOf(false) }
  var details by remember(episode.key) { mutableStateOf<EpisodeDetails?>(null) }
  var detailsLoading by remember(episode.key) { mutableStateOf(false) }
  var detailsFailed by remember(episode.key) { mutableStateOf(false) }
  val uriHandler = LocalUriHandler.current
  LaunchedEffect(showDetails, episode.key) {
    if (showDetails && details == null) {
      detailsLoading = true
      detailsFailed = false
      try {
        details = loadEpisodeDetails(platform, episode)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (_: Exception) {
        detailsFailed = true
      } finally {
        detailsLoading = false
      }
    }
  }
  var draft by remember(episode.key) { mutableStateOf<Float?>(null) }
  val displayedPosition = draft?.let { scrubPosition(it, episode.durationMs) } ?: position
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (episode.podcastTitle.isNotBlank()) {
        Text(episode.podcastTitle, style = MaterialTheme.typography.labelLarge)
      }
      Text(
        episode.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      TextButton(onClick = { showDetails = !showDetails }) {
        Text(if (showDetails) "Hide episode details" else "Episode details")
      }
      if (showDetails) {
        if (episode.published.isNotBlank())
          Text(episode.published, style = MaterialTheme.typography.labelMedium)
        when {
          detailsLoading ->
            Text("Loading episode details…", style = MaterialTheme.typography.bodySmall)
          detailsFailed ->
            Text(
              "Couldn’t load episode details. Close and reopen to retry.",
              style = MaterialTheme.typography.bodySmall,
            )
          details != null -> {
            val info = details!!
            if (info.summary.isNotBlank())
              Text(info.summary, style = MaterialTheme.typography.bodyMedium)
            else Text("No episode summary available.", style = MaterialTheme.typography.bodySmall)
            info.transcriptUrl?.let { url ->
              TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text("Open transcript")
              }
            }
          }
        }
      }
      Text(
        "${formatTime(displayedPosition)} of ${formatTime(episode.durationMs)} heard",
        style = MaterialTheme.typography.bodyMedium,
      )
      Slider(
        value = draft ?: (position.toFloat() / episode.durationMs).coerceIn(0f, 1f),
        onValueChange = { draft = it },
        onValueChangeFinished = {
          draft?.let { fraction ->
            val selected = scrubPosition(fraction, episode.durationMs)
            action {
              try {
                setEpisodePosition(episode.key, selected)
              } finally {
                draft = null
              }
            }
          }
        },
        enabled = !busy,
        modifier =
          Modifier.fillMaxWidth().semantics { contentDescription = "Position in ${episode.title}" },
      )
      if (
        episode.backend == "gpodder" ||
          (episode.backend == "pocket-casts" &&
            local.pocketCasts.signedIn &&
            !local.pocketCasts.server)
      ) {
        val sent = local.sentPositions[episode.key]
        if (position > episode.positionMs && position != sent)
          TextButton(
            enabled = !busy,
            onClick = { action { sendPosition(episode.key) } },
            modifier =
              Modifier.semantics { contentDescription = "Send position for ${episode.title}" },
          ) {
            Text("Send ${formatTime(position)} to your other apps")
          }
        else if (sent != null)
          Text(
            "Your other apps have ${formatTime(sent)}",
            style = MaterialTheme.typography.bodySmall,
          )
      }
      if (manifest != null) {
        TextButton(onClick = { showParts = !showParts }) {
          Text(if (showParts) "Hide parts" else "Show ${manifest.sections.size} parts")
        }
      }
      if (showParts)
        manifest?.sections?.forEach { section ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "${formatTime(section.startMs)}–${formatTime(section.endMs)}" +
                if (section.id in local.readySectionIds) "" else " · not downloaded",
              Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
    }
  }
}

@Composable
private fun Expandable(
  title: String,
  initiallyExpanded: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  var open by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
  OutlinedCard(Modifier.fillMaxWidth()) {
    ListItem(
      headlineContent = { Text(title) },
      trailingContent = { Text(if (open) "Hide" else "Show") },
      modifier = Modifier.clickable { open = !open },
    )
    if (open)
      Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
      )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioOptions(
  controller: PodcastController,
  library: PodcastLibrary,
  busy: Boolean,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val formats = controller.platform.splitFormats
  var chunks by rememberSaveable { mutableStateOf(library.config.chunkMinutes.toString()) }
  LaunchedEffect(library.config.chunkMinutes) { chunks = library.config.chunkMinutes.toString() }
  Expandable("Audio options") {
    if (formats.isEmpty()) {
      Text("This version copies whole episodes; splitting needs the Android or desktop app.")
      return@Expandable
    }
    LabeledCheckbox("Skip what you've already heard", library.config.skipListened, !busy) {
      action { configure(library.config.copy(skipListened = it)) }
    }
    LabeledCheckbox("Split at your listening position", library.config.splitAtPosition, !busy) {
      action { configure(library.config.copy(splitAtPosition = it)) }
    }
    OutlinedTextField(
      chunks,
      {
        chunks = it
        it
          .toIntOrNull()
          ?.takeIf { minutes -> minutes in 0..120 }
          ?.let { minutes -> action { configure(library.config.copy(chunkMinutes = minutes)) } }
      },
      label = { Text("Split into parts of (minutes)") },
      supportingText = { Text("0 keeps each episode in one piece") },
      enabled = !busy,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (formats.size > 1)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.sorted().forEach { format ->
          FilterChip(
            library.config.chunkFormat == format,
            { action { configure(library.config.copy(chunkFormat = format)) } },
            label = { Text(format.uppercase()) },
          )
        }
      }
  }
}

@Composable
private fun CloudBackup(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  action: (suspend PodcastController.() -> Unit) -> Unit,
  initiallyExpanded: Boolean = false,
) {
  val signedIn by controller.signedIn.collectAsState()
  var url by rememberSaveable { mutableStateOf(local.cloudUrl) }
  var key by rememberSaveable { mutableStateOf(local.cloudKey) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var providerSelected by rememberSaveable { mutableStateOf(local.cloudUrl.isNotBlank()) }
  Expandable(if (signedIn) "Cloud sync" else "Connect a cloud sync provider", initiallyExpanded) {
    Text(
      "Share episodes and progress across your devices. Audio stays on each device.",
      style = MaterialTheme.typography.bodyMedium,
    )
    if (!signedIn) {
      if (!providerSelected) {
        OutlinedButton(enabled = !busy, onClick = { providerSelected = true }) {
          Text("Connect Supabase")
        }
        return@Expandable
      }
      Text("Connect your Supabase project", style = MaterialTheme.typography.titleSmall)
      OutlinedTextField(
        url,
        { url = it },
        label = { Text("Project URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        key,
        { key = it },
        label = { Text("Publishable key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        email,
        { email = it },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        password,
        { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        enabled =
          !busy &&
            url.isNotBlank() &&
            key.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank(),
        onClick = {
          val supplied = password
          password = ""
          action {
            if (state.value.cloudUrl != url.trim() || state.value.cloudKey != key.trim())
              configureCloud(url.trim(), key.trim())
            signIn(email, supplied)
          }
        },
      ) {
        Text("Sign in")
      }
      return@Expandable
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(enabled = !busy, onClick = { action { pushCloud() } }) { Text("Save") }
      OutlinedButton(enabled = !busy, onClick = { action { pullCloud() } }) { Text("Restore") }
      TextButton(enabled = !busy, onClick = { controller.signOut() }) { Text("Sign out") }
    }
  }
}

@Composable
fun CloudSyncScreen(controller: PodcastController) {
  val local by controller.state.collectAsState()
  val busy by controller.busy.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  fun action(block: suspend PodcastController.() -> Unit) {
    scope.launch { controller.attempt(snackbar, block) }
  }
  Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
    Column(
      Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Cross-device backup", style = MaterialTheme.typography.headlineMedium)
      CloudBackup(controller, local, busy, ::action, initiallyExpanded = true)
    }
  }
}

/** The whole row toggles, so the label is the checkbox's accessible name. */
@Composable
private fun LabeledCheckbox(
  label: String,
  checked: Boolean,
  enabled: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    // A null onCheckedChange drops the checkbox's own touch size, so the row keeps 48dp.
    Modifier.fillMaxWidth()
      .heightIn(min = 48.dp)
      .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked, onCheckedChange = null, enabled = enabled)
    Spacer(Modifier.width(12.dp))
    Text(label)
  }
}

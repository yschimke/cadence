package ee.schimke.cadence.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
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
import ee.schimke.cadence.developer.LocalDeveloperMode
import ee.schimke.cadence.devices.rememberFileExplorerOpenLauncher
import kotlinx.coroutines.launch

@Composable
fun rememberPodcastController(): PodcastController {
  val platform = rememberPodcastPlatform()
  val controller = remember(platform) { platform.controller() }
  LaunchedEffect(controller) { controller.run { if (!loaded.value) load() } }
  return controller
}

/**
 * The podcast, audiobook and music services you can add. Signed-in services can be paired together,
 * alongside any number of folders; [kinds] are the sources it stands for, to tell when adding it
 * replaces another.
 */
enum class PodcastProvider(
  val title: String,
  val detail: String,
  val icon: ImageVector,
  val kinds: Set<PodcastSourceKind>,
) {
  PocketCasts(
    "Pocket Casts",
    "Up Next, in-progress episodes or a playlist",
    Icons.Filled.Podcasts,
    setOf(PodcastSourceKind.PocketCasts),
  ),
  LibroFm(
    "Libro.fm",
    "Audiobooks you own on Libro.fm",
    Icons.Filled.Headphones,
    setOf(PodcastSourceKind.LibroFm),
  ),
  Audiobookshelf(
    "Audiobookshelf",
    "Self-hosted audiobooks and podcasts with progress sync",
    Icons.Filled.Headphones,
    setOf(PodcastSourceKind.Audiobookshelf),
  ),
  Subsonic(
    "Subsonic / Navidrome",
    "Music playlists, albums and starred tracks from OpenSubsonic servers",
    Icons.Filled.LibraryMusic,
    setOf(PodcastSourceKind.Subsonic),
  ),
  Jellyfin(
    "Jellyfin",
    "Self-hosted music playlists, albums and audiobooks",
    Icons.Filled.LibraryMusic,
    setOf(PodcastSourceKind.Jellyfin),
  ),
  Plex(
    "Plex",
    "Music playlists and audiobooks from your Plex Media Server",
    Icons.Filled.LibraryMusic,
    setOf(PodcastSourceKind.Plex),
  ),
  LibriVox(
    "LibriVox",
    "Free public-domain audiobooks, no account needed",
    Icons.AutoMirrored.Filled.MenuBook,
    setOf(PodcastSourceKind.Rss),
  ),
  Rss(
    "Podcast feed",
    "The newest episodes of any public RSS feed",
    Icons.Filled.RssFeed,
    setOf(PodcastSourceKind.Rss),
  ),
  Gpodder(
    "gpodder or Nextcloud",
    "Episodes in progress in AntennaPod, Kasts and other apps that sync",
    Icons.Filled.Sync,
    setOf(PodcastSourceKind.Gpodder),
  ),
  Export(
    "Overcast or a saved export",
    "Paste an Overcast All data export",
    Icons.Filled.ContentPaste,
    setOf(PodcastSourceKind.Overcast, PodcastSourceKind.Snapshot),
  ),
}

/**
 * What adding [provider] would replace: the connected service's name when it is a different one
 * with episodes, or null. Folders are never replaced.
 */
fun replacedSource(library: PodcastLibrary, provider: PodcastProvider): String? {
  val current = library.source ?: return null
  if (library.playlist == null || current in provider.kinds) return null
  return current.title
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
    pending = "Connecting to ${provider?.title ?: "the service"}…"
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          Text(
            "Add podcasts, audiobooks or music",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
          )
          Text(
            local.library.playlist?.let {
              if (local.library.source in PAIRED_SOURCES)
                "Signed-in services can be kept together. Another service replaces ${local.library.source.title}; your folders stay."
              else
                "Choose one service. It replaces ${local.library.source.title}; your folders stay."
            } ?: "Choose a service. Your folders stay alongside it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
          )
          PodcastProvider.entries
            .filter { it != PodcastProvider.PocketCasts || controller.platform.pocketCastsApi }
            .filter { it != PodcastProvider.LibroFm || controller.platform.libroFmApi }
            .filter {
              it !in
                setOf(
                  PodcastProvider.Audiobookshelf,
                  PodcastProvider.Jellyfin,
                  PodcastProvider.Plex,
                ) || controller.platform.authenticatedDownloads
            }
            .forEach { option ->
              ListItem(
                leadingContent = { Icon(option.icon, contentDescription = null) },
                headlineContent = { Text(option.title) },
                supportingContent = { Text(option.detail) },
                modifier = Modifier.clickable { provider = option },
              )
            }
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
        Modifier.weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val besideKind = pairedKind(local.library, chosen)
        var beside by rememberSaveable(chosen) { mutableStateOf(true) }
        if (besideKind != null)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(
                "Keep ${local.library.source.title} too",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                "Both sync to your headphones, ${local.library.source.title} first.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Switch(checked = beside, enabled = !working, onCheckedChange = { beside = it })
          }
        val alongside = besideKind != null && beside
        if (!alongside)
          replacedSource(local.library, chosen)?.let { replaced ->
            ReplaceNotice(replaced, local.library.allEpisodes.size)
          }
        if (chosen != PodcastProvider.Export)
          EpisodeCount(
            latest,
            when (chosen) {
              PodcastProvider.LibroFm -> "audiobooks"
              PodcastProvider.Subsonic -> "tracks"
              PodcastProvider.Audiobookshelf,
              PodcastProvider.Jellyfin,
              PodcastProvider.Plex -> "items"
              PodcastProvider.LibriVox -> "chapters"
              else -> "episodes"
            },
            enabled = !working,
          ) {
            latest = it
          }
        when (chosen) {
          PodcastProvider.PocketCasts ->
            PocketCastsForm(
              controller,
              local,
              working,
              pending,
              latest,
              alongside,
              ::import,
              ::action,
            )
          PodcastProvider.LibroFm ->
            LibroFmForm(controller, local, working, pending, latest, alongside, ::import, ::action)
          PodcastProvider.Audiobookshelf ->
            AudiobookshelfForm(
              controller,
              local,
              working,
              pending,
              latest,
              alongside,
              ::import,
              ::action,
            )
          PodcastProvider.Subsonic ->
            SubsonicForm(controller, local, working, pending, latest, alongside, ::import, ::action)
          PodcastProvider.Jellyfin ->
            JellyfinForm(controller, local, working, pending, latest, alongside, ::import, ::action)
          PodcastProvider.Plex ->
            PlexForm(controller, local, working, pending, latest, alongside, ::import, ::action)
          PodcastProvider.LibriVox ->
            LibriVoxForm(controller, working, pending, latest, ::import, ::action)
          PodcastProvider.Rss ->
            RssForm(local, working) { url -> import { importRss(url, latest) } }
          PodcastProvider.Gpodder ->
            GpodderForm(local, working) { account, password ->
              import { importGpodder(account, password, latest) }
            }
          PodcastProvider.Export ->
            ExportForm(working) { text -> import { importText(text, latest) } }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

/**
 * The service [provider] would be added as beside the connected one, or null when it can only
 * replace it: Pocket Casts and Libro.fm pair up, one of each.
 */
fun pairedKind(library: PodcastLibrary, provider: PodcastProvider): PodcastSourceKind? {
  val kind = provider.kinds.singleOrNull()?.takeIf { it in PAIRED_SOURCES } ?: return null
  val main = library.source
  return kind.takeIf { library.playlist != null && main in PAIRED_SOURCES && main != kind }
}

/** Says up front that adding replaces the connected service, so it never happens by surprise. */
@Composable
private fun ReplaceNotice(replaced: String, episodes: Int) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      when (episodes) {
        0 -> "Adding this replaces $replaced."
        1 -> "Adding this replaces the episode from $replaced, with its download."
        else -> "Adding this replaces the $episodes episodes from $replaced, with their downloads."
      } + " Your folders aren't affected.",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(12.dp),
    )
  }
}

@Composable
private fun EpisodeCount(latest: Int, noun: String, enabled: Boolean, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Up to $latest $noun", Modifier.weight(1f))
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
  /** Added beside the connected Libro.fm instead of replacing it. */
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val lists by controller.pocketCastsLists.collectAsState()
  val loaded by controller.loaded.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.PocketCasts }?.list
        ?: local.library.sourceList
          .takeIf { local.library.source == PodcastSourceKind.PocketCasts }
          ?.ifBlank { null }
        ?: POCKET_CASTS_UP_NEXT,
    )
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
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.PocketCasts, list, latest)
          else importPocketCasts(list, latest)
        }
      },
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
private fun LibroFmForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  /** Added beside the connected Pocket Casts instead of replacing it. */
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val books by controller.libroFmBooks.collectAsState()
  val loaded by controller.loaded.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.LibroFm }?.list
        ?: local.library.sourceList.takeIf { local.library.source == PodcastSourceKind.LibroFm }
        ?: LIBRO_FM_LIBRARY,
    )
  }
  var email by rememberSaveable { mutableStateOf(local.libroFm.email) }
  var password by remember { mutableStateOf("") }
  // A sign-in saved earlier needs its library; signing in loads it itself.
  LaunchedEffect(loaded) {
    if (controller.state.value.libroFm.signedIn && controller.libroFmBooks.value.isEmpty())
      controller.run { refreshLibroFmBooks() }
  }
  if (local.libroFm.signedIn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Signed in" + local.libroFm.email.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty(),
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(enabled = !busy, onClick = { action { signOutLibroFm() } }) { Text("Sign out") }
    }
    Column {
      LibroFmChoice(
        "Whole library",
        "Newest purchases first",
        list == LIBRO_FM_LIBRARY,
        enabled = !busy,
      ) {
        list = LIBRO_FM_LIBRARY
      }
      books.forEach { book ->
        LibroFmChoice(
          book.title.ifBlank { "Audiobook" },
          listOfNotNull(
              book.authors.joinToString(", ").takeIf { it.isNotBlank() },
              book.durationSeconds.takeIf { it > 0 }?.let { formatTime(it * 1000) },
            )
            .joinToString(" · "),
          list == book.isbn,
          enabled = !busy,
        ) {
          list = book.isbn
        }
      }
    }
    Button(
      enabled = !busy && (list == LIBRO_FM_LIBRARY || books.any { it.isbn == list }),
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.LibroFm, list, latest)
          else importLibroFm(list, latest)
        }
      },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    Text(
      "Books download as one M4B file and start from the beginning: Libro.fm doesn't share your listening position.",
      style = MaterialTheme.typography.bodySmall,
    )
    return
  }
  Text(
    "Cadence reads your library through the Libro.fm app's API, which isn't public and may change.",
    style = MaterialTheme.typography.bodyMedium,
  )
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
  Button(
    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
    onClick = {
      val supplied = password
      password = ""
      action { signInLibroFm(email, supplied) }
    },
  ) {
    Text(if (pending != null) "Signing in…" else "Sign in")
  }
}

@Composable
private fun LibroFmChoice(
  title: String,
  detail: String,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
) {
  ListItem(
    leadingContent = { RadioButton(selected, onClick = null, enabled = enabled) },
    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supportingContent = detail.takeIf { it.isNotBlank() }?.let { { Text(it) } },
    modifier =
      Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
  )
}

@Composable
private fun AudiobookshelfForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val libraries by controller.audiobookshelfLibraries.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.Audiobookshelf }?.list
        ?: local.library.sourceList.takeIf {
          local.library.source == PodcastSourceKind.Audiobookshelf
        }
        ?: AUDIOBOOKSHELF_IN_PROGRESS,
    )
  }
  var server by rememberSaveable { mutableStateOf(local.audiobookshelf.server) }
  var username by rememberSaveable { mutableStateOf(local.audiobookshelf.username) }
  var password by remember { mutableStateOf("") }
  var apiToken by remember { mutableStateOf("") }
  var useToken by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(local.audiobookshelf.signedIn) {
    if (
      controller.state.value.audiobookshelf.signedIn &&
        controller.audiobookshelfLibraries.value.isEmpty()
    )
      controller.run { refreshAudiobookshelfLibraries() }
  }

  if (local.audiobookshelf.signedIn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Signed in" +
          local.audiobookshelf.username.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty() +
          " · ${local.audiobookshelf.baseUrl.substringAfter("https://")}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      TextButton(enabled = !busy, onClick = { action { signOutAudiobookshelf() } }) {
        Text("Sign out")
      }
    }
    LibroFmChoice(
      "Continue listening",
      "In-progress audiobooks and podcast episodes",
      list == AUDIOBOOKSHELF_IN_PROGRESS,
      !busy,
    ) {
      list = AUDIOBOOKSHELF_IN_PROGRESS
    }
    libraries.forEach { lib ->
      val id = "$AUDIOBOOKSHELF_LIBRARY_PREFIX${lib.id}"
      LibroFmChoice(
        lib.name,
        if (lib.mediaType == "podcast") "Podcast library" else "Audiobook library",
        list == id,
        !busy,
      ) {
        list = id
      }
    }
    Button(
      enabled = !busy,
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.Audiobookshelf, list, latest)
          else importAudiobookshelf(list, latest)
        }
      },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    Text(
      "Downloads DRM-free audio directly from your Audiobookshelf server and supports sending updated progress back.",
      style = MaterialTheme.typography.bodySmall,
    )
    return
  }

  Text(
    "Connect your Audiobookshelf server using its official REST API.",
    style = MaterialTheme.typography.bodyMedium,
  )
  OutlinedTextField(
    server,
    { server = it },
    enabled = !busy,
    label = { Text("Server URL (https://…)") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = !useToken,
      onClick = { useToken = false },
      label = { Text("Username & password") },
    )
    FilterChip(selected = useToken, onClick = { useToken = true }, label = { Text("API token") })
  }
  if (useToken) {
    OutlinedTextField(
      apiToken,
      { apiToken = it },
      enabled = !busy,
      label = { Text("API token") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      enabled = !busy && server.isNotBlank() && apiToken.isNotBlank(),
      onClick = {
        val supplied = apiToken
        apiToken = ""
        action { signInAudiobookshelfToken(server, supplied) }
      },
    ) {
      Text(if (pending != null) "Signing in…" else "Sign in")
    }
  } else {
    OutlinedTextField(
      username,
      { username = it },
      enabled = !busy,
      label = { Text("Username") },
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
    Button(
      enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
      onClick = {
        val supplied = password
        password = ""
        action { signInAudiobookshelf(server, username, supplied) }
      },
    ) {
      Text(if (pending != null) "Signing in…" else "Sign in")
    }
  }
}

@Composable
private fun SubsonicForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val playlists by controller.subsonicPlaylists.collectAsState()
  val albums by controller.subsonicAlbums.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.Subsonic }?.list
        ?: local.library.sourceList.takeIf { local.library.source == PodcastSourceKind.Subsonic }
        ?: SUBSONIC_STARRED,
    )
  }
  var server by rememberSaveable { mutableStateOf(local.subsonic.server) }
  var username by rememberSaveable { mutableStateOf(local.subsonic.username) }
  var password by remember { mutableStateOf("") }
  var apiKey by remember { mutableStateOf("") }
  var useApiKey by rememberSaveable { mutableStateOf(local.subsonic.apiKey.isNotBlank()) }

  LaunchedEffect(local.subsonic.signedIn) {
    if (
      controller.state.value.subsonic.signedIn &&
        controller.subsonicPlaylists.value.isEmpty() &&
        controller.subsonicAlbums.value.isEmpty()
    )
      controller.run { refreshSubsonicCatalog() }
  }

  if (local.subsonic.signedIn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Signed in" +
          local.subsonic.username.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty() +
          " · ${local.subsonic.baseUrl.substringAfter("https://")}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      TextButton(enabled = !busy, onClick = { action { signOutSubsonic() } }) { Text("Sign out") }
    }
    LibroFmChoice("Starred songs", "Your favorited tracks", list == SUBSONIC_STARRED, !busy) {
      list = SUBSONIC_STARRED
    }
    playlists.forEach { pl ->
      val id = "$SUBSONIC_PLAYLIST_PREFIX${pl.id}"
      val tracks = "${pl.songCount} ${if (pl.songCount == 1) "track" else "tracks"}"
      LibroFmChoice(pl.name, "Playlist · $tracks", list == id, !busy) { list = id }
    }
    albums.forEach { album ->
      val id = "$SUBSONIC_ALBUM_PREFIX${album.id}"
      val tracks = "${album.songCount} ${if (album.songCount == 1) "track" else "tracks"}"
      LibroFmChoice(
        album.name,
        listOf(album.artist, tracks).filter { it.isNotBlank() }.joinToString(" · "),
        list == id,
        !busy,
      ) {
        list = id
      }
    }
    Button(
      enabled = !busy,
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.Subsonic, list, latest)
          else importSubsonic(list, latest)
        }
      },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    Text(
      "Works with Navidrome, Airsonic, Gonic, Ampache, Funkwhale and other OpenSubsonic servers.",
      style = MaterialTheme.typography.bodySmall,
    )
    return
  }

  Text(
    "Connect any Subsonic or OpenSubsonic music server (Navidrome, Airsonic, Gonic, Funkwhale).",
    style = MaterialTheme.typography.bodyMedium,
  )
  OutlinedTextField(
    server,
    { server = it },
    enabled = !busy,
    label = { Text("Server URL (https://…)") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = !useApiKey,
      onClick = { useApiKey = false },
      label = { Text("Username & password") },
    )
    FilterChip(selected = useApiKey, onClick = { useApiKey = true }, label = { Text("API key") })
  }
  if (useApiKey) {
    OutlinedTextField(
      apiKey,
      { apiKey = it },
      enabled = !busy,
      label = { Text("OpenSubsonic API key") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      enabled = !busy && server.isNotBlank() && apiKey.isNotBlank(),
      onClick = {
        val supplied = apiKey
        apiKey = ""
        action { signInSubsonic(server, "", "", supplied) }
      },
    ) {
      Text(if (pending != null) "Signing in…" else "Sign in")
    }
  } else {
    OutlinedTextField(
      username,
      { username = it },
      enabled = !busy,
      label = { Text("Username") },
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
    Button(
      enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
      onClick = {
        val supplied = password
        password = ""
        action { signInSubsonic(server, username, supplied) }
      },
    ) {
      Text(if (pending != null) "Signing in…" else "Sign in")
    }
  }
}

@Composable
private fun JellyfinForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val playlists by controller.jellyfinPlaylists.collectAsState()
  val albums by controller.jellyfinAlbums.collectAsState()
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.Jellyfin }?.list
        ?: local.library.sourceList.takeIf { local.library.source == PodcastSourceKind.Jellyfin }
        ?: JELLYFIN_AUDIOBOOKS,
    )
  }
  var server by rememberSaveable { mutableStateOf(local.jellyfin.server) }
  var username by rememberSaveable { mutableStateOf(local.jellyfin.username) }
  var password by remember { mutableStateOf("") }

  LaunchedEffect(local.jellyfin.signedIn) {
    if (
      controller.state.value.jellyfin.signedIn &&
        controller.jellyfinPlaylists.value.isEmpty() &&
        controller.jellyfinAlbums.value.isEmpty()
    )
      controller.run { refreshJellyfinCatalog() }
  }

  if (local.jellyfin.signedIn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Signed in as ${local.jellyfin.username} · ${local.jellyfin.baseUrl.substringAfter("https://")}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      TextButton(enabled = !busy, onClick = { action { signOutJellyfin() } }) { Text("Sign out") }
    }
    LibroFmChoice(
      "Audiobooks",
      "Your Jellyfin audiobooks, newest first",
      list == JELLYFIN_AUDIOBOOKS,
      !busy,
    ) {
      list = JELLYFIN_AUDIOBOOKS
    }
    LibroFmChoice(
      "Favorites",
      "Favorite music tracks and audiobooks",
      list == JELLYFIN_FAVORITES,
      !busy,
    ) {
      list = JELLYFIN_FAVORITES
    }
    playlists.forEach { pl ->
      val id = "$JELLYFIN_PLAYLIST_PREFIX${pl.id}"
      LibroFmChoice(pl.name, "Playlist", list == id, !busy) { list = id }
    }
    albums.forEach { album ->
      val id = "$JELLYFIN_ALBUM_PREFIX${album.id}"
      LibroFmChoice(album.name, album.subtitle.ifBlank { "Album" }, list == id, !busy) { list = id }
    }
    Button(
      enabled = !busy,
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.Jellyfin, list, latest)
          else importJellyfin(list, latest)
        }
      },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    return
  }

  Text(
    "Connect your Jellyfin server to sync music playlists, albums and audiobooks.",
    style = MaterialTheme.typography.bodyMedium,
  )
  OutlinedTextField(
    server,
    { server = it },
    enabled = !busy,
    label = { Text("Server URL (https://…)") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    username,
    { username = it },
    enabled = !busy,
    label = { Text("Username") },
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
  Button(
    enabled = !busy && server.isNotBlank() && username.isNotBlank(),
    onClick = {
      val supplied = password
      password = ""
      action { signInJellyfin(server, username, supplied) }
    },
  ) {
    Text(if (pending != null) "Signing in…" else "Sign in")
  }
}

@Composable
private fun PlexForm(
  controller: PodcastController,
  local: LocalPodcastState,
  busy: Boolean,
  pending: String?,
  latest: Int,
  alongside: Boolean,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val playlists by controller.plexPlaylists.collectAsState()
  val sections by controller.plexSections.collectAsState()
  val pin by controller.plexPin.collectAsState()
  val servers by controller.plexServers.collectAsState()
  val uriHandler = LocalUriHandler.current
  var list by rememberSaveable {
    mutableStateOf(
      local.library.second?.takeIf { it.kind == PodcastSourceKind.Plex }?.list
        ?: local.library.sourceList.takeIf { local.library.source == PodcastSourceKind.Plex }
        ?: "",
    )
  }
  var server by rememberSaveable { mutableStateOf(local.plex.server) }
  var token by remember { mutableStateOf("") }
  var useDirectToken by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(local.plex.signedIn) {
    if (
      controller.state.value.plex.signedIn &&
        controller.plexPlaylists.value.isEmpty() &&
        controller.plexSections.value.isEmpty()
    )
      controller.run { refreshPlexCatalog() }
  }

  LaunchedEffect(playlists, sections) {
    if (list.isBlank()) {
      list =
        playlists.firstOrNull()?.let { "$PLEX_PLAYLIST_PREFIX${it.ratingKey}" }
          ?: sections.firstOrNull()?.let { "$PLEX_SECTION_PREFIX${it.key}" }
          ?: ""
    }
  }

  if (local.plex.signedIn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Connected" +
          local.plex.serverName.takeIf { it.isNotBlank() }?.let { " to $it" }.orEmpty() +
          " · ${local.plex.baseUrl.substringAfter("https://")}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      TextButton(enabled = !busy, onClick = { action { signOutPlex() } }) { Text("Sign out") }
    }
    playlists.forEach { pl ->
      val id = "$PLEX_PLAYLIST_PREFIX${pl.ratingKey}"
      val countText = "${pl.leafCount} ${if (pl.leafCount == 1) "item" else "items"}"
      LibroFmChoice(pl.title, "Playlist · $countText", list == id, !busy) { list = id }
    }
    sections.forEach { section ->
      val id = "$PLEX_SECTION_PREFIX${section.key}"
      LibroFmChoice(section.title, "Audio library (recently added)", list == id, !busy) {
        list = id
      }
    }
    Button(
      enabled = !busy && list.isNotBlank(),
      onClick = {
        import {
          if (alongside) importSecond(PodcastSourceKind.Plex, list, latest)
          else importPlex(list, latest)
        }
      },
    ) {
      Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
    }
    Text(
      "Downloads original DRM-free files from your Plex Media Server and supports sending updated listening progress back.",
      style = MaterialTheme.typography.bodySmall,
    )
    return
  }

  Text(
    "Connect your Plex Media Server to sync music playlists and audiobooks.",
    style = MaterialTheme.typography.bodyMedium,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = !useDirectToken,
      onClick = { useDirectToken = false },
      label = { Text("Sign in with Plex.tv") },
    )
    FilterChip(
      selected = useDirectToken,
      onClick = { useDirectToken = true },
      label = { Text("Server & token") },
    )
  }
  if (!useDirectToken) {
    if (servers.isNotEmpty()) {
      Text("Choose a Plex Media Server:", style = MaterialTheme.typography.titleSmall)
      servers.forEach { choice ->
        ListItem(
          headlineContent = { Text(choice.name) },
          supportingContent = { Text(choice.url.substringAfter("https://")) },
          modifier =
            Modifier.clickable(enabled = !busy) {
              action { signInPlexToken(choice.url, choice.token, choice.name) }
            },
        )
      }
    } else if (pin != null) {
      val currentPin = pin!!
      Text(
        "Approve Cadence on plex.tv, then return here and tap Check sign-in.",
        style = MaterialTheme.typography.bodySmall,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = { uriHandler.openUri(currentPin.authUrl) }) {
          Text("Open Plex sign-in")
        }
        OutlinedButton(enabled = !busy, onClick = { action { pollPlexPin() } }) {
          Text("Check sign-in")
        }
      }
    } else {
      Button(enabled = !busy, onClick = { action { startPlexPin() } }) {
        Text(if (pending != null) "Starting…" else "Sign in with Plex.tv")
      }
    }
  } else {
    OutlinedTextField(
      server,
      { server = it },
      enabled = !busy,
      label = { Text("Server URL (https://…)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      token,
      { token = it },
      enabled = !busy,
      label = { Text("X-Plex-Token") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      enabled = !busy && server.isNotBlank() && token.isNotBlank(),
      onClick = {
        val supplied = token
        token = ""
        action { signInPlexToken(server, supplied) }
      },
    ) {
      Text(if (pending != null) "Connecting…" else "Connect")
    }
  }
}

@Composable
private fun LibriVoxForm(
  controller: PodcastController,
  busy: Boolean,
  pending: String?,
  latest: Int,
  import: (suspend PodcastController.() -> Unit) -> Unit,
  action: (suspend PodcastController.() -> Unit) -> Unit,
) {
  val results by controller.libriVoxResults.collectAsState()
  var query by rememberSaveable { mutableStateOf("") }
  var selectedUrl by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(Unit) {
    if (controller.libriVoxResults.value.isEmpty()) controller.run { searchLibriVoxBooks("") }
  }

  Text(
    "Search 40,000+ free public-domain audiobooks from LibriVox. Chapters import via their official RSS feed.",
    style = MaterialTheme.typography.bodyMedium,
  )
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      query,
      { query = it },
      enabled = !busy,
      label = { Text("Title search") },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
    Button(enabled = !busy, onClick = { action { searchLibriVoxBooks(query) } }) { Text("Search") }
  }
  results.forEach { book ->
    val detail =
      listOf(book.authors, book.totalTime, book.language)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    LibroFmChoice(book.title, detail, selectedUrl == book.rssUrl, !busy) {
      selectedUrl = book.rssUrl
    }
  }
  Button(
    enabled = !busy && selectedUrl.isNotBlank(),
    onClick = { import { importRss(selectedUrl, latest) } },
  ) {
    Text(if (pending == "Adding podcasts…") "Adding…" else "Add")
  }
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
        else "Server, e.g. https://gpodder.net",
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastScreen(
  controller: PodcastController,
  onChangeSource: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val local by controller.state.collectAsState()
  val controllerBusy by controller.busy.collectAsState()
  val controllerStatus by controller.status.collectAsState()
  val work = rememberPodcastWork(controller)
  val busy = controllerBusy || work.active
  val status = work.status ?: controllerStatus
  KeepPodcastScreenAwake(controllerBusy && !work.active)
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
  val developer = LocalDeveloperMode.current.enabled
  Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (busy || work.status != null)
        item {
          if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
        library.second?.let { second ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "Also ${second.name.ifBlank { second.kind.title }} from ${second.kind.title}",
              Modifier.weight(1f),
              style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
              enabled = !busy,
              onClick = { action { removeSecondSource() } },
              colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
              Text("Remove")
            }
          }
        }
      }
      item {
        val prepared = library.manifests.isNotEmpty() && local.readyForDevice()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!prepared) {
            val missing = playlist.episodes.count { it.key !in local.downloadedEpisodeKeys }
            if (work.supportsDownload && missing > 0)
              Button(enabled = !busy, onClick = work.download) { Text("Download $missing") }
            else Button(enabled = !busy, onClick = work.process) { Text("Prepare for headphones") }
          } else
            Button(
              enabled = !busy,
              onClick = {
                if (local.target.isBlank()) selectTarget() else action { copyToDevice() }
              },
            ) {
              Text("Copy to headphones")
            }
          if (controller.canRefresh)
            OutlinedButton(enabled = !busy, onClick = work.refresh) { Text("Refresh") }
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
          showPartsToggle = developer,
        )
      }
      item { AudioOptions(controller, library, busy, ::action, chooseFormat = developer) }
      item {
        Expandable("Advanced settings") {
          OvernightPodcastOptions()
          if (controller.supportsFresh)
            Row(
              Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text("New releases on Home", style = MaterialTheme.typography.titleSmall)
                Text(
                  "New episodes from your Pocket Casts subscriptions, to add to Up Next or dismiss.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              Switch(
                checked = local.showFresh,
                enabled = !busy,
                onCheckedChange = { show ->
                  action {
                    setShowFresh(show)
                    if (show) refreshFresh()
                  }
                },
              )
            }
          if (local.target.isNotBlank()) {
            Text("Headphone storage selected", style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !busy, onClick = selectTarget) { Text("Change headphone storage") }
            HeadphoneAssociation(local.target)
          }
          if (controller.platform.splitFormats.isNotEmpty())
            PodcastTrimming(library, busy, ::action)
        }
      }
      item {
        // Wraps rather than squeezing: side by side, a long label left "Remove podcasts" a few
        // letters a line at larger font sizes.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onChangeSource) { Text("Use a different podcast source") }
          if (playlist != null) RemovePodcastsButton(enabled = !busy) { action { removeSource() } }
        }
      }
    }
  }
}

/** Forgets the imported episodes and prepared audio, after asking: it can't be undone here. */
@Composable
private fun RemovePodcastsButton(enabled: Boolean, onRemove: () -> Unit) {
  var asking by remember { mutableStateOf(false) }
  TextButton(
    enabled = enabled,
    onClick = { asking = true },
    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
  ) {
    Text("Remove podcasts")
  }
  if (asking)
    AlertDialog(
      onDismissRequest = { asking = false },
      title = { Text("Remove podcasts?") },
      text = {
        Text(
          "Cadence forgets these episodes and the audio it prepared. Your podcast app and sign-ins " +
            "are unchanged; add the source again to get them back.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            asking = false
            onRemove()
          },
        ) {
          Text("Remove")
        }
      },
      dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
    )
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
  Expandable("Per-podcast settings") {
    Text(
      "How each podcast is split into files, and seconds to skip at the start and end of every episode. Changes apply when audio is next prepared.",
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
        // Saved on its own: the split applies straight away, trims with their button below.
        SplitChoice(saved.split, enabled = !busy, default = library.config.splitMode) { mode ->
          action { configurePodcastTrim(episode.podcastId, saved.copy(split = mode)) }
        }
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
              saved.copy(startSeconds = first, endSeconds = last) != saved,
          onClick = {
            if (first != null && last != null)
              action {
                configurePodcastTrim(
                  episode.podcastId,
                  saved.copy(startSeconds = first, endSeconds = last),
                )
              }
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
  /** The prepared parts' time ranges, a developer option. */
  showPartsToggle: Boolean = false,
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
            !local.pocketCasts.server) ||
          (episode.backend == AUDIOBOOKSHELF_BACKEND && local.audiobookshelf.signedIn) ||
          (episode.backend == PLEX_BACKEND && local.plex.signedIn)
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
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showDetails = !showDetails }) {
          Text(if (showDetails) "Hide details" else "Episode details")
        }
        if (showPartsToggle && manifest != null)
          TextButton(onClick = { showParts = !showParts }) {
            Text(if (showParts) "Hide parts" else "Show ${manifest.sections.size} parts")
          }
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
      if (showPartsToggle && showParts)
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
  /** The MP3/M4A choice, a developer option; everyone else gets the platform's default. */
  chooseFormat: Boolean = false,
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
    Text("Split into files", style = MaterialTheme.typography.titleSmall)
    SplitChoice(library.config.splitMode, enabled = !busy) { mode ->
      action {
        configure(
          library.config.copy(
            split = mode,
            // Choosing "by time" with no part length yet starts at 30 minutes.
            chunkMinutes =
              if (mode == SplitMode.Time && library.config.chunkMinutes == 0) DEFAULT_PART_MINUTES
              else library.config.chunkMinutes,
          ),
        )
      }
    }
    when (library.config.splitMode) {
      SplitMode.Whole -> Unit
      SplitMode.Time ->
        OutlinedTextField(
          chunks,
          {
            chunks = it
            it
              .toIntOrNull()
              ?.takeIf { minutes -> minutes in 1..120 }
              ?.let { minutes -> action { configure(library.config.copy(chunkMinutes = minutes)) } }
          },
          label = { Text("Part length (minutes)") },
          enabled = !busy,
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      SplitMode.Chapters ->
        Text(
          "Uses the feed's chapter index or the chapter marks in the audio; audiobooks that come " +
            "as separate tracks keep those files as they are. Episodes without chapters stay whole.",
          style = MaterialTheme.typography.bodySmall,
        )
    }
    if (chooseFormat && formats.size > 1)
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

/**
 * Leave as is, by time or by chapter. With [default], a podcast can also follow the library's
 * setting (null).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitChoice(
  selected: SplitMode?,
  enabled: Boolean,
  default: SplitMode? = null,
  onSelect: (SplitMode?) -> Unit,
) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (default != null)
      FilterChip(
        selected == null,
        { onSelect(null) },
        enabled = enabled,
        label = { Text("Default (${splitLabel(default).lowercase()})") },
      )
    SplitMode.entries.forEach { mode ->
      FilterChip(
        selected == mode,
        { onSelect(mode) },
        enabled = enabled,
        label = { Text(splitLabel(mode)) },
      )
    }
  }
}

private fun splitLabel(mode: SplitMode) =
  when (mode) {
    SplitMode.Whole -> "As is"
    SplitMode.Time -> "By time"
    SplitMode.Chapters -> "By chapter"
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

package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.devices.rememberFileExplorerOpenLauncher
import kotlinx.coroutines.launch

@Composable
fun rememberPodcastController(): PodcastController {
  val platform = rememberPodcastPlatform()
  val controller = remember(platform) { PodcastController(platform) }
  LaunchedEffect(controller) { controller.run { load() } }
  return controller
}

@Composable
fun PodcastScreen(controller: PodcastController) {
  val local by controller.state.collectAsState()
  val status by controller.status.collectAsState()
  val busy by controller.busy.collectAsState()
  val signedIn by controller.signedIn.collectAsState()
  val scope = rememberCoroutineScope()
  fun action(block: suspend PodcastController.() -> Unit) {
    scope.launch { controller.run(block) }
  }
  var snapshot by rememberSaveable { mutableStateOf("") }
  var source by rememberSaveable { mutableStateOf(ImportSource.Rss) }
  var latest by rememberSaveable { mutableStateOf(local.library.feedLatest) }
  var flavor by rememberSaveable { mutableStateOf(local.gpodder.flavor) }
  var server by rememberSaveable { mutableStateOf(local.gpodder.server) }
  var user by rememberSaveable { mutableStateOf(local.gpodder.user) }
  var gpodderPassword by remember { mutableStateOf("") }
  var rss by rememberSaveable { mutableStateOf("") }
  var chunks by rememberSaveable { mutableStateOf(local.library.config.chunkMinutes.toString()) }
  var url by rememberSaveable { mutableStateOf(local.cloudUrl) }
  var key by rememberSaveable { mutableStateOf(local.cloudKey) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  val selectTarget = rememberFileExplorerOpenLauncher { _, path ->
    action { setTarget(path.toString()) }
  }
  val library = local.library
  LaunchedEffect(local.cloudUrl, local.cloudKey) {
    url = local.cloudUrl
    key = local.cloudKey
  }
  LaunchedEffect(library.feedUrl) { if (library.feedUrl.isNotBlank()) rss = library.feedUrl }
  LaunchedEffect(library.config.chunkMinutes) { chunks = library.config.chunkMinutes.toString() }
  Surface(Modifier.fillMaxSize()) {
    Column {
      Text(
        status,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
      )
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      LazyColumn(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Text("Podcast sync", style = MaterialTheme.typography.headlineMedium)
          Text(
            "1. Backend → app   2. App → headphones",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(controller.platform.description)
        }
        item {
          ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Import episodes", style = MaterialTheme.typography.titleLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportSource.entries.forEach {
                  FilterChip(source == it, { source = it }, label = { Text(it.label) })
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Episodes: $latest", Modifier.weight(1f))
                TextButton(enabled = latest > 1, onClick = { latest-- }) { Text("Fewer") }
                TextButton(enabled = latest < MAX_FEED_EPISODES, onClick = { latest++ }) {
                  Text("More")
                }
              }
              when (source) {
                ImportSource.Rss -> {
                  Text("The newest episodes of a public podcast feed. Starts from the beginning.")
                  OutlinedTextField(
                    rss,
                    { rss = it },
                    label = { Text("Podcast RSS feed (HTTPS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Button(
                    enabled = !busy && rss.isNotBlank(),
                    onClick = { action { importRss(rss.trim(), latest) } },
                  ) {
                    Text("Import from feed")
                  }
                }
                ImportSource.Gpodder -> {
                  Text(
                    "Unfinished episodes and positions from apps that sync with gpodder.net, oPodSync or Nextcloud GPodder Sync, such as AntennaPod, Kasts and gPodder."
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GpodderFlavor.entries.forEach {
                      FilterChip(
                        flavor == it,
                        { flavor = it },
                        label = {
                          Text(if (it == GpodderFlavor.Nextcloud) "Nextcloud" else "gpodder API")
                        },
                      )
                    }
                  }
                  OutlinedTextField(
                    server,
                    { server = it },
                    label = {
                      Text(
                        if (flavor == GpodderFlavor.Nextcloud) "Nextcloud URL (HTTPS)"
                        else "Server URL, e.g. https://gpodder.net"
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
                  )
                  OutlinedTextField(
                    gpodderPassword,
                    { gpodderPassword = it },
                    label = {
                      Text(
                        if (flavor == GpodderFlavor.Nextcloud) "App password · memory only"
                        else "Password · memory only"
                      )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                  )
                  Button(
                    enabled =
                      !busy &&
                        server.isNotBlank() &&
                        user.isNotBlank() &&
                        gpodderPassword.isNotBlank(),
                    onClick = {
                      val password = gpodderPassword
                      gpodderPassword = ""
                      action {
                        importGpodder(
                          GpodderAccount(flavor, server.trim(), user.trim()),
                          password,
                          latest,
                        )
                      }
                    },
                  ) {
                    Text("Import in-progress episodes")
                  }
                }
                ImportSource.Paste -> {
                  Text(
                    "Paste Overcast's All data export (Account → Export OPML), a Pocket Casts helper export, or a Cadence snapshot."
                  )
                  OutlinedTextField(
                    snapshot,
                    { snapshot = it },
                    label = { Text("Export or snapshot") },
                    maxLines = 5,
                    modifier =
                      Modifier.fillMaxWidth().semantics {
                        contentDescription = "Export or snapshot"
                      },
                  )
                  Button(
                    enabled = !busy && snapshot.isNotBlank(),
                    onClick = { action { importText(snapshot, latest) } },
                  ) {
                    Text("Import")
                  }
                }
              }
              Text(
                "Spotify, Apple Podcasts and Audible do not allow audio export.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
        library.playlist?.let { playlist ->
          item {
            Text(
              "${playlist.name} · ${playlist.episodes.size} episodes",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              "Imported ${library.lastBackendSync.take(16).replace('T', ' ')}",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          if (library.manifests.isEmpty())
            items(playlist.episodes, key = { "imported-" + it.key }) { episode ->
              Text(
                "${episode.title} · ${formatTime(episode.positionMs)} of ${formatTime(episode.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
        }
        item {
          ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Prepare audio", style = MaterialTheme.typography.titleLarge)
              LabeledCheckbox(
                "Split at imported playback position",
                library.config.splitAtPosition,
                enabled = !busy,
              ) { value ->
                action { configure(library.config.copy(splitAtPosition = value)) }
              }
              LabeledCheckbox(
                "Skip what you've already heard",
                library.config.skipListened,
                enabled = !busy && controller.platform.splitFormats.isNotEmpty(),
              ) { value ->
                action { configure(library.config.copy(skipListened = value)) }
              }
              OutlinedTextField(
                chunks,
                { chunks = it },
                label = { Text("Chunk minutes · 0 for off, 1–120") },
              )
              OutlinedButton(
                enabled = !busy && (chunks.toIntOrNull()?.let { it in 0..120 } == true),
                onClick = {
                  action { configure(library.config.copy(chunkMinutes = chunks.toInt())) }
                },
              ) {
                Text("Apply chunk duration")
              }
              Text("Chunk format: ${library.config.chunkFormat.uppercase()}")
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  enabled = !busy,
                  onClick = { action { configure(library.config.copy(chunkFormat = "mp3")) } },
                ) {
                  Text("MP3 · desktop")
                }
                OutlinedButton(
                  enabled = !busy,
                  onClick = { action { configure(library.config.copy(chunkFormat = "m4a")) } },
                ) {
                  Text("M4A · Android/desktop")
                }
              }
              Text(
                "For unsplit audio, turn off both options. Web prepares unsplit audio only. Android chunks use AAC/M4A: check headphone compatibility first."
              )
              Button(
                enabled = !busy && library.playlist != null,
                onClick = { action { prepare() } },
              ) {
                Text("Download & prepare")
              }
              Text("${local.readySectionIds.size} sections ready on this device")
            }
          }
        }
        library.manifests.forEach { manifest ->
          item(key = manifest.episode.key) {
            val episode = manifest.episode
            val position = advancedPosition(episode, library.completions)
            Text(episode.title, style = MaterialTheme.typography.titleMedium)
            Text("Listened to ${formatTime(position)} of ${formatTime(episode.durationMs)}")
            if (episode.backend == "gpodder") {
              val sent = local.sentPositions[episode.key]
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  if (sent != null) "Sent ${formatTime(sent)} to gpodder"
                  else "Your other apps still show ${formatTime(episode.positionMs)}",
                  Modifier.weight(1f),
                  style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                  enabled = !busy && position > episode.positionMs && position != sent,
                  onClick = { action { sendPosition(episode.key) } },
                  modifier =
                    Modifier.semantics {
                      contentDescription = "Send position for ${episode.title}"
                    },
                ) {
                  Text("Send position")
                }
              }
            } else
              Text(
                "Position stays in Cadence; ${episode.backend} does not accept updates",
                style = MaterialTheme.typography.bodySmall,
              )
          }
          items(manifest.sections, key = { it.id }) { section ->
            val played =
              library.completions.any {
                it.sectionId == section.id && it.observedRevision == manifest.episode.revision
              }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                "${formatTime(section.startMs)}–${formatTime(section.endMs)}" +
                  if (section.id in local.readySectionIds) " · Ready" else " · Not downloaded"
              )
              TextButton(
                enabled = !busy && !played,
                onClick = { action { markPlayed(section) } },
                modifier =
                  Modifier.semantics {
                    contentDescription =
                      "Mark ${formatTime(section.startMs)} to ${formatTime(section.endMs)} of ${manifest.episode.title} played"
                  },
              ) {
                Text(if (played) "Played locally" else "Mark played")
              }
            }
          }
        }
        item {
          ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Sync to headphones", style = MaterialTheme.typography.titleLarge)
              Text(
                "Select the OS-mounted volume. Copies go only to Cadence/podcasts. Unrelated files are preserved; stale Cadence sections are retained."
              )
              Text(if (local.target.isBlank()) "No target selected" else "Target: ${local.target}")
              OutlinedButton(enabled = !busy, onClick = selectTarget) {
                Text("Choose headphone volume")
              }
              Button(
                enabled = !busy && local.readyForDevice() && local.target.isNotBlank(),
                onClick = { action { copyToDevice() } },
              ) {
                Text("Copy prepared audio to USB")
              }
              Text("${local.copiedSectionIds.size} sections copied in the last successful transfer")
            }
          }
        }
        item {
          ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Cross-device continuity", style = MaterialTheme.typography.titleLarge)
              Text(
                "Optional Supabase metadata sync. Requires your own project and the setup SQL; nothing is provisioned automatically. Audio stays local. Playlist URLs and listening history are uploaded to your account."
              )
              OutlinedTextField(
                url,
                { url = it },
                label = { Text("Supabase HTTPS project URL") },
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                key,
                { key = it },
                label = { Text("Publishable / anon key (never service role)") },
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedButton(enabled = !busy, onClick = { action { configureCloud(url, key) } }) {
                Text("Save cloud configuration")
              }
              OutlinedTextField(email, { email = it }, label = { Text("Account email") })
              OutlinedTextField(
                password,
                { password = it },
                label = { Text("Password · held in memory only") },
                visualTransformation = PasswordVisualTransformation(),
              )
              Button(
                enabled = !busy && local.cloudUrl.isNotBlank(),
                onClick = {
                  val suppliedPassword = password
                  password = ""
                  action { signIn(email, suppliedPassword) }
                },
              ) {
                Text("Sign in")
              }
              Text(
                "Sign in each app session. Create/confirm your account in your project's auth flow first."
              )
              Text(if (signedIn) "Signed in for this session" else "Not signed in")
              if (signedIn)
                TextButton(enabled = !busy, onClick = { controller.signOut() }) { Text("Sign out") }
              OutlinedButton(enabled = !busy && signedIn, onClick = { action { pullCloud() } }) {
                Text("Restore cloud configuration & merge played sections")
              }
              OutlinedButton(enabled = !busy && signedIn, onClick = { action { pushCloud() } }) {
                Text("Save to cloud (revision ${local.cloudRevision})")
              }
              Text(
                "Concurrent edits stop the save. Restore remote configuration, reapply your settings, then retry. Device folders and download readiness never sync to another device."
              )
            }
          }
        }
        item { Spacer(Modifier.height(24.dp)) }
      }
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
    Modifier.fillMaxWidth()
      .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked, onCheckedChange = null, enabled = enabled)
    Spacer(Modifier.width(12.dp))
    Text(label)
  }
}

private enum class ImportSource(val label: String) {
  Rss("RSS feed"),
  Gpodder("gpodder"),
  Paste("Paste export"),
}

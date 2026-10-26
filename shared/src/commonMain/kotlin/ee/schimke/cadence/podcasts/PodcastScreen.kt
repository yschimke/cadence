package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
  LaunchedEffect(library.feedUrl) { rss = library.feedUrl }
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
              Text("Pocket Casts", style = MaterialTheme.typography.titleLarge)
              Text(PocketCastsBackend.capabilities.explanation)
              Text(
                "Spotify and Audible audio export are unavailable. Public podcast RSS with downloadable audio is supported below."
              )
              OutlinedTextField(
                rss,
                { rss = it },
                label = { Text("Public RSS feed (HTTPS)") },
                modifier = Modifier.fillMaxWidth(),
              )
              Button(enabled = !busy && rss.isNotBlank(), onClick = { action { importRss(rss) } }) {
                Text("Sync RSS to app")
              }
              OutlinedTextField(
                snapshot,
                { snapshot = it },
                label = { Text("Playlist snapshot JSON (see setup guide)") },
                maxLines = 5,
                modifier =
                  Modifier.fillMaxWidth().semantics {
                    contentDescription = "Playlist snapshot JSON (see setup guide)"
                  },
              )
              OutlinedButton(
                enabled = !busy && snapshot.isNotBlank(),
                onClick = { action { importSnapshot(snapshot) } },
              ) {
                Text("Import snapshot")
              }
              Text("Last backend import: ${library.lastBackendSync}")
              Text(
                library.playlist?.let { "${it.name} · ${it.episodes.size} episodes" }
                  ?: "No playlist imported"
              )
            }
          }
        }
        item {
          ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Prepare audio", style = MaterialTheme.typography.titleLarge)
              Row {
                Checkbox(
                  library.config.splitAtPosition,
                  { value -> action { configure(library.config.copy(splitAtPosition = value)) } },
                  enabled = !busy,
                )
                Text("Split at imported playback position")
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
            Text(manifest.episode.title, style = MaterialTheme.typography.titleMedium)
            Text(
              "Local contiguous position: ${advancedPosition(manifest.episode, library.completions) / 1000}s / ${manifest.episode.durationMs / 1000}s · Backend writes unavailable"
            )
          }
          items(manifest.sections, key = { it.id }) { section ->
            val played =
              library.completions.any {
                it.sectionId == section.id && it.observedRevision == manifest.episode.revision
              }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                "${section.startMs / 1000}s–${section.endMs / 1000}s" +
                  if (section.id in local.readySectionIds) " · Ready" else " · Not downloaded"
              )
              TextButton(enabled = !busy && !played, onClick = { action { markPlayed(section) } }) {
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

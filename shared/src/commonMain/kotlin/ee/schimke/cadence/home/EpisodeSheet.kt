package ee.schimke.cadence.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.EpisodeDetails
import ee.schimke.cadence.podcasts.formatTime
import kotlinx.coroutines.CancellationException

/** The show's cover art, or a podcast glyph while it loads or when there is none. */
@Composable
fun PodcastArtwork(episode: Episode, size: Dp = 40.dp, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(if (size > 64.dp) 16.dp else 8.dp)
  Surface(
    shape = shape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier.size(size).clip(shape),
  ) {
    val placeholder =
      @Composable {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Filled.Podcasts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size / 2),
          )
        }
      }
    val url = episode.artworkUrl
    if (url == null) placeholder()
    else
      SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { placeholder() },
        error = { placeholder() },
      )
  }
}

/** What Home knows about an episode, opened by tapping its row. */
class EpisodeInfo(
  val episode: Episode,
  /** How far in, in milliseconds; 0 when not started. */
  val position: Long = 0,
  val downloaded: Boolean = false,
  /** Where it sits: "Synced to headphones", "Next in …", "New release". */
  val status: String = "",
  val onPlay: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSheet(
  info: EpisodeInfo,
  loadDetails: suspend (Episode) -> EpisodeDetails,
  onOpenPodcasts: () -> Unit,
  onDismiss: () -> Unit,
) {
  val episode = info.episode
  var details by remember(episode.key) { mutableStateOf<EpisodeDetails?>(null) }
  var failed by remember(episode.key) { mutableStateOf(false) }
  LaunchedEffect(episode.key) {
    try {
      details = loadDetails(episode)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      failed = true
    }
  }
  val uriHandler = LocalUriHandler.current
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      Modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PodcastArtwork(episode, size = 96.dp)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
          if (episode.podcastTitle.isNotBlank())
            Text(
              episode.podcastTitle,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          Text(
            episode.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
          )
        }
      }
      val position = info.position.coerceIn(0, episode.durationMs)
      Text(
        if (position > 0)
          "${formatTime(position)} of ${formatTime(episode.durationMs)} · " +
            "${formatTime(episode.durationMs - position)} left"
        else "${formatTime(episode.durationMs)} · not started",
        style = MaterialTheme.typography.bodyLarge,
      )
      if (position > 0)
        LinearProgressIndicator(
          progress = { position.toFloat() / episode.durationMs },
          modifier = Modifier.fillMaxWidth(),
        )
      val facts =
        listOfNotNull(
          info.status.takeIf { it.isNotBlank() },
          "Downloaded".takeIf { info.downloaded },
          episode.published.take(10).takeIf { it.isNotBlank() },
        )
      if (facts.isNotEmpty())
        Text(
          facts.joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        info.onPlay?.let { play ->
          Button(
            onClick = {
              play()
              onDismiss()
            },
          ) {
            Text(if (position > 0) "Resume on phone" else "Play on phone")
          }
        }
        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenPodcasts()
          },
        ) {
          Text("Podcast settings")
        }
      }
      when {
        failed ->
          Text(
            "Couldn’t load the show notes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        details == null ->
          Text(
            "Loading show notes…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        else -> {
          val loaded = details!!
          if (loaded.summary.isNotBlank())
            Text(loaded.summary, style = MaterialTheme.typography.bodyMedium)
          loaded.transcriptUrl?.let { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
              Text("Open transcript")
            }
          }
        }
      }
    }
  }
}

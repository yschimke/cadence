package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Home's status card, drawn from the same document as the launcher widget: [headline] says what to
 * do next, then the headphones, what's ready on this phone, the battery and [source].
 */
@Composable
expect fun PodcastSyncHomeCard(
  copied: String,
  available: String,
  battery: String?,
  ready: Boolean,
  showSwimming: Boolean,
  headline: String?,
  source: String?,
  attention: Boolean,
)

/** Fallback for platforms that cannot host the Android Remote Compose document. */
@Composable
fun PodcastSyncHomeFallback(
  copied: String,
  available: String,
  battery: String?,
  headline: String? = null,
  source: String? = null,
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      headline?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
      Text(copied, style = MaterialTheme.typography.bodyMedium)
      Text(available, style = MaterialTheme.typography.bodyMedium)
      Text(battery ?: "Battery unavailable", style = MaterialTheme.typography.bodyMedium)
      source?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
  }
}

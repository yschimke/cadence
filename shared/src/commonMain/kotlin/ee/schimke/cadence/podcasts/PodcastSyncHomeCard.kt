package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A button drawn inside the status card; its [id] comes back through `onAction`. */
data class StatusCardButton(val id: String, val label: String, val primary: Boolean = false)

/**
 * What the status card offers besides its status lines: the step's buttons, the "What will change"
 * summary (and its lines once expanded), and the refresh and audio-controls icons.
 */
data class StatusCardExtras(
  val buttons: List<StatusCardButton> = emptyList(),
  /** Summary such as "3 new · 1 removed", or null when the next sync changes nothing. */
  val changes: String? = null,
  /** One line per change, shown while expanded; empty while folded. */
  val changeLines: List<String> = emptyList(),
  val refresh: Boolean = false,
  val audioControls: Boolean = false,
) {
  companion object {
    const val CHANGES = "changes"
    const val REFRESH = "refresh"
    const val AUDIO_CONTROLS = "audio-controls"
  }
}

/**
 * Home's status card, drawn from the same document as the launcher widget: [headline] says what to
 * do next, then the headphones, what's ready on this phone, the battery and [source], with the
 * step's buttons and the next sync's changes from [extras]. Taps report their id to [onAction].
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
  extras: StatusCardExtras = StatusCardExtras(),
  onAction: (String) -> Unit = {},
)

/** Fallback for platforms that cannot host the Android Remote Compose document. */
@Composable
fun PodcastSyncHomeFallback(
  copied: String,
  available: String,
  battery: String?,
  headline: String? = null,
  source: String? = null,
  extras: StatusCardExtras = StatusCardExtras(),
  onAction: (String) -> Unit = {},
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      headline?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
      Text(copied, style = MaterialTheme.typography.bodyMedium)
      Text(available, style = MaterialTheme.typography.bodyMedium)
      Text(battery ?: "Battery unavailable", style = MaterialTheme.typography.bodyMedium)
      source?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      extras.changes?.let { changes ->
        TextButton(onClick = { onAction(StatusCardExtras.CHANGES) }) {
          Text("What will change · $changes")
        }
        extras.changeLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
      Row {
        extras.buttons.forEach { button ->
          if (button.primary) Button(onClick = { onAction(button.id) }) { Text(button.label) }
          else TextButton(onClick = { onAction(button.id) }) { Text(button.label) }
        }
        if (extras.refresh)
          TextButton(onClick = { onAction(StatusCardExtras.REFRESH) }) { Text("Refresh") }
        if (extras.audioControls)
          TextButton(onClick = { onAction(StatusCardExtras.AUDIO_CONTROLS) }) {
            Text("Audio controls")
          }
      }
    }
  }
}

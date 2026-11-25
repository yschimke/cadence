package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The icons the status card's buttons draw in place of their labels, which stay what a screen
 * reader says. Sync is a two-way transfer, unlike refresh's circular arrow beside it.
 */
val StatusButtonIcons: List<Pair<Set<String>, ImageVector>> =
  listOf(
    setOf("sync", "sync-again", "open") to Icons.Filled.SyncAlt,
    setOf("download") to Icons.Filled.Download,
    setOf("process") to Icons.Filled.AutoFixHigh,
    setOf("eject") to Icons.Filled.Eject,
    setOf("add") to Icons.Filled.Add,
    setOf("storage") to Icons.Filled.SdStorage,
    setOf("folders") to Icons.Filled.Folder,
  )

/** The icon button [id] draws, or null to show its label. */
fun statusButtonIcon(id: String): ImageVector? =
  StatusButtonIcons.firstOrNull { id in it.first }?.second

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
 * While a task is [working] the swimmer swims laps, and once it reports how far it has got,
 * [progress] (0 to 1) moves the swimmer across the pool instead.
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
  working: Boolean = false,
  progress: Float? = null,
  onAction: (String) -> Unit = {},
)

/** Fallback for platforms that cannot host the Android Remote Compose document. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastSyncHomeFallback(
  copied: String,
  available: String,
  battery: String?,
  headline: String? = null,
  source: String? = null,
  extras: StatusCardExtras = StatusCardExtras(),
  working: Boolean = false,
  progress: Float? = null,
  onAction: (String) -> Unit = {},
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      headline?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
      Text(copied, style = MaterialTheme.typography.bodyMedium)
      Text(available, style = MaterialTheme.typography.bodyMedium)
      Text(battery ?: "Battery unavailable", style = MaterialTheme.typography.bodyMedium)
      source?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      val bar = Modifier.fillMaxWidth().padding(vertical = 8.dp)
      if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = bar)
      else if (working) LinearProgressIndicator(bar)
      extras.changes?.let { changes ->
        TextButton(onClick = { onAction(StatusCardExtras.CHANGES) }) {
          Text("What will change · $changes")
        }
        extras.changeLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
      // Wraps: three actions plus refresh and audio controls don't fit one row on a phone.
      FlowRow {
        extras.buttons.forEach { button ->
          val label: @Composable () -> Unit = {
            val icon = statusButtonIcon(button.id)
            if (icon != null) Icon(icon, contentDescription = button.label) else Text(button.label)
          }
          if (button.primary) Button(onClick = { onAction(button.id) }) { label() }
          else TextButton(onClick = { onAction(button.id) }) { label() }
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

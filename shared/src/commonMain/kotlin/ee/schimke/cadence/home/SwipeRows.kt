package ee.schimke.cadence.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.formatTime
import kotlinx.coroutines.launch

/** What a swipe does: [run] reports whether it worked, so a failed swipe springs back. */
class SwipeAction(
  val label: String,
  val icon: ImageVector,
  val run: suspend () -> Boolean,
)

/**
 * A row that can be swiped right ([onSwipeRight]) and/or left ([onSwipeLeft]). Both are also
 * offered as accessibility actions, since a swipe alone isn't reachable with a screen reader or
 * switch.
 */
@Composable
fun SwipeActionRow(
  onSwipeRight: SwipeAction?,
  onSwipeLeft: SwipeAction?,
  content: @Composable () -> Unit,
) {
  val state = rememberSwipeToDismissBoxState()
  val scope = rememberCoroutineScope()
  fun perform(action: SwipeAction) {
    scope.launch { if (!action.run()) state.reset() }
  }
  SwipeToDismissBox(
    state = state,
    enableDismissFromStartToEnd = onSwipeRight != null,
    enableDismissFromEndToStart = onSwipeLeft != null,
    onDismiss = { value ->
      when (value) {
        SwipeToDismissBoxValue.StartToEnd -> onSwipeRight?.let(::perform)
        SwipeToDismissBoxValue.EndToStart -> onSwipeLeft?.let(::perform)
        SwipeToDismissBoxValue.Settled -> Unit
      }
    },
    backgroundContent = {
      val direction = state.dismissDirection
      val action =
        when (direction) {
          SwipeToDismissBoxValue.StartToEnd -> onSwipeRight
          SwipeToDismissBoxValue.EndToStart -> onSwipeLeft
          SwipeToDismissBoxValue.Settled -> null
        }
      val colors = MaterialTheme.colorScheme
      val adding = action != null && action === onSwipeLeft
      val container by
        animateColorAsState(
          when {
            action == null -> Color.Transparent
            adding -> colors.primaryContainer
            else -> colors.tertiaryContainer
          }
        )
      Box(
        Modifier.fillMaxSize().background(container).padding(horizontal = 24.dp),
        contentAlignment =
          if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd
          else Alignment.CenterStart,
      ) {
        if (action != null)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              action.icon,
              contentDescription = null,
              tint = if (adding) colors.onPrimaryContainer else colors.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
              action.label,
              style = MaterialTheme.typography.labelLarge,
              color = if (adding) colors.onPrimaryContainer else colors.onTertiaryContainer,
            )
          }
      }
    },
    modifier =
      Modifier.semantics {
        customActions =
          listOfNotNull(onSwipeRight, onSwipeLeft).map { action ->
            CustomAccessibilityAction(action.label) {
              perform(action)
              true
            }
          }
      },
  ) {
    content()
  }
}

/** Archiving from the source: swiping right says "done with this". */
fun archiveAction(run: suspend () -> Boolean) = SwipeAction("Archive", Icons.Filled.Archive, run)

private const val FRESH_SHOWN = 8

/**
 * New episodes to triage: swipe right to dismiss, left to add to the source. Only shown while there
 * is something to decide on, and on Home only once asked for ([onCollapse] folds it away again).
 */
@Composable
fun FreshSection(
  fresh: List<Episode>,
  canAdd: Boolean,
  onAdd: suspend (String) -> Boolean,
  onDismiss: suspend (String) -> Boolean,
  onCollapse: (() -> Unit)? = null,
  onTurnOff: (() -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedCard(Modifier.fillMaxWidth()) {
    Row(
      Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.NewReleases, contentDescription = null, tint = colors.primary)
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "New releases",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          if (canAdd) "Swipe left to add to Up Next, right to dismiss"
          else "Swipe right to dismiss",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      if (onCollapse != null)
        IconButton(onClick = onCollapse) {
          Icon(Icons.Filled.ExpandLess, contentDescription = "Hide new releases")
        }
    }
    if (onTurnOff != null)
      TextButton(
        onClick = onTurnOff,
        modifier = Modifier.padding(start = 48.dp),
      ) {
        Text("Don't suggest new releases")
      }
    fresh.take(FRESH_SHOWN).forEach { episode ->
      key(episode.key) {
        HorizontalDivider()
        SwipeActionRow(
          onSwipeRight =
            SwipeAction("Dismiss", Icons.Filled.VisibilityOff) { onDismiss(episode.key) },
          onSwipeLeft =
            if (canAdd)
              SwipeAction("Add to Up Next", Icons.Filled.PlaylistAdd) { onAdd(episode.key) }
            else null,
        ) {
          FreshRow(episode)
        }
      }
    }
    if (fresh.size > FRESH_SHOWN)
      Text(
        "${fresh.size - FRESH_SHOWN} more after these",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
      )
  }
}

@Composable
private fun FreshRow(episode: Episode) {
  ListItem(
    overlineContent =
      if (episode.podcastTitle.isBlank()) null
      else {
        { Text(episode.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      },
    headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      Text(
        listOfNotNull(
            formatTime(episode.durationMs),
            episode.published.take(10).takeIf { it.isNotBlank() },
          )
          .joinToString(" · ")
      )
    },
    // Opaque, so the swipe background only shows where the row has moved away.
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
  )
}

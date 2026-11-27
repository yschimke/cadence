package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PodcastSyncHomeFallbackTest {
  private fun measureHeight(
    headline: String = "In sync",
    source: String? = "Swim Series · 1 h ago",
    extras: StatusCardExtras = StatusCardExtras(refresh = true),
    working: Boolean = false,
    progress: Float? = null,
  ): Int {
    var height = -1
    val scene =
      ImageComposeScene(width = 720, height = 800) {
        MaterialTheme {
          Box(Modifier.fillMaxWidth().onGloballyPositioned { height = it.size.height }) {
            PodcastSyncHomeFallback(
              copied = "Last copy: 3 eps · 57m · 0 marked",
              available = "Ready: 2 eps · 40m",
              battery = null,
              headline = headline,
              source = source,
              extras = extras,
              working = working,
              progress = progress,
            )
          }
        }
      }
    try {
      scene.render()
    } finally {
      scene.close()
    }
    assertTrue(height > 0, "Expected card to be laid out")
    return height
  }

  @Test
  fun heightStaysConstantAcrossSyncStatusTransitions() {
    val inSync =
      measureHeight(
        headline = "In sync",
        extras = StatusCardExtras(refresh = true),
      )
    val readyToSync =
      measureHeight(
        headline = "Ready to sync",
        extras =
          StatusCardExtras(
            buttons = listOf(StatusCardButton("sync", "Sync now", primary = true)),
            changes = "3 removed",
            refresh = true,
          ),
      )
    val archiving =
      measureHeight(
        headline = "Working…",
        extras = StatusCardExtras(refresh = true),
        working = true,
      )
    val downloadingWithProgress =
      measureHeight(
        headline = "Downloading 1 of 2…",
        extras = StatusCardExtras(refresh = false),
        working = true,
        progress = 0.5f,
      )
    val prepare =
      measureHeight(
        headline = "1 to download",
        extras =
          StatusCardExtras(
            buttons = listOf(StatusCardButton("download", "Download", primary = true)),
            refresh = true,
          ),
      )

    assertEquals(inSync, readyToSync, "Ready to sync should match In sync height")
    assertEquals(inSync, archiving, "Working state should match In sync height")
    assertEquals(
      inSync,
      downloadingWithProgress,
      "Determinate progress should match In sync height",
    )
    assertEquals(inSync, prepare, "Prepare state should match In sync height")
  }

  @Test
  fun expandingChangesGrowsCardBeyondCollapsedBaseline() {
    val collapsed =
      measureHeight(
        headline = "Ready to sync",
        extras =
          StatusCardExtras(
            buttons = listOf(StatusCardButton("sync", "Sync now", primary = true)),
            changes = "2 removed",
            refresh = true,
          ),
      )
    val expanded =
      measureHeight(
        headline = "Ready to sync",
        extras =
          StatusCardExtras(
            buttons = listOf(StatusCardButton("sync", "Sync now", primary = true)),
            changes = "2 removed",
            changeLines = listOf("− Episode 1 · 20m", "− Episode 2 · 18m"),
            refresh = true,
          ),
      )
    assertTrue(expanded > collapsed, "Expanded changes should grow the card")
  }
}

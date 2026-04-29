package ee.schimke.cadence.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun formatLastRefreshed(
  iso: String,
  now: Instant = Clock.System.now(),
  fallback: String = "never refreshed",
): String {
  if (iso.isBlank()) return fallback
  val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
  val seconds = (now - then).inWholeSeconds
  return when {
    seconds < 60L -> "just now"
    seconds < 3600L -> "${seconds / 60} min ago"
    seconds < 86_400L -> "${seconds / 3600} h ago"
    seconds < 604_800L -> "${seconds / 86_400} d ago"
    else -> iso.substringBefore('T')
  }
}

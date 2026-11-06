package ee.schimke.cadence.home

import ee.schimke.cadence.podcasts.DeviceEpisode
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.deviceManifest
import ee.schimke.cadence.podcasts.formatTime

/** One episode-level difference the next sync makes to the headphones' playlist. */
data class HeadphoneSyncChange(
  val kind: Kind,
  val title: String,
  val podcast: String,
  val detail: String,
) {
  enum class Kind {
    Add,
    Update,
    Remove,
  }
}

/**
 * What the next sync changes, by episode: compares the playlist last copied to the headphones with
 * the one a copy would write now. New episodes first, then updated ones, then those leaving. With
 * no record of a previous copy, every episode counts as new.
 */
fun headphoneSyncChanges(state: LocalPodcastState): List<HeadphoneSyncChange> {
  val before = state.lastCopied?.episodes.orEmpty().associateBy { it.episodeKey }
  val after = deviceManifest(state.library).episodes
  val afterKeys = after.map { it.episodeKey }.toSet()
  val added = mutableListOf<HeadphoneSyncChange>()
  val updated = mutableListOf<HeadphoneSyncChange>()
  after.forEach { episode ->
    val previous = before[episode.episodeKey]
    when {
      previous == null -> added += change(HeadphoneSyncChange.Kind.Add, episode, length(episode))
      previous.audioVersion != episode.audioVersion ->
        updated += change(HeadphoneSyncChange.Kind.Update, episode, "New version of the audio")
      previous.sections.map { it.id } != episode.sections.map { it.id } -> {
        val start = episode.sections.minOfOrNull { it.startMs } ?: 0
        val previousStart = previous.sections.minOfOrNull { it.startMs } ?: 0
        updated +=
          change(
            HeadphoneSyncChange.Kind.Update,
            episode,
            if (start != previousStart) "Picks up from ${formatTime(start)}"
            else "Re-split into ${files(episode.sections.size)}",
          )
      }
    }
  }
  val removed =
    before.values
      .filter { it.episodeKey !in afterKeys }
      .map { change(HeadphoneSyncChange.Kind.Remove, it, "Deleted from the headphones") }
  return added + updated + removed
}

private fun change(kind: HeadphoneSyncChange.Kind, episode: DeviceEpisode, detail: String) =
  HeadphoneSyncChange(kind, episode.title, episode.podcastTitle, detail)

private fun length(episode: DeviceEpisode): String {
  val ms = episode.sections.sumOf { it.endMs - it.startMs }
  return "${formatTime(ms)} left · ${files(episode.sections.size)}"
}

private fun files(n: Int) = "$n file" + if (n == 1) "" else "s"

/** "2 new · 1 updated · 3 removed", leaving out kinds with none. */
fun List<HeadphoneSyncChange>.summary(): String =
  listOf(
      HeadphoneSyncChange.Kind.Add to "new",
      HeadphoneSyncChange.Kind.Update to "updated",
      HeadphoneSyncChange.Kind.Remove to "removed",
    )
    .mapNotNull { (kind, label) ->
      count { it.kind == kind }.takeIf { it > 0 }?.let { "$it $label" }
    }
    .joinToString(" · ")

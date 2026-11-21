package ee.schimke.cadence.home

import ee.schimke.cadence.podcasts.CloudSyncStatus
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.readyForDevice
import ee.schimke.cadence.podcasts.upToDateOnDevice

/**
 * The one thing the home screen suggests next for the headphones, from what is set up and whether
 * they are plugged in. Setup steps only appear until they are done, so the daily view stays a
 * single status line and, at most, one button.
 */
sealed interface HeadphoneSyncStep {
  val message: String

  /** Nothing to sync yet; the welcome card offers sources instead. */
  data object NoSource : HeadphoneSyncStep {
    override val message = "Add podcasts, audiobooks or a folder to get started."
  }

  /** First time: the headphones' storage has never been chosen. */
  data object PlugInToSetUp : HeadphoneSyncStep {
    override val message = "Plug in your headphones once to finish setup."
  }

  /** Plugged in, but the storage hasn't been chosen yet. */
  data object ChooseStorage : HeadphoneSyncStep {
    override val message =
      "Headphones connected. Allow access to the whole headphone drive (not a folder on it) to finish setup."
  }

  /** Episodes still need downloading or processing before a copy. */
  data class Prepare(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "New episodes on this phone need processing before they can sync."
      else "New episodes on this phone are processed overnight while charging, or process them now."
  }

  /** Prepared and not on the headphones yet. */
  data class ReadyToCopy(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Not in sync: this phone has episodes your headphones don't."
      else "Not in sync: this phone has new episodes. Plug in your headphones to sync."
  }

  /** The headphones already hold everything prepared. */
  data class UpToDate(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn)
        "In sync with this phone. Before unplugging, tap Eject on the USB drive notification."
      else "In sync with this phone."
  }

  /** Only folders: their copy runs from the folder sync screen. */
  data class Folders(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Headphones connected." else "Plug in your headphones to sync your folders."
  }
}

fun headphoneSyncStep(
  podcasts: LocalPodcastState,
  hasFolders: Boolean,
  pluggedIn: Boolean,
): HeadphoneSyncStep {
  val hasPodcasts = podcasts.library.playlist != null
  return when {
    !hasPodcasts && !hasFolders -> HeadphoneSyncStep.NoSource
    !hasPodcasts -> HeadphoneSyncStep.Folders(pluggedIn)
    podcasts.target.isBlank() ->
      if (pluggedIn) HeadphoneSyncStep.ChooseStorage else HeadphoneSyncStep.PlugInToSetUp
    // An empty source with nothing copied yet has nothing to prepare, copy or delete.
    podcasts.library.playlist?.episodes.orEmpty().isEmpty() && podcasts.lastCopied == null ->
      HeadphoneSyncStep.UpToDate(pluggedIn)
    !podcasts.readyForDevice() -> HeadphoneSyncStep.Prepare(pluggedIn)
    podcasts.upToDateOnDevice() -> HeadphoneSyncStep.UpToDate(pluggedIn)
    else -> HeadphoneSyncStep.ReadyToCopy(pluggedIn)
  }
}

/** Plugging in should copy on its own only when that is clearly the next step. */
fun shouldCopyOnConnect(step: HeadphoneSyncStep): Boolean =
  step is HeadphoneSyncStep.ReadyToCopy && step.pluggedIn

/**
 * Whether a failed copy came from the saved headphone storage itself (unplugged, remounted under a
 * new id, or access revoked), so choosing the storage again is the way forward.
 */
fun needsStorageChoice(message: String): Boolean =
  message.contains("headphone storage", ignoreCase = true)

/**
 * The step as one short headline, for the top of the status card and the launcher widget, with the
 * counts that say how much is left to do.
 */
fun syncHeadline(step: HeadphoneSyncStep, cloud: CloudSyncStatus): String =
  when (step) {
    HeadphoneSyncStep.NoSource -> "Add podcasts or a folder"
    HeadphoneSyncStep.PlugInToSetUp -> "Plug in your headphones to finish setup"
    HeadphoneSyncStep.ChooseStorage -> "Choose the headphone drive to finish setup"
    is HeadphoneSyncStep.Prepare ->
      cloud.downloadNeeded?.takeIf { it > 0 && cloud.canDownload }?.let { "$it to download" }
        ?: cloud.processingNeeded?.takeIf { it > 0 }?.let { "$it to process" }
        ?: "To process"
    is HeadphoneSyncStep.ReadyToCopy ->
      if (step.pluggedIn) "Ready to sync" else "Not in sync · plug in to copy"
    is HeadphoneSyncStep.UpToDate -> "In sync"
    is HeadphoneSyncStep.Folders ->
      if (step.pluggedIn) "Headphones connected" else "Plug in to sync your folders"
  }

/** Whether [step] asks for something now, so the headline stands out. */
val HeadphoneSyncStep.needsAttention: Boolean
  get() =
    this == HeadphoneSyncStep.ChooseStorage ||
      this is HeadphoneSyncStep.Prepare ||
      this is HeadphoneSyncStep.ReadyToCopy

/** Where the episodes come from and when they were last checked, as one line. */
fun syncSourceLine(cloud: CloudSyncStatus): String? =
  cloud.source
    .takeIf { cloud.hasSource }
    ?.let { source ->
      val checked = cloud.checked.removePrefix("Last checked: ")
      if (checked.isBlank()) source else "$source · checked $checked"
    }

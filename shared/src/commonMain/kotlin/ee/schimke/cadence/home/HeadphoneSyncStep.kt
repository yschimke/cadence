package ee.schimke.cadence.home

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
    override val message = "Add podcasts or a folder to get started."
  }

  /** First time: the headphones' storage has never been chosen. */
  data object PlugInToSetUp : HeadphoneSyncStep {
    override val message = "Plug in your headphones once to finish setup."
  }

  /** Plugged in, but the storage hasn't been chosen yet. */
  data object ChooseStorage : HeadphoneSyncStep {
    override val message = "Headphones connected. Choose their storage to finish setup."
  }

  /** Episodes still need downloading or processing before a copy. */
  data class Prepare(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Prepare episodes, then copy them to the headphones."
      else "Episodes are prepared overnight while charging, or prepare them now."
  }

  /** Prepared and not on the headphones yet. */
  data class ReadyToCopy(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Ready to copy to your headphones."
      else "Ready. Plug in your headphones to copy."
  }

  /** The headphones already hold everything prepared. */
  data class UpToDate(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Headphones are up to date. Eject the volume before unplugging."
      else "Headphones are up to date."
  }

  /** Only folders: their copy runs from the folder sync screen. */
  data class Folders(val pluggedIn: Boolean) : HeadphoneSyncStep {
    override val message =
      if (pluggedIn) "Headphones connected." else "Plug in your headphones to copy your folders."
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
    !podcasts.readyForDevice() -> HeadphoneSyncStep.Prepare(pluggedIn)
    podcasts.upToDateOnDevice() -> HeadphoneSyncStep.UpToDate(pluggedIn)
    else -> HeadphoneSyncStep.ReadyToCopy(pluggedIn)
  }
}

/** Plugging in should copy on its own only when that is clearly the next step. */
fun shouldCopyOnConnect(step: HeadphoneSyncStep): Boolean =
  step is HeadphoneSyncStep.ReadyToCopy && step.pluggedIn

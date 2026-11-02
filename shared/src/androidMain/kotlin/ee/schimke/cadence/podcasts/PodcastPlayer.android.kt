package ee.schimke.cadence.podcasts

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

private const val POCKET_CASTS = "au.com.shiftyjelly.pocketcasts"

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
actual fun rememberPodcastPlayer(controller: PodcastController, mediaAccess: Boolean): PodcastPlayerUi {
  val context = LocalContext.current.applicationContext
  val state by PodcastPlaybackService.playerState.collectAsState()
  val mediaAppsInstalled = remember(context) {
    listOf(POCKET_CASTS, "com.spotify.music", "de.danoeh.antennapod").any { packageName ->
      runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }
  }
  val pocketCastsInstalled = remember(context) {
    runCatching { context.packageManager.getPackageInfo(POCKET_CASTS, 0) }.isSuccess
  }
  var localPlayer by remember { mutableStateOf<MediaController?>(null) }
  // Robolectric's service binding returns a null ComponentName to Media3. App-tour previews
  // render without a real playback service; devices use the normal MediaController connection.
  if (android.os.Build.FINGERPRINT != "robolectric") DisposableEffect(context) {
    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))).buildAsync()
    var disposed = false
    Futures.addCallback(future, object : FutureCallback<MediaController> {
      override fun onFailure(t: Throwable) = Unit
      override fun onSuccess(player: MediaController?) { if (!disposed) localPlayer = player }
    }, ContextCompat.getMainExecutor(context))
    onDispose { disposed = true; localPlayer = null; MediaController.releaseFuture(future) }
  }
  return state.copy(
    supported = localPlayer != null || pocketCastsInstalled,
    pocketCastsAvailable = pocketCastsInstalled,
    suggestMediaAccess = mediaAppsInstalled && !mediaAccess,
    requestMediaAccess = {
      context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
    playEpisode = { episode ->
      if (episode.backend == "pocket-casts" && pocketCastsInstalled) {
        // Pocket Casts can open and play a specific episode even when its media session is absent.
        val uri = Uri.Builder().scheme("https").authority("pca.st")
          .appendPath("episode").appendPath(episode.id)
          .appendQueryParameter("auto_play", "true").build()
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(POCKET_CASTS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
          if (localPlayer?.isPlaying == true) localPlayer?.pause()
          context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
          Toast.makeText(context, "Couldn't open this episode in Pocket Casts", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
          Toast.makeText(context, "Couldn't open this episode in Pocket Casts", Toast.LENGTH_LONG).show()
        }
      } else {
        val file = AndroidPodcastPlatform.instance(context).originalForPlayback(episode)
        if (file != null) localPlayer?.let { player ->
          val metadata = MediaMetadata.Builder().setTitle(episode.title).setArtist(episode.podcastTitle).build()
          val savedPosition = episodePosition(episode, controller.state.value.library)
          player.setMediaItem(MediaItem.Builder().setUri(android.net.Uri.fromFile(file)).setMediaId(episode.key).setMediaMetadata(metadata).build(), savedPosition)
          player.prepare()
          player.play()
        }
      }
    },
    playPause = { localPlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
    seek = { localPlayer?.seekTo(it) },
  )
}

class PodcastPlaybackService : MediaSessionService() {
  companion object { val playerState = MutableStateFlow(PodcastPlayerUi()) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var player: ExoPlayer
  private lateinit var session: MediaSession
  private val controller by lazy { AndroidPodcastPlatform.instance(applicationContext).controller() }
  private var episode: Episode? = null
  private var lastSavedPosition = -1L

  override fun onCreate() {
    super.onCreate()
    player = ExoPlayer.Builder(this).build().apply {
      setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
      addListener(object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          episode = controller.state.value.library.playlist?.episodes?.firstOrNull { it.key == mediaItem?.mediaId }
          lastSavedPosition = -1
          publish()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
          if (oldPosition.mediaItem?.mediaId == episode?.key && oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) savePositionAt(oldPosition.positionMs)
          else if (newPosition.mediaItem?.mediaId == episode?.key) savePositionAt(newPosition.positionMs)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { publish(); if (!isPlaying) savePosition() }
        override fun onPlaybackStateChanged(playbackState: Int) { publish(); if (playbackState == Player.STATE_ENDED) savePosition() }
      })
    }
    val launchIntent = requireNotNull(packageManager.getLaunchIntentForPackage(packageName))
    val sessionActivity = PendingIntent.getActivity(
      this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    session = MediaSession.Builder(this, player).setSessionActivity(sessionActivity).build()
    scope.launch {
      var seconds = 0
      while (isActive) {
        delay(1000)
        publish()
        if (player.isPlaying && ++seconds >= 15) { seconds = 0; savePosition() }
      }
    }
  }
  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

  private fun savePosition() {
    savePositionAt(player.currentPosition)
  }
  private fun savePositionAt(currentPosition: Long) {
    val item = episode ?: return
    val position = currentPosition.coerceIn(0, item.durationMs)
    if (position == lastSavedPosition) return
    lastSavedPosition = position
    CoroutineScope(Dispatchers.Main.immediate).launch {
      controller.run { if (state.value.library.playlist?.episodes?.any { it.key == item.key } == true) setEpisodePosition(item.key, position) }
    }
  }
  private fun publish() {
    val item = episode ?: return
    val duration = player.duration.takeIf { it > 0 } ?: item.durationMs
    playerState.value = PodcastPlayerUi(supported = true, title = item.title, playing = player.isPlaying, positionMs = player.currentPosition.coerceAtLeast(0), durationMs = duration)
  }
  override fun onDestroy() {
    savePosition()
    scope.cancel()
    session.release()
    player.release()
    playerState.value = PodcastPlayerUi()
    super.onDestroy()
  }
}

package ee.schimke.cadence.bluetooth

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Media3 transport for on-device MP3 playback. The headset does not expose a timeline or track. */
@UnstableApi
class ShokzPlayer(private val controller: BluetoothController) : SimpleBasePlayer(Looper.getMainLooper()) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var snapshot = BluetoothState()

  init {
    scope.launch { controller.state.collect { snapshot = it; invalidateState() } }
  }

  override fun getState(): State {
    val commands = Player.Commands.Builder()
      .addIf(Player.COMMAND_PLAY_PAUSE, snapshot.localAudioControls)
      .build()
    val state = State.Builder()
      .setAvailableCommands(commands)
      .setPlaybackState(if (snapshot.localAudioControls) Player.STATE_READY else Player.STATE_IDLE)
      .setPlayWhenReady(snapshot.headsetPlaying == true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    // Media3 requires a current item in STATE_READY. This placeholder deliberately carries no
    // track identity, duration or seekability: the headphones do not report those values.
    if (snapshot.localAudioControls) state.setPlaylist(listOf(MediaItemData.Builder("unknown").setIsPlaceholder(true).build()))
    return state.build()
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    if (snapshot.localAudioControls) {
      if (playWhenReady) controller.play() else controller.pause()
    }
    return Futures.immediateVoidFuture()
  }

  /** The device has next/previous commands but supplies no queue or selectable media items. */
  fun nextTrack() { if (snapshot.localAudioControls) controller.next() }
  fun previousTrack() { if (snapshot.localAudioControls) controller.previous() }

  override fun handleRelease(): ListenableFuture<*> {
    scope.cancel()
    return Futures.immediateVoidFuture()
  }
}

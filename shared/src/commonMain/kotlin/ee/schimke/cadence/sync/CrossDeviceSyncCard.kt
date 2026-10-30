package ee.schimke.cadence.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.sync.google.LocalGoogleSyncController
import kotlinx.coroutines.launch

/**
 * Opt-in cross-device sync via Google Drive AppData. Hidden entirely on platforms that have no
 * controller wired yet (Android, wasm).
 */
@Composable
fun CrossDeviceSyncCard() {
  val controller = LocalGoogleSyncController.current
  val state by controller.state.collectAsState()
  if (!state.available) return
  val scope = rememberCoroutineScope()

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "Cross-device sync",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        when {
          !state.configured ->
            "Not configured. Drop OAuth credentials in ~/.cadence/google-oauth.json " +
              "or set CADENCE_GOOGLE_CLIENT_ID / CADENCE_GOOGLE_CLIENT_SECRET."
          !state.signedIn -> "Sign in to Google to share sync history with your other hosts."
          state.enabled -> "Enabled · signed in as ${state.signedInEmail}"
          else -> "Signed in as ${state.signedInEmail}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      state.lastError?.let {
        Text(
          "Last error: $it",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
          checked = state.enabled,
          enabled = state.signedIn && !state.busy,
          onCheckedChange = { enabled -> scope.launch { controller.setEnabled(enabled) } },
        )
        Text("Enable", modifier = Modifier.padding(start = 12.dp))
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.busy) {
          CircularProgressIndicator()
        } else if (state.signedIn) {
          TextButton(onClick = { scope.launch { controller.signOut() } }) { Text("Sign out") }
        } else {
          TextButton(
            enabled = state.configured,
            onClick = { scope.launch { controller.signIn() } },
          ) {
            Text("Sign in with Google")
          }
          if (!state.configured) {
            TextButton(onClick = { scope.launch { controller.refreshConfig() } }) {
              Text("I've added the file, recheck")
            }
          }
        }
      }
    }
  }
}

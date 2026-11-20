# Accessibility Report

_Auto-generated from `compose-preview/a11y/pr`. 5 preview(s) across 2 module(s) · 3 error(s) · 0 warning(s) · 1 info._

Browse inline; image URLs are pinned to the commit SHA on the baseline branch so links keep resolving after merge.

## composeApp

### `NotificationDownloadingPreview`

<img src="https://raw.githubusercontent.com/yschimke/cadence/compose-preview/a11y/pr/renders/composeApp/NotificationDownloadingPreview_Notification_downloading-f99a71e3.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | INFO | DuplicateSpeakableTextCheck | android.view.View  desc="Cancel" | This non-clickable item's speakable text: "Cancel" is identical to that of 1 other item(s). |

### `NotificationSyncingPreview`

<img src="https://raw.githubusercontent.com/yschimke/cadence/compose-preview/a11y/pr/renders/composeApp/NotificationSyncingPreview_Notification_syncing_light-fb11050e.a11y.png" width="400" />

_No findings._

## wearApp

### `LocalAudioPlaylistWearWidgetSquirclePreview`

<img src="https://raw.githubusercontent.com/yschimke/cadence/compose-preview/a11y/pr/renders/wearApp/LocalAudioPlaylistWearWidgetSquirclePreview_Playlist_squircle-fe5fe95f.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | androidx.compose.remote.player.view.RemoteComposePlayer | This item may not have a label readable by screen readers. |

### `ModeAndControlsWearWidgetSquirclePreview`

<img src="https://raw.githubusercontent.com/yschimke/cadence/compose-preview/a11y/pr/renders/wearApp/ModeAndControlsWearWidgetSquirclePreview_Now_playing_squircle-f7452489.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | androidx.compose.remote.player.view.RemoteComposePlayer | This item may not have a label readable by screen readers. |

### `SyncStatusWearWidgetSquirclePreview`

<img src="https://raw.githubusercontent.com/yschimke/cadence/compose-preview/a11y/pr/renders/wearApp/SyncStatusWearWidgetSquirclePreview_Sync_squircle-d27e8677.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | androidx.compose.remote.player.view.RemoteComposePlayer | This item may not have a label readable by screen readers. |

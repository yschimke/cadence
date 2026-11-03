# Foreground service evidence videos for Google Play

Play Console asks for a video of each declared foreground service type
(**App content → Foreground service permissions**). `scripts/record-fgs-videos.py`
records them from the running app: it drives the real UI over adb, records with
`screenrecord`, and burns a caption for each step into a band under the phone
frame.

| Video | Permission | Play Console task | What it shows |
| --- | --- | --- | --- |
| `data-sync.mp4` | `FOREGROUND_SERVICE_DATA_SYNC` | Media transcoding; Importing, exporting | Adds a public feed, turns on 1-minute parts, then **Download** and **Process**. Each one runs in WorkManager's `SystemForegroundService` (`dataSync`) and keeps going, with its notification, after the user leaves the app. |
| `media-playback.mp4` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback | Plays a downloaded episode. After the user leaves the app, `PodcastPlaybackService` (`mediaPlayback`) keeps it playing, with the system media controls. |

The USB copy to headphones (`SyncForegroundService`, also `dataSync`) needs the
supported headphones attached, so the video only names it in its last caption.

## Record

Build the debug APK, then record on a disposable emulator:

```sh
./gradlew :composeApp:assembleDebug
python3 scripts/record-fgs-videos.py all --serial emulator-5580
```

Videos land in `build/play-fgs-videos/`, next to the raw takes and their
`.srt` captions. The script installs the debug APK with notification permission
granted, clears Cadence's data, and switches the status bar to demo mode. On an
emulator it also clears the emulator's own notifications. It needs network
access for the NPR News Now feed, and `ffmpeg` built with libass.
`--allow-physical-device` records a real phone instead, but this still clears
Cadence's data on that phone, and other apps' notifications can appear in the
shade.

Upload each video somewhere Play reviewers can open it without signing in, such
as an unlisted YouTube video, and paste the link into its Play Console field.

The current videos, recorded from 0.1.15, are attached to the
[v0.1.15 release](https://github.com/yschimke/cadence/releases/tag/v0.1.15) as
`fgs-data-sync.mp4` and `fgs-media-playback.mp4`.

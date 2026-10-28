# Podcast preparation and headphone sync

Cadence has two separate operations: importing a listening list/positions and
preparing local files; then copying those prepared files to a mounted headphone
volume. Podcasts are added from the library home screen (**Add source →
Podcasts**) and appear there alongside folders; tapping them opens the episode
view.

## Sources

Cadence imports from Pocket Casts, public RSS feeds, gpodder-compatible servers,
Overcast exports and Cadence JSON snapshots.

### Pocket Casts (private web-player API)

Pocket Casts has no supported third-party API. Cadence uses the private API its
own web player calls, observed in a signed-in account on 2026-10-28. It can
change without notice, and using it may not be permitted by Pocket Casts' terms.

| Request | Used for |
| --- | --- |
| `POST /user/login_pocket_casts` `{email, password, scope: "webplayer"}` | Password sign-in; returns `accessToken`, `refreshToken`, `expiresIn` (3600 s) |
| `POST /user/token` `{grant_type: "refresh_token", refresh_token, scope}` | Refreshing an expired access token |
| `POST /up_next/list` | Up Next order; `episodeSync` carries `duration` and `playedUpTo` for started episodes |
| `POST /user/in_progress` | In-progress episodes with `url`, `duration`, `playedUpTo`, `fileType` |
| `GET /user/playlists` | Playlists; manual ones list members in `episodeOrder` |
| `POST /user/episode` `{uuid}` | One episode, for playlist members and Up Next entries without a length |

All requests send `Authorization: Bearer <accessToken>`. The API answers
cross-origin requests only from `https://pocketcasts.com`, so the browser build
of Cadence can't call it; Android and desktop can. There is no bot challenge on
the API itself.

Signing in:

- **Android: Sign in on pocketcasts.com** opens the web player's own sign-in page
  in a WebView. After sign-in the player stores its tokens in a script-readable
  `auth` cookie (`{accessToken, refreshToken, tokenType, expiresIn}`); Cadence
  reads it through `CookieManager` and from then on calls the API itself. If no
  token can be read, Cadence falls back to recording the account responses the
  player loads while it opens **Playlists**, and imports manual playlists from
  those. Google rejects sign-in from embedded WebViews, so accounts that only
  use **Continue with Google** need a Pocket Casts password.
- **Sign in with password** (Android and desktop) calls the sign-in endpoint
  directly.

The tokens are saved with the local library (`library.json` in app storage) so
later imports don't ask again; **Sign out** deletes them. Smart playlists are
returned without members and aren't offered. Archived playlist members are left
out unless the playlist shows archived episodes, as in the player. Position
write-back to Pocket Casts isn't implemented.

The earlier [passive browser helper](pocketcasts-browser-import.md) still works
for the browser build: run it in your own Pocket Casts tab and paste its export.

| Backend | Selection/positions | Exportable audio | Implemented |
| --- | --- | --- | --- |
| Pocket Casts | Up Next, in progress and manual playlists, with positions | Episode enclosure URLs from the account | Private web-player API (Android/desktop); passive browser helper on web |
| Public RSS | Newest 1–50 feed items; no account position service | HTTPS, unencrypted enclosures with permission to download | Feed import/refresh, download and preparation |
| gpodder API v2 (gpodder.net, oPodSync) and Nextcloud GPodder Sync | Unfinished episodes and positions (no queue); used by AntennaPod, Kasts, gPodder | Enclosures resolved from each show's public feed | Import; explicit **Send position** with a re-read conflict check |
| Overcast | "All data" OPML has per-episode `progress` (seconds) and `played`; no queue or durations | Enclosures resolved from each show's public feed | Paste-import of the export file |
| Cadence JSON snapshot | Explicit selected episodes, positions and revisions | Authorized HTTPS enclosure supplied by user | Import, validation, preparation |
| Spotify | OAuth playlist metadata and some resume-point reads | API forbids facilitating content downloads; previews are not full episodes | No export adapter |
| Audible | Vendor account and compatible-device flows | Protected vendor downloads require supported devices | No export or DRM conversion adapter |

Spotify's [playlist API documentation](https://developer.spotify.com/documentation/web-api/reference/get-playlist)
explicitly prohibits content downloading. A public podcast also listed on Spotify
can be imported independently through its publisher's RSS feed; this is not an
export of Spotify audio. Audible's
[AudibleSync workflow](https://help.audible.com/s/article/listen-with-audiblesync?language=en_US)
is Windows-specific and limited to supported devices. Cadence does not decrypt
or repackage protected audio. Apple Podcasts, YouTube Music, Castro and Player FM
offer no export with positions. AntennaPod's database export (SQLite) has the
queue order and positions in milliseconds and is a candidate for a future file
import; today AntennaPod users can sync to a gpodder/Nextcloud server instead.

### Sources that only report positions

gpodder and Overcast identify an episode by its show's feed URL and the enclosure
URL that app played, without the title, duration or current enclosure that a
download needs. Cadence fetches each show's public feed once and matches the item
by GUID, then exact enclosure URL, then the enclosure file name (tracking prefixes
such as Podtrac change over time). It refuses an episode when the feed no longer
lists it, or when the length the other app measured differs from the feed's
`itunes:duration` by more than 2% (minimum 30 s): different audio, for example
re-inserted ads, would put the position in the wrong place. Skipped episodes are
counted in the import message.

**gpodder.** Cadence reads `GET /api/2/episodes/{user}.json?since=0&aggregated=true`
(Nextcloud: `GET /index.php/apps/gpoddersync/episode_action?since=0`) with HTTP
Basic auth, keeps the latest `play` action per episode (action names are
case-insensitive; Nextcloud uses `-1` for unset) and lists episodes with a
position more than 30 s before the end, most recently played first. gpodder has
no queue. **Send position** uploads one `play` action (`started`, `position`,
`total` in seconds) to `POST /api/2/episodes/{user}.json` or
`/index.php/apps/gpoddersync/episode_action/create`, using the feed and enclosure
URLs the other app reported so it recognises the episode. gpodder has no
conditional write, so Cadence re-reads first and refuses if the episode moved
since import (or since its own last send); a write racing between that read and
the upload can still be lost. The password (use a Nextcloud app password) is held
in memory for the app session and never saved or uploaded. The web app can only
reach servers that allow cross-origin requests with credentials, which gpodder.net
and default Nextcloud installs do not; use desktop or Android.

**Overcast.** Overcast has no API. Its **Account → Export OPML (All data)** file
lists episodes under each feed with `progress` (seconds), `played` and
`userUpdatedDate`. Cadence imports unplayed episodes with progress, most recently
updated first. Paste the file contents into **Overcast or a saved export**. A subscriptions-only
OPML is rejected with a pointer to the All data export.

## Use the workflow

1. On the library screen choose **Add source → Podcasts**, then one service and
   how many episodes (1–50, default 3):
   - **Pocket Casts**: sign in, then choose Up Next, In progress or a manual
     playlist. Episodes without HTTPS audio or a known length are skipped and
     counted.
   - **Podcast feed**: the newest episodes of a public HTTPS feed. Items without
     an HTTPS enclosure or `itunes:duration` are skipped and counted. RSS has no
     account position, so first import starts at zero; refresh retains
     Cadence's contiguous position for the same episode/audio version. Feeds may
     redirect and be up to 40 MB.
   - **gpodder or Nextcloud**: unfinished episodes from a gpodder-compatible
     account.
   - **Overcast or a saved export**: Overcast's All data OPML, a Pocket Casts
     helper export or a Cadence snapshot.

   Adding returns to the library. The episodes' overflow menu offers
   **Refresh** (Pocket Casts, feeds, and gpodder while its password is held)
   and **Remove**.
2. Tap the episodes to open them and choose **Download**. By default Cadence
   skips what you've already heard; **Audio options** controls that, splitting
   at your position, part length (1–120 minutes; 0 keeps each episode whole)
   and the part format.
3. **Mark played** records a durable local acknowledgement. For gpodder imports,
   **Send … to your other apps** uploads the listened-to position; other
   sources keep it in Cadence.
4. Connect the headphones, let the OS mount the storage volume, and choose
   **Copy to headphones**. The first time, pick the volume root, not an
   existing music folder.

A 60-minute episode at 30 minutes produces `[0,30)` and `[30,60)` sections. With
five-minute chunks, each side is subdivided. Irregular boundaries and short tails
are retained. Every section records original episode identity, immutable audio
version and exact start/end milliseconds. IDs are SHA-256 over that identity and
range; filenames include a safe title and a padded starting offset so sections
of an episode sort in timeline order. Playlist ordering between different episodes
is not enforced by headphone firmware. Encoded audio has codec frame/encoder-delay
rounding; offsets remain the intended source timeline rather than an assertion
of sample-perfect MP3 boundaries.

### Platform support

| Platform | Local preparation | USB copy |
| --- | --- | --- |
| Android (current app minimum API 34) | Original unsplit audio; Media3 Transformer clips to AAC/M4A | System document picker/SAF, if Android exposes the mounted volume; OTG/host hardware required |
| Desktop (Linux/macOS/Windows) | Original unsplit audio; FFmpeg clips to MP3 or AAC/M4A | OS-mounted writable directory; no raw device writes |
| Web | Original unsplit audio to OPFS; RSS/media servers must allow CORS | HTTPS/localhost and desktop Chrome/Edge File System Access picker |
| iOS | No app target in this repository | Unsupported |
| Firefox/Safari/mobile browsers | Download/import depends on browser storage and CORS | Writable mounted-directory workflow unsupported |

Install FFmpeg separately on desktop and make `ffmpeg` available on `PATH`; it is
not bundled into installers. Android uses `androidx.media3:media3-transformer`
1.11.1, clipping configuration, video removal and AAC encoding, on the application
looper. Its default MP4 muxer produces real M4A files. Export cancellation cancels
the transformer, and partial output is never marked ready. See Android's
[clipping/transcoding guide](https://developer.android.com/media/media3/transformer/transformations)
and [supported formats](https://developer.android.com/media/media3/transformer/supported-formats).
Android does not promise MP3 encoding. **Check AAC/M4A support on the actual
headphones** before choosing it; use desktop MP3 preparation for MP3-only devices.
Cloud currently syncs manifests, not generated audio, so a phone cannot retrieve
desktop MP3 chunks through cloud sync.

All operations are foreground/manual. Keep the app/tab open; no background
podcast worker is advertised. Browser requests omit ambient cookies and reject
redirects; feeds or enclosures that depend on redirects need a direct HTTPS URL
or preparation on desktop/Android. Native media downloads follow at most five
HTTPS redirects without account headers. Downloads reject empty/non-audio content
and have a 2 GB per-file limit. There is no protected streaming/HLS/DRM export.

### Snapshot format

This is a **Cadence interchange format**, not a Pocket Casts export format. A
user-supplied/authorized integration would have to produce it. The example URL
is illustrative and is not a downloadable fixture:

```json
{
  "schema": 1,
  "name": "Wanted on headphones",
  "episodes": [{
    "backend": "pocket-casts",
    "id": "original-episode-id",
    "title": "An hour-long episode",
    "audioUrl": "https://publisher.example/episode.mp3",
    "durationMs": 3600000,
    "positionMs": 1800000,
    "revision": "observed-position-revision",
    "audioVersion": "immutable-media-version",
    "extension": "mp3"
  }]
}
```

`revision` identifies observed backend state; `audioVersion` identifies the media
whose offsets were measured. Never reuse an audio version after media changes.
RSS uses enclosure URL and declared length as a best-effort version fingerprint;
this cannot detect all dynamic advertising changes. Use versioned snapshots for
feeds with changing ads. Credentials must not be embedded in URLs. Cloud metadata
contains the playlist URLs/history; use public media URLs unless you deliberately
accept storing private URLs in your own account. Files and metadata accumulate
locally; automatic cache eviction is not implemented.

## Playback acknowledgements and conflicts

Completions form an idempotent, grow-only set of section IDs. Marking the same
section twice does not duplicate progress. Starting from the imported backend
position, only contiguous completed intervals advance the local position. A later
chunk can be acknowledged first without skipping an unplayed gap. A different
audio version cannot advance the old media's position. Acknowledgements also record
the observed backend revision. A fresh snapshot with a new revision respects an
explicit backend rewind; old acknowledgements are retained as history and cannot
automatically advance the new revision.

`PodcastBackend` separates playlist enumeration, snapshot reads, capability flags
and conditional/idempotent position writes. A future write-capable adapter must
use `expectedRevision` and a stable `operationId`, re-read on conflict, and expose
rewinds/reset decisions rather than overwrite them. gpodder is the only source
that accepts positions, and only by explicit **Send position**: it has no
conditional write, so Cadence re-reads and compares before uploading (see above);
it is not a compare-and-set. It was tested against recorded protocol responses,
not a live server. Other sources keep acknowledgements as local/cloud metadata,
and the UI says the source does not accept updates.

## Safe device folder ownership

Only `Cadence/podcasts` beneath the selected volume is used by this workflow.
An empty folder receives `.cadence-owner` containing `cadence-podcasts-v1`. An
existing nonempty folder without that marker is refused. Nothing outside that
folder is deleted or replaced. Existing Cadence sections are retained, including
obsolete sections, so an incremental refresh cannot delete personal files.
Desktop also rejects symlinks in the owned path. Directory selection grants
filesystem access; USB discovery does not grant a mount path or access to the
protected mass-storage interface.

The device receives audio and `cadence-manifest.json` with opaque episode keys and
timeline ranges. The app/cloud library maps those keys back to original backend
episode IDs; raw provider IDs are omitted from USB because RSS GUID fallbacks can
contain private URLs. Account credentials, cloud settings and enclosure download URLs
are omitted. The manifest is written last. Desktop uses temporary files before
replace; browser writes use File System Access commit semantics. SAF cannot
promise atomic replacement: unplugging may leave a partial file, and the app
reports failure without marking the transfer complete. Retry before listening.
Firmware does not report played chunks back; marking played is an explicit app
action. Safe eject remains the OS's responsibility.

## Optional cross-device persistence

**Chosen approach:** Supabase Postgres + Auth for small metadata documents, with
operator-applied [SQL setup](../cloud/supabase.sql). This uses the same HTTPS REST
RPC and password auth calls on all three platforms, avoids platform-specific cloud
SDKs, and gives explicit per-user row-level security and atomic revision checks.
Firebase would also work, but its platform SDK differences add integration work
for Kotlin/Wasm; a bespoke server would add hosting/auth maintenance. Supabase is
a recommendation and optional adapter, not a provisioned service.

The document includes the listening list, RSS feed selection, segmentation configuration, source
positions, manifests, played-section acknowledgements and last import timestamp.
Device mount paths, directory permissions, available-file IDs and copied-file IDs
stay local. Restoring a manifest on another device never claims its audio is
already downloaded; preparation must run there. No audio is uploaded, minimizing
storage, transfer costs and redistribution concerns. Adding encrypted cloud audio
storage is a separate product/licensing/cost decision.

Setup (requires the project owner's action; not performed by this change):

1. Create/select a Supabase project and review its region/privacy requirements.
   Run `cloud/supabase.sql` in its SQL editor. Do not disable RLS.
2. Configure email/password Auth and create/confirm the user's account using your
   chosen account enrollment flow. Cadence currently provides sign-in, not account
   registration/password recovery. A production public product should add a
   reviewed enrollment and PKCE/OAuth flow before wider distribution.
3. In each Cadence app, enter the HTTPS project URL and **publishable/anon** key.
   Never use a service-role or secret key. Sign in with the same account. Passwords
   and access tokens are memory-only, with no token logging or cloud serialization.
   Sign out discards the in-memory session while retaining the local library.
   Sign in again after restarting or token expiry. No refresh token is persisted.
4. **Save to cloud** creates a document at revision 1. Another device signs in and
   chooses **Restore cloud configuration & merge played sections**, then prepares
   its own audio. That action explicitly adopts remote configuration and unions
   played acknowledgements. Save again to upload the union.
5. Concurrent saves use compare-and-set on `revision`. A stale writer gets HTTP
   409; local state is retained. Restore remote configuration, reapply desired
   settings, and retry. No wall-clock last-writer-wins overwrite is used. A response
   lost after server commit may require the same restore/retry recovery.

The free plan currently includes 500 MB database storage, 50,000 monthly active
users and 5 GB egress, and pauses projects after one week of inactivity. Pro starts
at $25/month; review current quotas and pricing before provisioning. This feature
needs no paid plan for small personal metadata use, but free service availability
is not guaranteed. No project, billing plan or deployment was created. Source:
[Supabase pricing](https://supabase.com/pricing). The
[RLS guide](https://supabase.com/docs/guides/database/postgres/row-level-security)
explains why grants as well as policies are restricted in the setup SQL.

Local state is `~/.cadence/podcasts/library.json` on desktop, private app files on
Android, and `cadence-podcasts-v1` localStorage plus OPFS audio on web. Browser
storage is origin-specific and subject to quota/eviction. The Android **Podcast
sync** home-screen widget shows last import/operation status and whether actual
local sections are ready for a device copy. Tap it to open Cadence; it does not
start background network work or claim the headphones are attached.

## Verification

Automated checks cover segmentation boundaries, out-of-order and duplicate played
acknowledgements, media-version isolation, durable restoration/readiness checks,
failed preparation, cloud payload separation, revision failure preservation,
actual FFmpeg M4A/MP3 exports, timeline filename order, owned-folder copying,
symlink rejection and browser folder safety. `tests/cloud-persistence.test.cjs`
executes the setup SQL in PGlite Postgres, testing anonymous denial, two-account
RLS isolation and revision conflicts without provisioning a cloud service.

```sh
./gradlew :shared:desktopTest :composeApp:assembleDebug :composeApp:testDebugUnitTest :composeApp:lintDebug
./gradlew :shared:wasmJsBrowserDistribution ktfmtCheck
node --test tests/browser-platform.test.cjs
npm install --prefix /tmp/cadence-cloud-tests @electric-sql/pglite@0.5.11
NODE_PATH=/tmp/cadence-cloud-tests/node_modules node tests/cloud-persistence.test.cjs
```

`Media3ClippingTest` is a real-device Android instrumentation check: it generates
PCM input and checks AAC output format/duration and original section mapping.
Run on a connected API-34+ Android device/emulator using `./gradlew :composeApp:connectedDebugAndroidTest`. Compilation alone does not verify codecs,
SAF device providers, widget rendering, USB permission behavior or headphone
firmware. These need a hardware check. Live Supabase Auth/REST and Pocket Casts
account integration need credentials/provider support; local SQL validation is
not a claim that those live flows were exercised.


The optional production browser smoke uses a generated two-second tone (no external
media) and intercepts only its enclosure URL. Serve the production distribution as
in the main README, install Playwright outside the repository, and run:

```sh
NODE_PATH=/tmp/cadence-tools/node_modules node tests/podcast-ui-smoke.cjs
```

The checked-in tone was generated with FFmpeg's sine source at 440 Hz, two seconds,
44.1 kHz, MP3 64 kbps. No podcast audio is checked into this repository.


A separate pre-existing shared-module device-test resource task fails before test
compilation (`CopyResourcesToAndroidAssetsTask.outputDirectory` is unset), also
reproduced with the original shared build configuration. It is tracked as
[SCH-57](https://linear.app/yschimke/issue/SCH-57/cadence-shared-android-device-tests-fail-before-compilation-compose).
The new Media3 instrumentation test lives in the app's existing Android test
suite; `:composeApp:assembleDebugAndroidTest` compiles it without that broken task.

### Session verification record (2026-10-26)

- Passed: 16 shared desktop tests, including actual FFmpeg MP3/M4A clipping and
  cloud restore/revision behavior and URL-free USB manifests; no skipped tests.
- Passed: Android debug APK and app instrumentation APK assembly, Android lint,
  desktop JAR, production Wasm distribution, and `ktfmtCheck`.
- Passed: nine browser adapter tests; local PGlite/Postgres auth isolation/CAS
  checks; the new podcast browser smoke and existing USB/unsupported-browser smoke.
- Android unit-test task reported `NO-SOURCE`; it is not counted as executed tests.
- Not run locally: connected Android codec/widget/SAF tests, physical headphone
  playback/copying, live cloud Auth/REST.
- Passed in the first PR CI run: `Media3ClippingTest` on an API-34 emulator, with
  one codec test and one app-launch test, zero failures and zero skipped tests.
  [Instrumentation run/report](https://github.com/yschimke/cadence/actions/runs/40303267743)
  at commit `43eca08`; this does not replace a physical-headphone check.

### Session verification record (2026-10-27): sources and real feeds

- Live desktop run (not checked in) against public feeds: NPR Planet Money, The
  Daily (301 from art19, 20 MB Simplecast feed), Ask a Spaceman (302 from
  libsyn), Lex Fridman. All imported the newest episodes. Previously the 8 MB
  limit and the refusal of feed redirects failed every one of these. One
  33 MB NPR episode downloaded through four tracking redirects and copied to a
  temporary volume's `Cadence/podcasts`, leaving an existing `Music/` file intact.
- Production web build in a browser: imported The Daily and Tech Won't Save Us,
  and downloaded the latter's 44,626,678-byte episode (its declared length)
  through tracking redirects to browser storage. Web can read only servers that
  allow CORS at every hop: of seven sampled hosts, Megaphone, Blubrry,
  Buzzsprout, Transistor and Omny worked; a libsyn and an Anchor enclosure
  chain, NPR's feed and Acast's feed refuse cross-origin reads, and The Daily's
  audio passes a tracker without CORS. These show a CORS-specific message; use
  desktop or Android for them. USB copy on web needs the OS folder picker and
  was not exercised.
- gpodder, Nextcloud GPodder and Overcast parsing, feed matching, length
  mismatch refusal and the send-position conflict check are covered by
  `PodcastSourcesTest` with recorded response shapes. No live gpodder server or
  Overcast export was available, so those are protocol tests, not live checks.
- Rendered `PodcastImportedPreview` and `PodcastPreparedPreview`.
- FFmpeg was not installed on this machine, so `realAudioClippingAndOwnedFolderExport`
  failed to start locally; CI installs FFmpeg.

Before is the published main app; after images are from the locally built
production Wasm app using generated test audio. The mobile image is a responsive
browser render, not a claim of an Android device screenshot.

| Before | After home |
| --- | --- |
| ![Published home](podcast-sync/before.png) | ![Podcast entry](podcast-sync/after-home.png) |

[Prepared sections and acknowledgement](podcast-sync/after-sections.png) ·
[Imported snapshot](podcast-sync/after-podcast.png) ·
[Mobile-width browser](podcast-sync/after-mobile.png) ·
[Dark theme](podcast-sync/after-dark.png)

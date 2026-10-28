# Experimental one-way Pocket Casts browser import

This is a user-authorized browser-session investigation tool, not a supported
Pocket Casts public API integration. Its JSON/protobuf parsers are tested against
synthetic responses using the published Android protocol, plus the web-player
flow observed in a signed-in account on 2026-10-27 (below). Smart playlists and
write-back remain unverified.

Pocket Casts' current [playlist documentation](https://support.pocketcasts.com/knowledge-base/playlists/)
says there is no playlist sharing/export link. No playlist RSS route was found.
That is separate from reading the account APIs used by its own apps.

The public Android
[service contract](https://github.com/Automattic/pocket-casts-android/blob/main/modules/services/servers/src/main/java/au/com/shiftyjelly/pocketcasts/servers/sync/SyncService.kt)
and [protobuf definitions](https://github.com/Automattic/pocket-casts-android/blob/main/modules/services/protobuf/src/main/proto/sync_api.proto)
provide a concrete lead:

| Read endpoint | Useful data |
| --- | --- |
| `/user/playlist/list` | Manual/smart flag; manual membership and episode order; smart filter rules |
| `/user/episodes` | Episode IDs, enclosure URLs, titles, duration and `played_up_to` |
| `/user/podcast/episodes` | Per-podcast episode positions/state in the JSON response |
| Public podcast cache responses | Titles, duration and enclosure metadata; no account position |

### Observed web-player flow (2026-10-27)

The web player at `pocketcasts.com` does not use the Android routes above. A
passive capture in a signed-in account (endpoint paths and JSON key shapes only)
showed:

| Request | Observed response |
| --- | --- |
| `GET /user/playlists` (on first opening **Playlists**) | `{playlists: [...]}` with `manual`, `showArchived`, `episodeOrder` (UUID strings) and `episodes` (`episode`, `podcast`, `title`, `url`, …) — the same JSON shape as the Android list. Smart playlists have empty `episodeOrder`/`episodes`. |
| `POST /user/episode` with `{uuid}`, once per manual-playlist member | One episode object: `url`, `duration` and `playedUpTo` (seconds), `fileType`, `title`, `playingStatus`, `isDeleted` |
| `GET podcast-api.pocketcasts.com/podcast/full/{uuid}` | Public podcast metadata; not needed for export |

Opening a playlist or episode detail afterwards issues no further account
requests; the app reuses what it already loaded. Install the helper on the
Podcasts page **before** first opening Playlists, or reload and do so.

The web player hides members whose `/user/episode` `isDeleted` is `true` as
"archived" unless the playlist's `showArchived` is set, and the helper does the
same. For a 4-episode manual playlist with one archived member, the helper's
order and positions matched the player: duration − `playedUpTo` gave the
remaining minutes it displays for each visible episode (50, 39 and 48).
The player's header total ("2 hours 49 minutes") did not match either sum and
is unexplained.

The helper observes these responses when the web player itself receives them.
It does **not** issue or replay API requests, access auth/session storage, inspect
request headers, log raw responses or change playback/playlist state. It does not
call sync/update endpoints. It restores its browser hooks when stopped or when
the page is reloaded.

## Try it in your own signed-in browser

1. Sign into Pocket Casts normally. Keep credentials in that browser; do not send
   passwords, tokens, cookies, authorization headers or a HAR recording.
2. Review [the helper source](../tools/pocketcasts-browser-export.js). In that
   Pocket Casts tab, open DevTools **Sources → Snippets**, create a snippet,
   paste the source, and run it. It refuses to install outside Pocket Casts.
3. Run it from the **Podcasts** page of a freshly loaded tab, then open
   **Playlists**. That first visit loads membership and each manual member's
   current position. If Playlists was already opened in this tab, reload and
   repeat. Do not press Play or change account state for this investigation.
4. In the console, run:

   ```js
   cadencePocket.status()
   cadencePocket.list()
   ```

   These report counts, read endpoint paths, and playlist names/IDs. They omit
   credentials and enclosure URLs. If nothing was captured, the app may already
   have the data cached, may use another endpoint, or may use a response format
   the helper does not support. The helper cannot recover earlier responses or
   inspect another browser's existing session. A real browser inspection is still
   needed in that case; do not treat an empty capture as an empty playlist.
5. Once the intended playlist appears, use its ID:

   ```js
   await cadencePocket.download('playlist-id-from-list')
   ```

   This downloads `cadence-pocketcasts.json` locally. Paste its JSON into
   Cadence's **Import episodes → Paste export**, then prepare and copy audio normally. Before
   relying on it, compare at least one episode's duration and imported position
   with the web player. The published protocol uses seconds; the snapshot uses
   milliseconds. Its revision is a browser capture revision, not a server CAS
   token and cannot authorize backend writes.
6. Finish with:

   ```js
   cadencePocket.stop()
   ```

   Reloading the tab also removes the recorder.

The exported file contains listening history and media URLs, which may be private
or time-limited. Keep it local and review the cloud metadata warning before
uploading it to your own account. Do not commit account exports to the repository.
The helper never downloads audio; media access, actual format support and rights
to copy still need checking by the app and the user. HLS/DRM content is not made
exportable by finding an enclosure URL.

## Deliberate limits

- **Manual playlists first.** A smart playlist response usually describes filter
  rules rather than a resolved set of episodes. Download-state predicates can
  also be device-specific. The helper refuses smart lists instead of guessing
  membership. A separately verified resolved-episode endpoint would be needed.
- **Incomplete captures fail.** Missing current position, metadata, duration or
  enclosure causes export to stop with a numbered-episode message. The helper
  does not quietly replace an unknown position with zero. Zero omitted from the
  published protobuf position field is decoded as its defined default of zero.
- **Foreground only.** The recorder sees subsequent responses in this one tab,
  until stopped/reloaded. It is neither a persistent account connection nor a
  cross-device background sync service.
- **Best-effort media identity.** The media version hashes the observed enclosure
  and duration. This cannot detect every dynamic-ad replacement; use stable media
  before relying on section offsets.
- **No write-back.** This investigation does not verify backend position writes,
  conditional writes, conflict handling at Pocket Casts or permission for a
  production third-party API client.

Verification:

```sh
node --test tests/pocketcasts-browser-export.test.cjs
```

Tests cover passive JSON capture/order/positions, the observed web-player
`/user/playlists` + `/user/episode` flow with archived members, protobuf membership and zero
position, no credential access/extra requests, rejection of unresolved smart rules
and incomplete metadata, malformed payload isolation, and restoring the original
fetch implementation. They are parser/recorder tests; the live-account evidence is
the observation above.

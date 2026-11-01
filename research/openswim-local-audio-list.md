# OpenSwim Pro local-audio listing

On 2026-11-01, the Shokz app's **Sync Local Audio Information** screen said it
synchronizes audio filenames and folder structure. With the headphones connected
over Bluetooth, its local-audio view showed the `Cadence/podcasts` folder and 32
audio files. The same folder had 32 nonempty files in the earlier USB listing.
All 29 filenames in Cadence's most recent USB copy manifest matched the prefixes
shown in the Bluetooth-synchronized view; the view truncated long filenames.
The other three files were from the earlier copy. A trace of the same live sync
contained 36 names across 11 `0x16` response batches (the additional four were
in a separate folder). Intermediate batches carried a two-byte trailer matching
the next batch's first file index; the final batch had no trailer. All 29
manifest filenames matched after allowing for truncation: long Bluetooth names
were limited to 128 bytes, retaining the extension while shortening the base
name. This is strong evidence of a
match in this session, not a unique device identifier: two devices can contain
identical files.

The current capture's `0x16` response batches do not validate with the
existing codec's fixed checksum seeds. Reversing the Modbus CRC gives different
apparent seeds for the 352-, 406-, and 404-byte bodies; do not add those
observations as universal constants. A payload decoder can be tested offline,
but transport/checksum handling and the end-of-list sequence still need to be
understood before Cadence can issue a supported listing request.

An earlier HCI capture of the Shokz app's sync contained a response carrying
folder names followed by a response carrying MP3 filenames. The filename
response followed an observed operation `0x49`, after other local-audio sync
operations. The precise request arguments, response correlation, pagination,
and side effects of the entire exchange have not been independently validated.
Do not infer a safe standalone command from the name
response or treat cached phone-app data as a fresh device listing. Capture and
verify the exchange on current firmware before exposing it in Cadence.

Cadence currently lets the user link a selected USB folder to a paired
Bluetooth headset. That correctable link is a user-confirmed suggestion based
on the paired device, not yet an automated content comparison.

The existing Cadence audio filenames contain a long content-derived section ID,
so multiple distinct matching filenames can support a tentative association
without renaming or re-encoding existing files. A future copy could place a
random per-association tag near the start of each filename, where truncation is
less likely to hide it. Neither scheme proves physical identity: files can be
copied between devices, and a file listing can be incomplete or stale. A
suggestion must retain an explicit change/remove option and avoid silently
overwriting an existing confirmed association.

## Current-track evidence

A previous controlled capture on the same firmware provides a useful lead. In
`with-local-audio-progress.pcapng`, selecting a track produced a `0x11`
notification with the five-word body `[1, 3, 1, 4, 1]`; the subsequent
`0x8040` reply to the app's `0x40` query contained the same five words. In
`with-mp3-next-track.pcapng`, a Next action produced `[1, 4, 1, 4, 1]`;
Previous in `with-mp3-previous-track.pcapng` produced `[1, 3, 1, 4, 1]`.
This is evidence that the headset reports a changing track-related value, and
that it can be recovered after reconnecting by the existing query. The capture
alone does not prove the meaning or indexing of the individual words.

The preceding sync contained file records with IDs 1 through 4, but it also
sent an `0x47` mapping that reordered values. The changing word might be a file
ID, a playlist ordinal, or another index requiring that mapping. The earlier
list was from one folder; it does not establish how multiple folders are
addressed or whether IDs survive a USB library change. We must correlate
several named selections across folders with both the live notification and a
fresh query before using this as a current filename. The app's visible title
can also be cached, so its response to physical headset buttons and a fresh
reconnection needs checking.

The repeated `0x8040` replies in `with-mp3-progress-refresh.pcapng` remained
`[1, 3, 1, 4, 1]` over roughly 25 seconds. This particular response is not
itself evidence of elapsed playback time. Other packets or a different query
might provide position; absence from this sample does not prove the headset
cannot report it. Until independently established, neither that value nor an
app-side timer should be displayed as the headset's exact playback position.

A further capture on 2026-11-01 after selecting podcast chunks was not suitable
for a clean one-selection/one-index correlation: the `0x11` notifications
rapidly traversed many values (including 9 through 19 in about two seconds)
without corresponding Next commands, while the Shokz player continued showing
the old title. This suggests the title can be stale and the headset was not
behaving like normal five-minute playback during this test. The cause has not
been established; possible playback, file, or device-state problems require a
separate controlled test. No claim that the newly selected chunks played
successfully should be made from this capture.

## Controlled folder and track test (2026-11-01)

Four MP3 tones were copied into a new `cadence-track-tests` folder, leaving the
existing podcast and music folders untouched. Their filenames and lengths were
`01-2s`, `02-16s`, `03-8s`, and `04-4s`. After synchronizing the Shokz app,
the folder listed all four. Selecting `02-16s` played it, and the listener
confirmed the sequence 02, 03, 04, 01, 02. The 04-to-01 transition stayed in
the selected folder even though other folders existed. Other playback orders,
firmware versions, and folder navigation after reconnecting remain untested.

The captured `0x16` records mapped these filenames to IDs 37, 38, 39, and 40.
A `0x43` selection request for 02 carried `[34, 38, 33, 36, 5]`; the ensuing
`0x11` notification and `0x8040` query response carried `[1, 38, 33, 36, 5]`.
As playback advanced, the `0x11` second word changed to 39, 40, 37, and 38;
observed transition gaps were approximately 16, 8, 4, and 2 seconds. The
`0x47` sync mapping associated displayed positions 33–36 with file IDs 37–40,
and marked position 33 as a folder boundary. The final four words therefore
strongly support `[file ID, first playlist position, last playlist position,
folder index]` for this case; `[1]` may be play state, but has not been
independently verified. The folder record itself reported index 37 and count 4,
so its first-file index is not the same as the tentative folder index 5.

This is evidence that current file and folder can be resolved given a freshly
synchronized listing and ordering table, and that the app can select an item in
a folder using `0x43`. It is not yet sufficient to emit arbitrary `0x43`
requests: verify more folders, empty/changed libraries, repeat/shuffle, stale
indices, errors, and reconnect behavior. In particular, a selection request
contains both a playlist position and file ID; treating its first integer as a
stable file ordinal would be incorrect. No elapsed-time field was identified.

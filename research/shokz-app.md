# Shokz app — proprietary features observed

Notes from three screenshots of the official **Shokz** Android app paired
with an **OPENSWIM PRO**. The goal of this document is to enumerate the
proprietary control surfaces the official app exposes that our app does
not yet reach, and to map each one to a probable RCSP opcode (per
[`rcsp.md`](rcsp.md)) so the work can be planned.

The screenshots captured:

1. **Device home** — battery, hero render, mode-switch tab, EQ presets,
   Local Audio entry, transport controls.
2. **Playback Order** — Normal / Shuffle / Repeat selector.
3. **Local Audio** — flat list of files stored on internal flash,
   "Play all", refresh, persistent mini-player.

---

## 1. Working-mode switch (Bluetooth Mode ↔ MP3 Mode)

**What it does.** Tab-style toggle on the device home screen. In Bluetooth
mode the headphones render audio streamed from the phone; in MP3 mode
they play files from on-device flash without a phone connection. The same
hardware buttons map to AVRCP transport in BT mode and to local-file
transport in MP3 mode.

**Why it matters for us.** This is the gateway feature for the swim use
case — files synced over USB are only useful if the user can switch the
headphones to MP3 mode without the Shokz app. Today our advanced-commands
list has a `ToggleSwimmingMode` placeholder; that is actually the EQ
preset, not the working mode. We need a separate primary control.

**RCSP mapping.** Almost certainly `JL_OPCODE_FUNCTION_CMD` (op `0x0E`).
The sub-opcode catalogue for `FUNCTION_CMD` is not yet in our research;
this is the highest-priority gap.

**Status.** Not implemented; not even a placeholder.

---

## 2. Playback order (Normal / Shuffle / Repeat)

**What it does.** Dedicated screen behind the device home. Sets how the
headphones traverse the on-flash library while in MP3 mode.

**Why it matters for us.** Direct sibling of MP3-mode playback; without
it the user has to plug back into the phone to change shuffle/repeat
state on a swim.

**RCSP mapping.** Most plausibly `JL_OPCODE_SYS_INFO_SET` (op `0x08`)
with a `PLAY_MODE` sub-attribute. Not currently in our sub-attribute
table — needs to be confirmed against the Jieli iOS SDK or the AC63
firmware source.

**Status.** Not implemented. Worth adding to the advanced-commands menu
(Audio category) once the sub-attribute id is confirmed.

---

## 3. Local audio browser

**What it does.** Lists every audio file on the device's internal flash,
shows track count, "Play all", per-row tap to play, refresh to rescan,
and a help affordance. The currently-playing track is highlighted.

**Why it matters for us.** Maps directly to the secondary use case —
"see what's on the headphones while connected." Today our `DeviceFiles`
screen reads files via the **USB** mass-storage volume (FAT32 SAF tree).
The Shokz app does the same query **over Bluetooth**, so it works while
the user is wearing the headphones with no cable. That is a meaningfully
different capability and should be a separate code path.

**RCSP mapping.** Lives in the file-transfer family (`0x1B`–`0x28` per
`rcsp.md`). Specifically:

- `JL_OPCODE_FILE_LIST` / `JL_OPCODE_FILE_LIST_GET` — enumerate.
- `JL_OPCODE_FILE_PLAY` / `JL_OPCODE_FILE_STOP` — pick a file by id.
- `JL_OPCODE_FILE_INFO` — name, size, duration.
- `JL_OPCODE_FILE_DELETE` — remove from device flash.

The exact opcode names need to be cross-referenced against the iOS SDK;
our existing `rcsp.md` flags the range but not individual opcodes.

**Status.** Not implemented. Big — needs the RFCOMM transport before we
can attempt it.

---

## 4. Persistent mini-player

**What it does.** Bottom-of-screen track row + play button + queue icon
that survives navigation between Local Audio, Playback Order, etc. It
mirrors the headphones' current MP3-mode state regardless of which page
is on screen.

**Why it matters for us.** UX shape, not a new opcode. Once we have file
listing and MP3-mode playback we should adopt the same pattern. Worth
recording here so the UI doesn't get rebuilt.

**RCSP mapping.** Same opcodes as #3 plus periodic `SYS_INFO_GET` for the
"now playing" sub-attribute (track id, position, state).

**Status.** Pure UI; defer until #3 lands.

---

## 5. Battery percentage badge

**What it does.** Numeric percentage with green icon, anchored under the
device name on the home screen.

**Why it matters for us.** We already plumb battery via `BluetoothDevice
.getBatteryLevel()` reflection (Android-side AVRCP-derived value). That
reads whatever the system surfaces, which for OpenSwim Pro is reported
through HFP indicator. The Shokz app likely queries it via RCSP for
parity in MP3 mode (HFP is detached when there is no phone audio link).

**RCSP mapping.** `JL_OPCODE_SYS_INFO_GET` (op `0x07`) sub-attribute
`BATTERY` — already in `rcsp.md`.

**Status.** Partially implemented (Android reflection); should be
upgraded to the RCSP path so it works when HFP is idle.

---

## 6. EQ presets — promoted to a primary control

**What it does.** "Standard" / "Swimming" tiles on the device home; the
selected preset is highlighted, tapping the other instantly switches.

**Why it matters for us.** EQ is in our advanced-commands menu, but the
Shokz app surfaces it as a primary control, not a hidden submenu. That
ordering reflects how often it changes (basically once per environment).

**RCSP mapping.** `JL_OPCODE_SYS_INFO_SET` sub-attribute `EQ` — already
in `rcsp.md`.

**Status.** Stub in advanced menu. Promote to a card on the BT controls
screen once the RFCOMM transport ships.

---

## 7. "Earphone Mode" indicator (with info popup)

**What it does.** Static label between the battery and the mode tabs;
the info icon opens an explanation. Likely just describes the device
state in plain language ("you're wearing them; in-ear detection is
active" or similar).

**Why it matters for us.** Hints at **wear/in-ear detection** as a
queryable signal, which would be useful for "auto-pause" UX. OpenSwim
Pro probably doesn't have it (it's bone-conduction, no skin-contact
sensor), but the framework wires through.

**RCSP mapping.** Likely a `SYS_INFO_GET` sub-attribute we haven't
catalogued; could also be a `GET_TARGET_FEATURE` capability flag rather
than a runtime read.

**Status.** Defer until we have a confirmed sniff — risk of inventing a
feature the device does not actually expose.

---

## 8. Settings / firmware update entry point (gear icon, top-right)

**What it does.** Gear icon with an orange notification dot. The dot
implies a pending firmware update is available.

**Why it matters for us.** The dot is a *notification* feature, not
just a UI affordance — it implies the app polls Shokz' OTA service for
an update manifest and compares against the device's reported firmware
version.

**RCSP mapping.**
- `JL_OPCODE_SYS_INFO_GET` — sub-attribute `FIRMWARE_VERSION`.
- Cloud side: an HTTPS endpoint on Shokz infrastructure returning
  available firmware metadata. We don't have to hit Shokz; we can host
  our own manifest.
- Apply: the OTA opcode family (`0xE1`–`0xE8`) already listed in
  `rcsp.md` and stubbed in our advanced menu.

**Status.** OTA *upload* is a stub; OTA *availability check* is not yet
modeled at all.

---

## 9. Mailbox / messages icon (top-left)

**What it does.** Envelope icon with an orange dot. Shokz uses this for
in-app announcements and tutorials.

**Why it matters for us.** Not a device feature — pure cloud product
surface owned by Shokz. We won't reproduce it; out of scope.

**Status.** Skip.

---

## 10. Refresh local-audio scan

**What it does.** Refresh icon next to the Local Audio title bar.
Re-enumerates files on the device flash.

**Why it matters for us.** Implies file-list responses can grow stale
between explicit fetches; the device probably does not push change
notifications for the on-flash library. Our future implementation
should treat the listing as polled, not live.

**RCSP mapping.** Same as #3.

**Status.** Defer with #3.

---

## 11. Help / how-to popups

**What it does.** Question-mark icon on Local Audio (and the info icon
on Earphone Mode). Each opens an in-app explanation of that feature.

**Why it matters for us.** UX signal — Shokz invests in inline
explanations because the working-mode / MP3-mode model is non-obvious.
We should plan equivalent affordances when we ship the same controls.

**Status.** Pure UI; no protocol implication.

---

## 12. Volume slider as a primary control

**What it does.** Track-position bar at the bottom of the home screen
(actually the top half of the bar; the lower half is volume). This is
the same volume that the hardware buttons drive.

**Why it matters for us.** We already drive `STREAM_MUSIC` via
`AudioManager`. In **MP3 mode** the AVRCP volume signal is detached and
we will need RCSP `SYS_INFO_SET` sub-attribute `VOL` (already in our
catalogue) to move it.

**Status.** Bluetooth-mode volume works today; MP3-mode volume will
need the RCSP path.

---

## What we already cover

For completeness — these are visible in the Shokz app and already in
our advanced-commands menu, even if not yet wired up:

- Power off
- Reboot / factory reset
- Voice-prompt language toggle
- Multipoint connection
- Pairing-mode entry
- OTA upload

---

## Recommended priority for the next pass

1. **Confirm `FUNCTION_CMD` (`0x0E`) sub-opcode table** — unlocks #1 and
   the Local Audio control surface (#3, #4) at the same time.
2. **Confirm the file-transfer opcode names** — `0x1B`–`0x28` listed but
   not individually named.
3. **Implement an RCSP RFCOMM transport** behind the existing
   `BluetoothController` interface so the placeholders in the advanced
   menu return real data.
4. **Promote EQ presets and working-mode** out of the advanced menu and
   onto the main BT-controls screen, mirroring the Shokz layout
   (status card → mode tabs → EQ tiles → transport).
5. **Add Local Audio screen** as the primary "what's on my headphones
   right now" view (replacing or supplementing the USB-only file
   browser).
6. **Add a persistent mini-player** once #5 lands.
7. **OTA availability check** alongside the OTA upload stub.

The highest-value capture before any of this is a single **HCI snoop
log** of the Shokz app exercising the working-mode tabs and the Local
Audio screen — that nails down #1 and #3 at the wire level in one
session.

# Wear OpenSwim Pro companion: implementation note

Build a Wear OS companion screen for a paired Shokz OpenSwim Pro. The goal is a useful remote
control surface that remains honest about which headset operations are actually available.

## Do this after `:rcsp` lands

Do not create a parallel RCSP packet codec, Bluetooth socket transport, command catalogue, or
capability model. Wait for the `:rcsp` library module to be available, then consume its public
APIs. This Wear work owns UI, state presentation and, eventually, an OpenSwim-specific
authenticated adapter—not a duplicate protocol stack.

## Foundation

- Depend on the new `:rcsp` Kotlin Multiplatform library for device facts and command capability
  checks. Do not depend on the current app's Bluetooth controller directly.
- The observed headset endpoint is RFCOMM channel 10 on
  `0000fef0-0000-1000-8000-00805f9b34fb`.
- The Shokz Android app uses a vendor `A5 5A` envelope. It is not the public Jieli
  `FE DC BA … EF` format, so a generic RCSP socket must not be used to control OpenSwim Pro.
- The vendor envelope has a request type of `1` and a response type of `0x101`; simple Local
  Audio requests use a fixed-width, little-endian tagged-word payload. Treat that byte format as
  an adapter implementation detail: UI must continue to use semantic `RcspCommand`s.
- Start from `OpenSwimProProfile`. It supports only the captured Local Audio `Play`, `Pause`,
  `PreviousTrack`, and `NextTrack` operations. Every interactive control must be gated with
  `profile.isSupported(command)` / `gateway.isSupported(command)`.

## Finding and connecting the headset

OpenSwim Pro is a Bluetooth Classic RFCOMM device, not a BLE/GATT peripheral.

1. First enumerate `BluetoothAdapter.bondedDevices` and let the user select the paired headset.
2. When it is not paired, perform normal Classic Bluetooth discovery while the headset is in
   pairing mode, then initiate Android system pairing. On Android 12+, request
   `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` as appropriate.
3. Verify the selected device exposes service UUID
   `0000fef0-0000-1000-8000-00805f9b34fb`. The observed connection uses RFCOMM channel 10.
4. Do not use hidden channel-opening APIs or implement a raw socket layer. Use the library's
   Android transport/adapter entry point once it is available.
5. The headset may already be connected to the phone. Represent that as a resolvable
   “connect to watch” state and guide the user through pairing or multipoint conflicts.

## UI and state

Use a single state holder that renders from these independent sources:

1. `connection`: disconnected, connecting, authenticated, unavailable/error.
2. `mode`: Bluetooth, Local Audio, Line In, USB, or unknown.
3. `media`: track title/index/total, play state, and position/duration only when actually
   reported by the headset.
4. `capabilities`: `RcspCapabilities` for the current device and firmware.

The Wear screen should have:

- A compact connection/status row, including current mode.
- A mode selector (Bluetooth / Local Audio / Line In) only when
  `SelectWorkingMode` is supported; otherwise show the current mode as read-only.
- Previous, play/pause, and next buttons, each independently capability-gated and shown only in
  Local Audio mode. Do not use Android media-session controls as a proxy for OpenSwim controls.
- A local-audio playlist page with track title, position such as `2 of 4`, selected/playing state,
  and a tap-to-play action only when `ListLocalAudio` and `PlayLocalAudio` are supported.
- Track progress and seeking only when both a genuine position/duration source and
  `SeekRelative` support exist. For OpenSwim Pro MP3 mode, show no scrubber or elapsed time:
  the Shokz app's active Local Audio UI does not expose one, and seeking is unverified.
- A volume control only when `SetVolume` is supported. Physical OpenSwim volume buttons are
  headset-local and produced no captured RFCOMM traffic; do not imply that their value is synced
  to the watch.

Disabled controls should explain why in accessible semantics, for example: “Not supported by this
headset firmware.” Avoid a permanently visible grid of grey controls; prefer hiding unavailable
actions behind a small “available controls” section or a capability detail screen.

## Transport boundary

The Wear module should consume a small adapter interface that exposes semantic commands and state,
not raw bytes. The adapter owns authentication, sequence/response correlation, reconnection, and
timeouts. It may use `RcspCommandGateway` after it has populated capabilities.

`PublicJieliCommandEncoder` is useful only for devices verified to use public RCSP. It has a
conservative implementation for battery/volume/equalizer/working-mode queries and volume write;
it intentionally returns no packet for media, seek, mode-select, or local-file commands. Do not
wire it to `OpenSwimProProfile`. For captured Local Audio transport actions, use
`ShokzOpenSwimController` with `AndroidShokzOpenSwimTransport`.

## Explicit non-goals until new evidence exists

- Do not send guessed headset volume or seek packets.
- Do not infer an operation solely from a matching request/response shape. Record a single-action
  capture, replay it after the Shokz app has released the RFCOMM socket, and verify the headset's
  visible or audible effect before adding it to `OpenSwimProProfile`.
- Do not treat the Flutter SoLoud seek/volume methods found in the Shokz APK as Bluetooth
  controls: they are phone-local audio rendering methods.
- Do not infer a progress value from playlist selection or Android media playback state.
- Do not transfer music from the watch. OpenSwim file transfer is USB-based; the app's Local Audio
  sync observed four existing tracks as metadata.

## Acceptance checks

- A disconnected watch never attempts Bluetooth I/O and communicates how to connect.
- Unsupported OpenSwim controls are absent or plainly labelled unsupported, without raw writes.
- A fake adapter with `Play`, `Pause`, `NextTrack`, and `PreviousTrack` enabled verifies independent
  button enablement and state updates.
- A fake adapter with local-audio commands enabled verifies playlist/index rendering.
- A fake adapter enabling `SeekRelative` plus position/duration verifies the scrubber; omitting
  either hides it.

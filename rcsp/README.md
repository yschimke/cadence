# RCSP Kotlin

`rcsp` is a small Kotlin Multiplatform library for feature-gated control of Jieli RCSP audio
devices. It separates three concerns: a semantic command catalogue, per-device command support,
and transport/vendor packet encoding.

```kotlin
val profile = RcspDeviceProfile(
  name = "Verified public RCSP device",
  serviceUuid = "0000fef0-0000-1000-8000-00805f9b34fb",
  capabilities = RcspCapabilities().withSupported(
    RcspCommandId.QueryVolume,
    RcspCommandId.SetVolume,
  ),
)

if (profile.isSupported(RcspCommand.SetVolume(60u))) {
  gateway.execute(RcspCommand.SetVolume(60u), PublicJieliCommandEncoder)
}
```

The public encoder implements only the documented system-information operations: battery,
volume, equalizer, and working-mode queries, plus volume writes. The command catalogue also
models media play/pause/navigation, relative seeking, local-audio control, pairing and device
settings. Those commands require a chipset- and firmware-verified `RcspCommandEncoder`.

## OpenSwim Pro

`OpenSwimProProfile` captures the observed FEF0 RFCOMM channel 10 endpoint. Its Shokz app sends
an `A5 5A` vendor envelope rather than public `FE DC BA … EF` frames. The module reconstructs the
captured commands byte-for-byte; `AndroidShokzOpenSwimTransport` sends them over a connected
socket. Its supported surface includes Local Audio transport, Bluetooth/MP3 mode selection, Local
Audio volume, Standard/Vocal/Swimming EQ, and Local Audio playback order. Seek, playlist sync,
and track identifiers remain unsupported and will not be encoded.

### Observed request/response shape

The envelope is deterministic and has no observed session-authentication field:

```text
A5 5A | outerLength:u16le | type:u32le | payloadLength:u16le | payloadCrc:u16le | payload
```

App requests use `type = 1`; headset replies use `type = 0x101`. Both payload checksums are
CRC-16/Modbus, but the seed is operation-class-specific: captured media-control requests use
`0x86F7`, simple setters and the Local Audio state query use `0x7649`, EQ writes use `0x742D`,
media acknowledgements use `0x7649`, and Local Audio state replies use `0xA492`.

The four verified media requests have a 40-byte, little-endian word payload:

```text
[1, 32, 2, 4, 1, 3, 4, action, 4, 0]
```

Only `action` varies: `3` is play/resume, `4` pause, `7` previous track, and `8` next track.
The inner words look like a tagged RPC argument list, but their individual field names are not yet
known.

### Operation inventory

The entries below describe firmware `R_24_202520250815`. “Verified” means the request was replayed
from Cadence while the Shokz app had released the RFCOMM socket, and the headset or Shokz UI showed
the expected result. “Observed” means it appeared in a capture only and is intentionally absent
from [OpenSwimProProfile].

| Wire operation | Semantic command | Evidence | Library status |
| --- | --- | --- | --- |
| media action `3` | `Play` | Headset resumed playback | Verified, supported |
| media action `4` | `Pause` | Headset paused playback | Verified, supported |
| media action `7` | `PreviousTrack` | Shokz UI changed to the preceding track | Verified, supported |
| media action `8` | `NextTrack` | Shokz UI changed to the following track | Verified, supported |
| query `0x40` / reply `0x8040` | `QueryMediaState` | Cadence replay acknowledged by the headset | Verified, supported; response fields are not public yet |
| setter `0x25`: `0`/`1` | `SelectWorkingMode(Bluetooth/LocalAudio)` | Cadence replay changed the official Shokz UI between Bluetooth and MP3 | Verified, supported |
| setter `0x0A`: raw `0…16` | `SetVolume(0…100)` | Shokz app volume-slider capture; library maps percent to the device's 16 steps | Captured, supported |
| EQ setter `0x0E`: `1`/`2`/`7` | `SelectEqualizerPreset(Standard/Vocal/Swimming)` | Shokz app action captures; Vocal replay acknowledged through Cadence | Verified, supported |
| setter `0x26`: `0`/`1`/`2` | `SelectPlaybackOrder(Normal/Shuffle/Repeat)` | Shokz app action captures | Captured, supported |
| pairing operations `0x10`/`0x11` | multipoint device-list / state mutation | Captured while toggling multipoint; values include paired-device identifiers | Observed only; unsafe to expose without a state API |
| query `0x02` | device/firmware identity | Reply contains `SEALU_R_R_24_202520250815` | Observed; no semantic command yet |
| queries `0x03`, `0x05`, `0x07`, `0x08`, `0x10`, `0x1c`, `0x1d`, `0x42` | startup capability/state probes | Replies are captured, but their values are unmapped | Observed only |
| operations `0x41`, `0x42`, `0x46`, `0x47`, `0x49`, `0x4a` | Local Audio sync / filesystem workflow | Sync replies include directory and MP3 filename metadata | Observed only |
| state mutation `0x43` | unknown | Two acknowledged replays changed the Shokz Local Audio title, but values `1` and `4` did not map to stable playlist ordinals | Explicitly blocked |

The `0x40` query uses the same tagged-word convention but a different request shape:

```text
[1, 36, 2, 4, 2, 3, 4, 0x40, 4, 4, 0]
```

Its `0x8040` reply has a changing nested value. It is suitable as a liveness/state query, but it
must not yet be shown as a track number, play state, duration, or position.

Do not infer an operation from a matching packet shape. In particular, do not emit guesses for
`0x43`, seeking, filesystem writes, or multipoint mutations. `0x43` has been acknowledged by the
headset but its values do not map to stable playlist ordinals; it remains deliberately blocked.

`AndroidRfcommTransport` is available for a connected public-RCSP `BluetoothSocket`; it performs
framing only, and deliberately does not bypass Android Bluetooth permission or OEM authentication.

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
captured Local Audio `Play`, `Pause`, `PreviousTrack`, and `NextTrack` requests byte-for-byte;
`AndroidShokzOpenSwimTransport` sends them over a connected socket. Those controls should appear
only in Local Audio mode. Volume, seek, mode selection, and playlist selection remain unsupported
and will not be encoded.

### Observed request/response shape

The envelope is deterministic and has no observed session-authentication field:

```text
A5 5A | outerLength:u16le | type:u32le | payloadLength:u16le | payloadCrc:u16le | payload
```

App requests use `type = 1`; headset replies use `type = 0x101`. Both payload checksums are
CRC-16/Modbus, with a captured request seed of `0x86F7` and response seed of `0x7649`.

The four verified media requests have a 40-byte, little-endian word payload:

```text
[1, 32, 2, 4, 1, 3, 4, action, 4, 0]
```

Only `action` varies: `3` is play/resume, `4` pause, `7` previous track, and `8` next track.
The inner words look like a tagged RPC argument list, but their individual field names are not yet
known. Other captured startup and sync traffic uses the same outer envelope and related word
shapes, so it can be enumerated safely through single-action captures. It must not be published as
a command until the request is replayed and the headset effect is verified.

`AndroidRfcommTransport` is available for a connected public-RCSP `BluetoothSocket`; it performs
framing only, and deliberately does not bypass Android Bluetooth permission or OEM authentication.

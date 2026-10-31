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

`OpenSwimProProfile` captures the observed FEF0 RFCOMM channel 10 endpoint, but reports no
supported commands. Its Shokz app sends an authenticated `A5 5A` envelope rather than public
`FE DC BA … EF` frames. Consequently, this module will never send a volume, seek or media packet
to OpenSwim Pro until an authenticated adapter proves the concrete command mapping.

`AndroidRfcommTransport` is available for a connected public-RCSP `BluetoothSocket`; it performs
framing only, and deliberately does not bypass Android Bluetooth permission or OEM authentication.

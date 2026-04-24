# Protocols — where control actually happens

Rolling map of known/plausible control surfaces and our current confidence.

## USB

**None.** USB is pure mass-storage (SCSI Bulk-Only, `05e3:0761`). See
[usb.md](usb.md). No firmware files or config files exposed via the FAT32
volume either. The library **will not** include a USB transport.

## Bluetooth — Classic audio / call

Fully covered by standard profiles and BlueZ handles them natively:

- **A2DP Sink (UUID `0x110B`)** — audio streaming host → device. Library
  consumers get this "for free" from the OS audio stack.
- **AVRCP Controller + Target (`0x110E`/`0x110F`/`0x110C`)** — media keys,
  track metadata, volume. Also handled by the OS.
- **HFP (`0x111E`)** — voice calls + `battchg` CIND indicator. Battery via
  `+CIEV: 7,N` on a 0-5 scale is **technically usable** but low-resolution
  and timing-dependent. Not the right source for a user-facing battery %.

None of these are what the library is *for*. The library's job is the device
control channel below.

## Bluetooth — vendor control (the real target)

**Protocol name: RCSP** (Jieli's "Remote Control Slave Protocol"). Source of
truth: [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth) — the
vendor-published iOS SDK, which includes Swift source + `xcframework` that
implement every opcode. Confirmed supported chip families per the SDK README:
AC693X, AC697X, AC695X, AC707N, JL701N.

**Transport on OpenSwim Pro:**

- RFCOMM over L2CAP
- Channel **10** (discovered via SDP — do not hardcode)
- Service UUID `0000fef0-0000-1000-8000-00805f9b34fb`, SDP service name `JL_SPP`
- Classic BR/EDR only — there is no BLE / GATT equivalent on this device

A second SPP endpoint at RFCOMM channel **1** / UUID `0x1101` appears to be a
legacy / test channel (likely speaks RCSP too).

**Feature areas the SDK exposes** (i.e. things the library can target once we
have opcode catalogue):

- Battery level (live %, not 0-5 HFP scale)
- Device info (model, firmware version, serial)
- EQ presets and custom EQ
- Voice-prompt language / volume
- Multipoint / TWS pairing state
- Firmware OTA update
- Button-mapping / gesture config
- Auto-power-off timer
- (Not applicable on OpenSwim Pro: ANC, hearing-aid fitting, watch faces, AI translation)

**Auth:** SDK references a `JL_HashPair.xcframework`. Jieli devices typically
negotiate a small hash-based handshake on SPP connect before accepting command
packets. The specifics are inside that xcframework and/or in the Android app
(`cn.com.aftershokz.app`). We do not yet know whether the OpenSwim Pro enforces
it or accepts unauthenticated commands.

**RCSP packet framing: not yet documented here.** Needs extraction from one of:

- `Jieli-Tech/iOS-JL_Bluetooth` Swift sources (cleanest — vendor-authored)
- `Jieli-Tech/fw-AC63_BT_SDK` C firmware (ground truth, less ergonomic)
- Decompile of the Shokz Android APK (`cn.com.aftershokz.app`)
- Live RFCOMM sniff of the Shokz app talking to the headphones (Android btsnoop)

See [next-steps.md](next-steps.md).

## Prior-art inventory

| Source | What's there |
| --- | --- |
| [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth) | **Official Jieli iOS SDK, Swift source.** Covers RCSP framing, opcode catalogue, OTA, hash-pair. Primary reference. |
| [`Jieli-Tech/fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK) | Official firmware SDK (C) for AC63xN, compatible with AC69 audio-less. Ground-truth for packet format and opcode numbers. |
| [`kagaimiq/jielie`](https://github.com/kagaimiq/jielie) + [site](https://kagaimiq.github.io/jielie/) | Community RE: chip identification, u-boot/ISP protocol, programming interface. Less focused on runtime control protocol. |
| [`kagaimiq/jl-uboot-tool`](https://github.com/kagaimiq/jl-uboot-tool) | Jieli bootloader/ISP tool — useful only if we ever want host-side firmware flashing via USB DFU (not applicable here: no DFU mode observed on USB). |
| [`buzzcola3/JieLi-AC690X-Programming`](https://github.com/buzzcola3/JieLi-AC690X-Programming) / [`christian-kramer/JieLi-AC690X-Familiarization`](https://github.com/christian-kramer/JieLi-AC690X-Familiarization) | Older RE on AC690X. Some protocol bits, but superseded by vendor-published SDKs above. |
| [Madushan 2025 blog](https://madushan.caas.lk/posts/2025-08-09-reverse-engineering-jieli-sdk/) | Write-up walking through the Jieli SDK structure. Good orientation before diving into sources. |
| [Hackaday 2022](https://hackaday.com/2022/03/26/reverse-engineering-your-own-bluetooth-audio-module/) | Beginner-friendly RE of a cheap Jieli BT audio module. Context only. |

## Shokz app

- Android package: `cn.com.aftershokz.app` (current version 5.7.7 at time of
  writing, [Play Store](https://play.google.com/store/apps/details?id=cn.com.aftershokz.app)).
- iOS counterpart exists.
- Likely embeds a build of `JL_Bluetooth` (iOS) / Jieli Android SDK plus
  Shokz-specific UI. Decompiling the APK would confirm which RCSP opcodes
  Shokz actually exercises vs the full SDK surface — useful if we want the
  library to track "what the Shokz app supports" rather than "everything the
  chip supports."

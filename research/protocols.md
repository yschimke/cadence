# Protocols — control-channel options

What the device exposes, and which surface a host-side library would
target. Sourced entirely from public protocol specifications and the
chip vendor's openly-published SDKs.

## USB

Pure mass-storage (SCSI Bulk-Only). See [usb.md](usb.md). No firmware,
config, or log files are surfaced via the FAT32 volume on the firmware
revision observed. USB is therefore a *content* channel only (copy
audio files in), not a control channel.

## Bluetooth — Classic audio / call

Standard profiles, handled natively by the host OS:

- **A2DP Sink (UUID `0x110B`)** — audio streaming.
- **AVRCP Controller + Target (`0x110E` / `0x110F` / `0x110C`)** —
  media keys, track metadata, volume.
- **HFP (`0x111E`)** — voice + the `battchg` CIND indicator (0–5
  resolution, not the right source for a user-facing battery %).

These are out of scope for an interop library.

## Bluetooth — vendor control channel

The Jieli BT SoC family used in this device speaks **RCSP** (Jieli's
"Remote Control Slave Protocol") on RFCOMM. The vendor publishes both
an iOS SDK and a firmware SDK on GitHub under permissive licences:

- [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth)
- [`Jieli-Tech/fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK)

These are the canonical references; see [rcsp.md](rcsp.md) for the
relevant subset of opcodes a client would target.

**Transport on this device family:** RFCOMM over L2CAP, channel
discovered via SDP at connect time (do not hardcode), service UUID
`0000fef0-0000-1000-8000-00805f9b34fb`, SDP service name `JL_SPP`.
Classic BR/EDR only — no BLE / GATT equivalent.

## Feature areas the public SDK exposes

(i.e. the opcode space a host-side library can target):

- Battery level (live %)
- Device info (model, firmware version)
- EQ presets and custom EQ
- Voice-prompt language / volume
- Multipoint / TWS pairing state
- Firmware OTA update
- Auto-power-off timer

## Authentication

The chip vendor's SDK ships a `JL_HashPair.xcframework` for iOS. The
public `JL_rcsp_api.h` shows the host-side auth function takes the
BR/EDR link key + BD_ADDR — i.e. derived from the standard pairing
secret rather than from a separate vendor key. Whether the device
enforces it on every session, or accepts unauthenticated commands once
paired, varies per firmware build and is best checked empirically
against hardware the user owns.

## Public prior art

| Source | What's there |
| --- | --- |
| [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth) | Vendor's iOS SDK, Swift source. Covers RCSP framing, opcode catalogue, OTA. |
| [`Jieli-Tech/fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK) | Vendor's firmware SDK (C). Ground truth for packet format and opcode numbers. |
| [`kagaimiq/jielie`](https://github.com/kagaimiq/jielie) | Community notes on chip identification and the u-boot/ISP interface. |
| [Madushan 2025 blog](https://madushan.caas.lk/posts/2025-08-09-reverse-engineering-jieli-sdk/) | Walk-through of the Jieli SDK structure. |

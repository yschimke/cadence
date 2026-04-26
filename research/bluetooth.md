# Bluetooth surface — Jieli-based BR/EDR audio device

Notes from a paired BR/EDR audio device using a Jieli BT SoC. Generic
host setup (Linux + BlueZ); host- and device-specific identifiers are
omitted intentionally.

## Identity (generic)

| Field            | Value                                                  |
| ---------------- | ------------------------------------------------------ |
| Bearers          | **BR/EDR only** (no LE bearer, no LE advertising)      |
| Pairing          | Secure Simple Pairing (Just Works)                     |
| Class of Device  | Audio/Video › Wearable headset, services Audio + Rendering |
| PnP (UUID 0x1200)| Vendor `0x05D6` (Jieli pattern), Source = Bluetooth SIG |

## SoC identification

SDP service records advertise `JL_A2DP`, `JL_HFP`, `JL_SPP` — the
default Jieli SDK naming. Combined with the Jieli-pattern PnP vendor
ID `0x05D6`, this identifies the device's BT SoC as Jieli (typically
AC69xN or AC63xN family, both covered by `fw-AC63_BT_SDK`).

## Profiles exposed (typical SDP browse)

| Service UUID(s)                                       | Name       | Transport    | RFCOMM ch |
| ----------------------------------------------------- | ---------- | ------------ | --------- |
| `0x110B` Audio Sink                                   | `JL_A2DP`  | L2CAP+AVDTP  | —         |
| `0x110E` / `0x110F` AVRCP Controller                  | —          | L2CAP+AVCTP  | —         |
| `0x110C` AVRCP Target                                 | —          | L2CAP+AVCTP  | —         |
| `0x111E` Handsfree + `0x1203` Generic Audio           | `JL_HFP`   | L2CAP+RFCOMM | discovered |
| `0x1101` Serial Port                                  | `JL_SPP`   | L2CAP+RFCOMM | discovered |
| `0000fef0-…` (vendor)                                 | `JL_SPP`   | L2CAP+RFCOMM | discovered |

### The `0xFEF0` UUID caveat

`0x0000FEF0` is allocated by the Bluetooth SIG to a different vendor
entirely; some BlueZ tooling will surface that vendor's name. Treat
the UUID as a magic number to match against, not as an identifier of
the SIG-registered owner.

### Two SPP channels

- `JL_SPP` at UUID `0x1101` — the plain SPP entry, typically used by
  older Jieli SDK test apps.
- `JL_SPP` at UUID `0xFEF0` — vendor-flavoured UUID; the channel
  companion apps tend to target so they can discriminate from
  bystander SPP services.

Both run RCSP framing (see [rcsp.md](rcsp.md)). The exact RFCOMM
channel numbers are advertised per device — discover via SDP at
connect time, do not hardcode.

## HFP Service Level Connection

Standard SLC at pair time. The notable detail for a battery-aware
client:

- `battchg` CIND indicator (range 0–5) is the only HFP battery
  signal exposed — no Apple-style `AT+XAPL` / `AT+IPHONEACCEV`.
- BlueZ's `org.bluez.Battery1.Percentage` may default to `100` with an
  empty `Source` when no `+CIEV: 7,N` arrives; **prefer reading
  battery via RCSP over the vendor SPP channel**, where live percent
  is available.

## Anti-findings (useful negatives)

- **No BLE bearer, no LE advertising, no GATT services.** A library
  for this device family should ignore GATT entirely.
- **No Apple-Accessory-Protocol AT commands.** No iAP-style battery /
  Siri extensions.
- **No OBEX / PBAP / MAP / HID.** Purely a Classic audio device plus
  the SPP channels.
- USB `iSerial` and BT-PnP rev are typically firmware build tags, not
  per-unit serials. Use the BD_ADDR as the stable per-unit
  identifier at connect time.

## Stable identifiers a library should use

1. SDP service name match `^JL_` plus the PnP vendor ID `0x05D6` — to
   recognise "this is a Jieli-based device" generically.
2. SDP service `JL_SPP` at UUID `0xFEF0` — confirms the device speaks
   RCSP before the client opens the socket. Discover the channel
   number via SDP, do not hardcode.
3. The connecting device's BD_ADDR — for stable per-unit identity
   inside the user's own paired-devices list.

# Bluetooth surface — Shokz OpenSwim Pro

Captured 2026-04-24 on Linux host (kernel 7.0.0-1-cachyos, BlueZ 5.86), adapter
`hci0 = 04:33:C2:21:F6:0A` (Intel AC 3168). Full HCI trace in
[`captures/pair-and-probe.pcapng`](captures/pair-and-probe.pcapng).

## Identity

| Field            | Value                                       |
| ---------------- | ------------------------------------------- |
| BD_ADDR          | `A8:F5:E1:7A:60:72` (public BR/EDR)         |
| OUI              | `A8-F5-E1` → **Shenzhen Shokz Co., Ltd.** (IEEE-registered) |
| Friendly name    | `OpenSwim Pro by Shokz`                     |
| Class of Device  | `0x00240404` — Audio/Video › Wearable headset, services = Audio + Rendering |
| Bearers          | **BR/EDR only** (`org.bluez.Bearer.BREDR1`; no `Bearer.LE1`, no LE advertising) |
| Legacy pairing   | No — Secure Simple Pairing (Just Works)     |
| PnP (UUID 0x1200)| VendorID=`0x05D6`, Product=`0x000A`, Version=`0x0240`, Source=Bluetooth SIG |
| Modalias         | `bluetooth:v05D6p000Ad0240` (matches PnP record) |

## SoC identification

The SDP service records contain attribute `0x0100` (Service Name) with values
**`JL_A2DP`**, **`JL_HFP`**, **`JL_SPP`** — the default SDK naming from **Zhuhai
Jieli Technology Co., Ltd. (杰理科技)**. Combined with the Jieli-pattern PnP
vendor ID `0x05D6`, this is unambiguous: **the OpenSwim Pro uses a Jieli
Bluetooth SoC**, most likely in the AC69xN or AC63xN family (covered by
[`fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK)).

The firmware revision `0x0240` matches the USB `bcdDevice` — single firmware
tag across both surfaces.

## Profiles exposed (full SDP browse — from pcap)

| Handle     | Service UUID(s)                                       | Name       | Transport    | RFCOMM |
| ---------- | ----------------------------------------------------- | ---------- | ------------ | ------ |
| 0x00010001 | `0x110B` Audio Sink                                   | `JL_A2DP`  | L2CAP+AVDTP  | —      |
| 0x00010002 | `0x110E` A/V Remote Control, `0x110F` AVRCP Controller| —          | L2CAP+AVCTP  | —      |
| 0x00010005 | `0x110C` A/V Remote Control Target                    | —          | L2CAP+AVCTP  | —      |
| 0x00010003 | `0x111E` Handsfree + `0x1203` Generic Audio           | `JL_HFP`   | L2CAP+RFCOMM | **4**  |
| 0x00010004 | `0x1101` Serial Port                                  | `JL_SPP`   | L2CAP+RFCOMM | **1**  |
| 0x00010011 | `0000fef0-0000-1000-8000-00805f9b34fb` (vendor)       | `JL_SPP`   | L2CAP+RFCOMM | **10** |
| 0x0001000a | `0x1200` PnP Information                              | —          | —            | —      |

### The `0xFEF0` UUID caveat

`0x0000FEF0` is **officially allocated by the Bluetooth SIG to Intel Corp.** —
that is why `bluetoothctl` shows it labelled "Intel". Shokz / Jieli are using
someone else's SIG-registered UUID as a custom service UUID, which is
spec-violating but a common pattern on low-cost Chinese BT SoCs. Treat the
UUID purely as a magic number to match against, not as an identifier of
"Intel something."

### Two SPP channels — why both?

- **Channel 1 (`JL_SPP`, UUID 0x1101)** — the plain-vanilla SPP entry;
  typically used by older Jieli SDK test apps or generic terminal tools.
- **Channel 10 (`JL_SPP`, UUID 0xFEF0)** — the primary channel the Shokz
  mobile app targets. Same Jieli stack, but advertised under a vendor UUID so
  the companion app can discriminate its device from bystander SPP services.

Both are RFCOMM-over-L2CAP and run the same underlying framing: Jieli's **RCSP**
protocol (see [protocols.md](protocols.md)).

## HFP Service Level Connection (observed)

SLC completed at pair time. Full AT exchange in
`captures/pair-and-probe.pcapng`, but the highlights:

| AT exchange                | Device response                                            |
| -------------------------- | ---------------------------------------------------------- |
| `AT+BRSF=671`              | `+BRSF: 3584` — AG features bitmap                         |
| `AT+BAC=1,2`               | `OK` — accepts CVSD + mSBC wideband                        |
| `AT+CIND=?`                | `("service",(0-1)),("call",(0-1)),("callsetup",(0-3)),("callheld",(0-2)),("signal",(0-5)),("roam",(0-1)),("battchg",(0-5))` |
| `AT+CIND?`                 | `+CIND: 0,0,0,0,0,0,0` — all indicators zero at SLC time   |
| `AT+CMER=3,0,0,1`          | `OK` — host enabled unsolicited indicator updates          |
| `AT+CHLD=?`                | `(0,1,1x,2,2x,3)` — standard 3-way set                     |
| `AT+BCS=2`                 | `OK` — host selected mSBC                                  |

- `battchg` indicator (range 0-5) is the **only battery signal exposed** — no
  `AT+XAPL` / `AT+IPHONEACCEV` / `AT+XEVENT` Apple-style battery reporting.
- BlueZ populated `org.bluez.Battery1.Percentage = 100` with an **empty
  `Source`**. We did not observe any `+CIEV: 7,N` update during the 150-second
  capture — the `100%` is almost certainly a BlueZ default, not a real read.
  The library should **ignore BlueZ's Battery1 for this device and read battery
  via RCSP over RFCOMM ch 10**, where live percentage is available.

## A2DP

Three sink SEPs enumerated (`sep1`, `sep2`, `sep3`). `sep2` activated at pair
time with `Delay = 2200 µs` and `Volume = 0x2F (47)`. BlueZ is an AVRCP
Controller; the headphones are an AVRCP Target (standard for a sink).

## Anti-findings (useful negatives)

- **No BLE bearer, no LE advertising, no GATT services** — the device does not
  speak BLE at all on this firmware. Ignore GATT entirely in the library.
- **No Apple-Accessory-Protocol AT commands** — the Shokz app does not piggyback
  on Apple's iAP battery/Siri extensions.
- **No OBEX / PBAP / MAP / HID / AVRCP-absolute-volume gotchas** — purely a
  Classic audio device plus two SPP channels.
- **Serial `000000002402`** (from USB) and `bcdDevice=24.02` and BT-PnP rev
  `0x0240` are all the same number. **None of them are a per-unit serial** —
  they are firmware build tags. For stable device identity the library should
  use the BD_ADDR, not any of these.

## Stable identifiers the library should use

1. **OUI prefix `A8:F5:E1`** — matches any Shokz device, future-proof.
2. **Friendly name regex `^OpenSwim Pro`** — matches this model specifically;
   other Shokz models will have different prefixes but the same OUI.
3. **SDP service `JL_SPP` at UUID `0xFEF0` on RFCOMM ch 10** — confirms the
   device speaks RCSP before we open the socket. Channel number is advertised
   per-device; do not hardcode `10` — discover via SDP at connect time.

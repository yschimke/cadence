# USB surface — Shokz OpenSwim Pro

Captured 2026-04-24, device connected to Linux host (kernel 7.0.0-1-cachyos) via USB-C.

## Identity

| Field            | Value                                           |
| ---------------- | ----------------------------------------------- |
| Sysfs path       | `1-4.4.4` (bus 1, device 60)                    |
| VID:PID          | `05e3:0761` — Genesys Logic, Inc.               |
| `bcdUSB`         | 2.01                                            |
| `bcdDevice`      | 24.02                                           |
| `iManufacturer`  | *(empty, index 0)*                              |
| `iProduct`       | `USB Storage`                                   |
| `iSerial`        | `000000002402`                                  |
| Negotiated speed | High-Speed (480 Mbps)                           |
| Power            | Bus-powered, 500 mA max, Remote Wakeup set      |

VID/PID is the Genesys Logic USB-to-SATA/eMMC bridge chip — not a Shokz identifier. The
serial `000000002402` mirrors `bcdDevice`, so it is almost certainly a **firmware build
tag, not a per-unit serial**. Cannot be relied on to distinguish two OpenSwim Pros.

The only positive "this is an OpenSwim Pro" signal at the USB layer is the **FAT32
volume label `SWIM PRO`** on the exposed partition.

## Descriptor layout

One configuration, one interface, two bulk endpoints — nothing else.

```
Configuration 1  (bmAttributes 0xa0, 500 mA, Remote Wakeup)
└── Interface 0, alt 0
    ├── bInterfaceClass    = 0x08  Mass Storage
    ├── bInterfaceSubClass = 0x06  SCSI transparent
    ├── bInterfaceProtocol = 0x50  Bulk-Only Transport (BBB)
    ├── EP 0x81 IN   bulk, 512 B
    └── EP 0x02 OUT  bulk, 512 B
```

No HID, no Audio Class, no CDC/ACM, no vendor-specific interface, no alternate
settings. Device descriptor class/subclass/protocol are all `0` (per-interface
classing), which matches a plain UMS stick.

## Block-device view

| Field      | Value                                         |
| ---------- | --------------------------------------------- |
| Block dev  | `/dev/sda` → `sda1`                           |
| Capacity   | 29.1 GiB (advertised 32 GB)                   |
| Partition  | single, FAT32, label `SWIM PRO`               |
| SCSI INQ   | Vendor `Generic`, Model `MassStorageClass`, Rev `2402`, ANSI 6 |

Filesystem root contents on this unit:

```
FSCK0000.REC … FSCK0003.REC   ~250 MB of CHKDSK recovery chains (prior fs damage)
LOST.DIR/                     (empty — Android MTP-era artifact)
PODCASTS/                     user content
SYSTEM/                       empty directory
System Volume Information/    Windows indexer metadata only
```

The `SYSTEM/` directory is **empty** on this firmware — no config files, no firmware
image, no logs surfaced via mass storage. There is therefore **no control / tuning /
firmware-update channel reachable from the USB side** on this revision.

## Implications for the library

- USB is a dead end for control: it is purely a music-loading channel.
- The library does not need a USB transport. If we ever add "manage on-device
  playlists from the host", it would be a filesystem-level feature (copy MP3s into
  the mounted volume), not a protocol-level one.
- All interactive protocol work (battery, EQ, mode switching, firmware info, OTA,
  voice prompts, multipoint) must happen over Bluetooth.

## Open questions deferred to later

- Does the device expose anything different over USB while **powered off /
  charging only** vs. **powered on**? Current capture is powered-on + charging.
- Does holding the mode button during connect enter a different USB mode
  (DFU / bootloader)? Worth a careful try once we have `usbmon` running.

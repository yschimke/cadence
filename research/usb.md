# USB surface — observed device profile

Profile of the USB face of a Jieli-based BR/EDR audio device, as
relevant to a host-side library. Host-specific paths and per-unit
identifiers are omitted.

## Identity

| Field            | Value                                           |
| ---------------- | ----------------------------------------------- |
| VID:PID          | `05e3:0761` — Genesys Logic, Inc. USB-to-storage bridge |
| `bcdUSB`         | 2.01                                            |
| `iProduct`       | `USB Storage`                                   |
| `iSerial`        | Mirrors `bcdDevice` — firmware build tag, not a per-unit serial |
| Negotiated speed | High-Speed (480 Mbps)                           |
| Power            | Bus-powered, 500 mA max, Remote Wakeup set      |

The VID/PID belongs to the Genesys Logic bridge chip and is **not** a
device-vendor identifier. The serial mirrors `bcdDevice` so it is a
firmware build tag and cannot be relied on to distinguish two units of
the same model.

## Descriptor layout

One configuration, one interface, two bulk endpoints — nothing else.

```
Configuration 1  (bmAttributes 0xa0, 500 mA, Remote Wakeup)
└── Interface 0, alt 0
    ├── bInterfaceClass    = 0x08  Mass Storage
    ├── bInterfaceSubClass = 0x06  SCSI transparent
    ├── bInterfaceProtocol = 0x50  Bulk-Only Transport (BBB)
    ├── EP IN   bulk, 512 B
    └── EP OUT  bulk, 512 B
```

No HID, no Audio Class, no CDC/ACM, no vendor-specific interface, no
alternate settings. Plain UMS.

## Block-device view

| Field      | Value                                  |
| ---------- | -------------------------------------- |
| Capacity   | ~29 GiB usable (advertised 32 GB)      |
| Partition  | single, FAT32                          |
| SCSI INQ   | Vendor `Generic`, Model `MassStorageClass` |

The exposed FAT32 volume is content-only — there is no firmware
image, no config file, no log surfaced via the mass-storage path on
the firmware revision observed. There is therefore no control /
tuning / firmware-update channel reachable from the USB side.

## Implications for a library

- USB is a content channel only. The library does not need a USB
  *control* transport.
- "Manage on-device audio from the host" is a filesystem feature
  (copy files into the mounted volume), not a protocol-level one.
- All interactive control work — battery, EQ, mode switching,
  firmware info, OTA, voice prompts, multipoint — happens over
  Bluetooth (see [protocols.md](protocols.md), [rcsp.md](rcsp.md)).

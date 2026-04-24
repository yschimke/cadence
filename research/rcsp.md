# RCSP — wire format and opcode catalogue

Extracted 2026-04-24 from Jieli's published SDKs. Two sources, cross-referenced:

- **iOS SDK** [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth)
  — `JL_BLEKit.framework/Headers/JL_OpCode.h` (top-level opcode `#define`s)
  and `JL_RCSP.h` (packet model class).
- **Firmware SDK** [`Jieli-Tech/fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK)
  — `include_lib/btstack/third_party/rcsp/JL_rcsp_packet.h` (wire layout
  struct + sync bytes), `JL_rcsp_protocol.h` (opcode enum + attribute IDs +
  send-API surface), `JL_rcsp_api.h` (auth API).

Where the two agree (opcode numbers; framing struct), it's a confirmed spec.
Where only one has the detail (sub-opcodes, attribute IDs), it's noted.

## Wire format

From `JL_rcsp_packet.h`:

```
+----+----+----+--------+--------+----------------------+----+
| FE | DC | BA | HEAD   | LEN    | DATA (LEN bytes)     | EF |
+----+----+----+--------+--------+----------------------+----+
  3 byte sync   2 byte   2 byte   variable                1 byte
                header   length                           end
```

- **Sync prefix:** `0xFE 0xDC 0xBA` (`JL_PACK_START_TAG{0,1,2}`).
- **Head (16 bits, packed):**
  ```
  bit 15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 0
       T  R  -  -  -  -  - -  ----OpCode---
  ```
  - bit 15 `_type` — 0 = command, 1 = response.
  - bit 14 `_resp` — request-for-response flag.
  - bits 13–8 `_unused`.
  - bits 7–0 `_OpCode` — see opcode table below.

  Source: `union __HEAD_BIT { struct { u16 _OpCode:8; u16 _unsed:6; u16 _resp:1; u16 _type:1; } _i; u16 _t; }`.
- **Length:** 2 bytes covering `DATA` only (`pkgLength` per `JL_RCSP.h` "参数长度(本身不计)" — "param length, not counting itself").
- **Data:** payload, layout depends on opcode (see "Sub-opcodes & attributes").
- **End tag:** `0xEF` (`JL_PACK_END_TAG`). Confirmed by `JL_ONE_PACKET_LEN(n) = sizeof(JL_PACKET) + n + 1` — the `+1` is the end byte.

**Endianness:** the firmware ships both big- and little-endian helpers
(`READ_BIG_U16` / `READ_LIT_U16`). `JL_rcsp_api.h` defines
`USE_ENDIAN_TYPE = USE_LITTLE_ENDIAN`. The HEAD bitfield is a packed
`u16` and on Jieli's ARM target maps to little-endian byte order, so
on the wire the head byte order is `[op_byte, flags_byte]`. Length and
multi-byte payload fields are little-endian by default but should be
verified per-opcode against a captured packet.

**No checksum field in the framing.** Integrity comes via:

1. Lower-layer reliability (RFCOMM is reliable / in-order).
2. Status codes carrying CRC errors (`JL_PRO_STATUS_CRC_ERR`,
   `JL_PRO_STATUS_ALL_DATA_CRC_ERR` — see "Status codes" below). These
   are surfaced by the device when it rejects an opcode, suggesting CRC
   *is* applied to certain payloads (likely OTA / file-transfer
   classes), but not to the generic CMD packet.

**MTU:** SDK defaults — `JL_MTU_RESV = 540` bytes (max receive),
`JL_MTU_SEND = 128` bytes (default send chunk). Negotiable via
opcode `0xD1` (`JL_OPCODE_NOTIFY_MTU` / `kJL_SET_MTU`).

## Top-level opcode catalogue

8-bit opcode in HEAD's low byte. Cross-referenced between iOS
(`kJL_*` macros) and firmware (`JL_OPCODE_*` enum). All values agree.

| Op  | iOS name (`kJL_…`)        | Firmware name (`JL_OPCODE_…`)             | Notes |
|-----|---------------------------|-------------------------------------------|-------|
| 01  | `DATA_CMD`                | `DATA`                                    | Generic data carrier with sub-opcode |
| 02  | `GET_TARGET_FEATURE_MAP`  | `GET_TARGET_FEATURE_MAP`                  | Marked "reserved" in iOS |
| 03  | `GET_TARGET_FEATURE`      | `GET_TARGET_FEATURE`                      | Returns `ATTR_TYPE_*` TLVs (table below) |
| 04  | `VOICE_START`             | (gap — was `SWITCH_DEVICE`, now removed)  | iOS-only; voice capture |
| 05  | `VOICE_STOP`              | —                                         | iOS-only |
| 06  | `VOICE_DISCONNECT_EDR`    | `DISCONNECT_EDR`                          | |
| 07  | `SYS_INFO_GET`            | `SYS_INFO_GET`                            | + sub-attribute, see `COMMON_INFO_ATTR_*` |
| 08  | `SYS_INFO_SET`            | `SYS_INFO_SET`                            | |
| 09  | `SYS_INFO_AUTO_UPDATE`    | `SYS_INFO_AUTO_UPDATE`                    | Device→host push |
| 0A  | `PHONE_CALL_REQUEST`      | `CALL_REQUEST`                            | |
| 0B  | `OTA_BLE_SPP`             | `SWITCH_DEVICE`                           | **Disagreement** — same op, different name. Worth a capture before assuming |
| 0C  | `FILE_BROWSE_START`       | `FILE_BROWSE_REQUEST_START`               | |
| 0D  | `FILE_BROWSE_STOP`        | `FILE_BROWSE_REQUEST_STOP`                | |
| 0E  | `FUNCTION_CMD`            | `FUNCTION_CMD`                            | + sub-opcode (per-class) |
| 0F  | `LRC_GET_START`           | —                                         | iOS-only; lyrics |
| 10  | `LRC_GET_STOP`            | —                                         | iOS-only |
| 11  | `A2F_TTS_START`           | —                                         | iOS-only; app→firmware TTS |
| 12  | `BT_SCAN_START`           | `SYS_OPEN_BT_SCAN`                        | TWS / Auracast pairing |
| 13  | `BT_SCAN_RESULT`          | `SYS_UPDATE_BT_STATUS`                    | |
| 14  | `BT_SCAN_STOP`            | `SYS_STOP_BT_SCAN`                        | |
| 15  | (`BT_CONNECT` commented)  | `SYS_BT_CONNECT_SPEC`                     | |
| 16  | `FILE_START`              | (see `FILE_TRANSFER_START` 0x1B)          | iOS file transfer |
| 17  | `FILE_STOP`               | (see `FILE_TRANSFER_END` 0x1C)            | |
| 19  | `FIND_DEVICE`             | `SYS_FIND_DEVICE`                         | |
| 1A  | `GET_FLASH_W_R`           | `EXTRA_FLASH_OPT`                         | |
| 1B  | `BIGFILE_START`           | `FILE_TRANSFER_START`                     | |
| 1C  | `BIGFILE_END`             | `FILE_TRANSFER_END`                       | |
| 1D  | `BIGFILE_GET_FILE`        | `FILE_TRANSFER`                           | |
| 1E  | `BIGFILE_STOP`            | `FILE_TRANSFER_CANCEL`                    | |
| 1F  | `FILE_DELETE`             | `FILE_DELETE`                             | |
| 20  | `GET_FILENAME`            | `FILE_RENAME`                             | **Disagreement** — investigate |
| 21  | `PRE_ENVIRONMENT`         | `ACTION_PREPARE`                          | |
| 22  | `FILE_FORMAT`             | `DEVICE_FORMAT`                           | Wipe device storage |
| 23  | `FILE_NAME_DEL`           | `ONE_FILE_DELETE`                         | |
| 24  | `FILE_READ_CONTENT`       | `ONE_FILE_TRANS_BACK`                     | |
| 25  | `RTC_PLUS`                | `ALARM_EXTRA`                             | |
| 26  | `BATCH_OPERATE`           | `FILE_BLUK_TRANSFER`                      | |
| 27  | `FILE_GET_CRC`            | `DEVICE_PARM_EXTRA`                       | |
| 28  | `SMALL_FILE`              | `SIMPLE_FILE_TRANS`                       | |
| 29  | `DEVICE_LOG`              | —                                         | iOS-only |
| 30  | `BIG_DATA`                | —                                         | iOS-only |
| 31  | `TWS_NAME_LIST`           | —                                         | "已连接手机名" — connected-phone names |
| 32  | `AI_CONTROL`              | —                                         | iOS-only |
| 33  | `PUBLIC_SET`              | —                                         | iOS-only |
| 34  | `TRAMSLATE` *(sic)*       | —                                         | iOS-only |
| 35  | `AURACASTE` *(sic)*       | —                                         | Auracast |
| A0–A6 | `WEAR_*`                | `SPORTS_DATA_INFO_*`                      | Watch/wearable; not on OpenSwim Pro |
| C0–C4 | `SET/GET_ADV`, `ADV_NOTIFY_*` | —                                   | Wear advertisement subset |
| D0  | `SET_APP_INFO`            | —                                         | |
| D1  | `SET_MTU`                 | `NOTIFY_MTU`                              | MTU negotiation |
| D3  | —                         | `SYS_EMITTER_BT_CONNECT_STATUS`           | "被遗忘的指令" — "the forgotten command" |
| D4  | `GET_MD5`                 | `GET_MD5`                                 | |
| D5  | `GET_LOW_DELAY`           | `LOW_LATENCY_PARAM`                       | |
| D6  | `GET_FLASH_INFO`          | `EXTRA_FLASH_INFO`                        | |
| D8  | `SET_DEV_STORAGE`         | —                                         | "设置当前使用的存储设备" — picks USB MS vs internal |
| D9  | `GET_DEV_CONFIG`          | —                                         | |
| E1–E8 | `OTA_*`                 | —                                         | OTA upgrade flow |
| F1  | `PHONE_NUMBER_WAY`        | —                                         | |
| F2  | `FILE_STRUCT_CHANGE`      | —                                         | Notify host that file tree changed |
| FF  | `CUSTOMER_USER`           | `CUSTOMER_USER`                           | OEM-defined opcode space |

**Reading this for OpenSwim Pro:** The library cares mostly about `0x07/0x08/0x09`
(SYS_INFO get/set/notify — battery, EQ, volume), `0x0E` (FUNCTION_CMD —
mode switching, voice prompts), `0xE1–0xE8` (OTA), and the `0x1B–0x28`
file-transfer family (which is **not** how files get to the OpenSwim Pro
because USB UMS handles that — but worth knowing the surface exists).
The `0xA*` wearable space and `0x3X` AI/translate/Auracast space are
out-of-scope.

## SYS_INFO sub-attribute IDs (opcodes 0x07/0x08/0x09)

From `JL_rcsp_protocol.h`. After the SYS_INFO opcode, the data payload
starts with one of these sub-attribute bytes, followed by an
attribute-specific value or TLV.

| ID | Name                      | Maps to user-visible feature |
|----|---------------------------|------------------------------|
| 0  | `BATTERY`                 | **Battery percentage** — primary source for the library |
| 1  | `VOL`                     | Master volume |
| 2  | `DEV`                     | Device state / power |
| 3  | `ERR_REPORT`              | Error reporting |
| 4  | `EQ`                      | EQ preset / band values |
| 5  | `FILE_BROWSE_TYPE`        | |
| 6  | `FUN_TYPE`                | Active "function" (BT / MP3 / line-in / etc.) — **likely the mode-switch attribute** |
| 7  | `LIGHT`                   | RGB / status LED |
| 8  | `FMTX`                    | FM transmitter (n/a OpenSwim Pro) |
| 11 | `HIGH_LOW_SET`            | |
| 12 | `PRE_FETCH_ALL_EQ_INFO`   | Bulk EQ snapshot |
| 13 | `ANC_VOICE`               | ANC (n/a OpenSwim Pro — bone conduction) |
| 14 | `FETCH_ALL_ANC_VOICE`     | |
| 15 | `PHONE_SCO_STATE_INFO`    | HFP SCO state mirror |

The "MP3 mode vs BT mode" switch on the OpenSwim Pro is almost certainly
**`SYS_INFO_SET` with attribute `FUN_TYPE`** — verify by sniffing the
Shokz app's mode-switch action (next-steps step 3).

## GET_TARGET_FEATURE attribute IDs (opcode 0x03)

Used to enumerate device capabilities at session start.

| ID | Name                  |
|----|-----------------------|
| 0  | `PROTOCOL_VERSION`    |
| 1  | `SYS_INFO`            |
| 2  | `EDR_ADDR`            |
| 3  | `PLATFORM`            |
| 4  | `FUNCTION_INFO`       |
| 5  | `DEV_VERSION`         |
| 6  | `SDK_TYPE`            |
| 7  | `UBOOT_VERSION`       |
| 8  | `DOUBLE_PARITION`     |
| 9  | `UPDATE_STATUS`       |
| 10 | `DEV_VID_PID`         |
| 11 | `DEV_AUTHKEY`         |
| 12 | `DEV_PROCODE`         |
| 13 | `DEV_MAX_MTU`         |
| 17 | `BLE_ADDR`            |
| 19 | `MD5_GAME_SUPPORT`    |

Library should issue `GET_TARGET_FEATURE` for `PROTOCOL_VERSION`,
`SYS_INFO`, `FUNCTION_INFO`, `DEV_VERSION`, `DEV_MAX_MTU` on connect
to discover the session shape before issuing any feature opcodes.

## BT / Music / RTC info attribute IDs

Used by sub-classes — likely nested inside `FUNCTION_CMD` (0x0E) per
`JL_CLASS` family. To be confirmed by reading `rcsp_bluetooth.c`.

- `BT_INFO_ATTR_*`: 0=MUSIC_TITLE, 1=ARTIST, 2=ALBUM, 3=NUMBER,
  4=TOTAL, 5=GENRE, 6=TIME, 7=STATE, 8=CURR_TIME — AVRCP-equivalent.
- `MUSIC_INFO_ATTR_*`: 0=STATUS, 1=FILE_NAME, 2=FILE_PLAY_MODE — for
  on-device MP3 playback (relevant: this is exactly what the
  OpenSwim Pro does in MP3 mode).
- `RTC_INFO_ATTR_*`: 0=RTC_TIME, 1=RTC_ALARM.
- `LINEIN_INFO_ATTR_*`: 0=STATUS — n/a OpenSwim Pro (no line-in).

## Status codes (response packets)

Cross-confirmed iOS `JL_CMDStatus` / firmware `JL_PRO_STATUS`:

| Val  | Meaning                |
|------|------------------------|
| 0x00 | Success                |
| 0x01 | Fail                   |
| 0x02 | Unknown / undefined cmd|
| 0x03 | Busy                   |
| 0x04 | No response            |
| 0x05 | CRC error              |
| 0x06 | Data CRC error         |
| 0x07 | Param error            |
| 0x08 | Data over limit        |
| 0x09 | LRC fetch error (iOS-only) |

Response packets carry `(status, sn, data)` in the data area
(`JL_CMD_response_send(OpCode, status, sn, data, len)`).
The `sn` is an 8-bit sequence number assigned by the sender of the
original command — see `mCmdSN` on `JL_FunctionBaseManager`.

## Send API surface (firmware-side)

For sanity-check on what the library's encoder needs to produce:

```c
JL_ERR JL_CMD_send(u8 OpCode, u8 *data, u16 len, u8 request_rsp);
JL_ERR JL_CMD_response_send(u8 OpCode, u8 status, u8 sn, u8 *data, u16 len);
JL_ERR JL_DATA_send(u8 OpCode, u8 CMD_OpCode, u8 *data, u16 len, u8 request_rsp);
JL_ERR JL_DATA_response_send(u8 OpCode, u8 status, u8 sn, u8 CMD_OpCode, u8 *data, u16 len);
```

So `DATA` packets (op `0x01`) carry an inner `CMD_OpCode` byte at the
front of `data`. `FUNCTION_CMD` (op `0x0E`) very likely follows the
same convention. Both still need a sniff or a deeper read of
`rcsp_bluetooth.c` to lock down their inner shape.

## Authentication / pairing

Header: `JL_rcsp_api.h`.

```c
void JL_rcsp_auth_init(int (*send)(void *, u8 *, u16),
                       u8 *link_key, u8 *addr);
void JL_rcsp_auth_reset(void);
u8   JL_rcsp_get_auth_flag(void);
void JL_rcsp_set_auth_flag(u8 auth_flag);
void JL_rcsp_auth_recieve(u8 *buffer, u16 len);
void smart_auth_res_pass(void);   // device-side bypass entry
```

Key facts:

- The auth function takes the **BR/EDR link key + BD_ADDR** as input.
  This means auth is *derived from the standard pairing key*, not from
  a separate Shokz secret. If the Kotlin host can read its own link
  key for the device (BlueZ stores them under
  `/var/lib/bluetooth/<adapter>/<bdaddr>/info`, root-only on Linux;
  Android's BluetoothDevice does not expose link key directly), the
  handshake can be reproduced without RE-ing the closed-source
  `JL_HashPair.xcframework`.
- `JL_AUTH_PASS = 1`, `JL_AUTH_NOTPASS = 0`. Devices ship with auth
  required (`JL_auth_pass` defaults to NOTPASS at protocol init —
  `set_auth_pass()` is the toggle). But `smart_auth_res_pass()` is a
  device-side function suggesting auth can be bypassed by the firmware
  itself for some session types — worth a sniff to see whether the
  OpenSwim Pro requires the handshake or accepts unauthenticated
  commands once paired.
- The exact handshake bytes are not in this header — they live inside
  the auth library `liba/rcsp_stack.a` (firmware) or the iOS
  `JL_HashPair.xcframework`. A pcap of the Shokz app pairing
  (next-steps step 3) is the cheapest way to learn the format.

## What's still unknown

1. **`FUNCTION_CMD` (0x0E) sub-opcode space.** Need to read
   `apps/common/third_party_profile/jieli/JL_rcsp/rcsp_bluetooth.c`
   (~now in the AC63 sparse clone but not yet checked out here) and
   the `JL_FunctionBaseManager` subclasses in the iOS framework
   binary.
2. **`DATA` (0x01) sub-opcode space.** Same as above. Likely overlaps
   with `JL_CLASS` enum (SmallFile, OTA, AlarmClock, etc.).
3. **CRC algorithm.** Status codes prove one exists; need to find
   where `crc_check_*` is computed in `rcsp_bluetooth.c`.
4. **Auth handshake byte sequence.** Best learned from a pcap.
5. **Per-attribute payload schemas** — most likely TLV with 1-byte
   length, but `rcspInfoFromData2ByteSize:` in `JL_RCSP.h` also
   supports a 2-byte-L variant. Per-opcode confirmation needed.
6. **Endianness for multi-byte payload fields.** API defaults to
   little-endian but several `WRITE_BIG_*` macros suggest the
   protocol switches to big-endian for some opcodes (likely the OTA
   address fields). Confirm per-opcode at sniff time.

## Library design implication

We now have enough to design the `Transport` and `Codec` layers
without committing to a specific feature opcode set:

```
RcspCodec                 // pack/parse JL_PACKET ↔ ByteArray
RcspSession              // owns RFCOMM socket, sequence numbers, auth state
└── on connect, runs auth init (or skip if device permits) and
    GET_TARGET_FEATURE for PROTOCOL_VERSION / SYS_INFO / DEV_MAX_MTU
RcspCommand sealed hierarchy:
    SysInfoGet(attr: SysInfoAttr)
    SysInfoSet(attr, payload)
    GetTargetFeature(attr: TargetAttr)
    FunctionCmd(sub: FunctionSub, payload)   // sub TBD
    Data(sub: DataSub, payload)              // sub TBD
    Ota* (later)
RcspNotification          // device-pushed SYS_INFO_AUTO_UPDATE etc.
```

Battery, EQ, volume, mode-switch all flow through `SysInfoGet` /
`SysInfoSet` / `SysInfoAutoUpdate`. That's enough to cover the
"first useful feature" cut without needing the function-cmd
sub-opcode catalogue first.

**Still: don't write this code yet** until next-steps step 2 (Shokz
APK decompile) confirms which opcodes the OpenSwim Pro actually
implements vs. the wider Jieli surface, and step 3/4 nail down auth
requirements.

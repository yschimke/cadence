# RCSP — wire format and opcode catalogue (public-source notes)

Notes compiled from material the chip vendor publishes openly on
GitHub. **No private SDKs, leaked binaries, or third-party-app code are
referenced here.** Two upstream sources, both public:

- **iOS SDK** [`Jieli-Tech/iOS-JL_Bluetooth`](https://github.com/Jieli-Tech/iOS-JL_Bluetooth)
  — `JL_BLEKit.framework/Headers/JL_OpCode.h` and `JL_RCSP.h`.
- **Firmware SDK** [`Jieli-Tech/fw-AC63_BT_SDK`](https://github.com/Jieli-Tech/fw-AC63_BT_SDK)
  — `JL_rcsp_packet.h`, `JL_rcsp_protocol.h`, `JL_rcsp_api.h`.

Both repositories are MIT-licensed and published by the chip vendor for
third-party integration. The catalogue below is a reading aid for those
headers, not a re-publication of any closed-source artefact.

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
- **Head (16 bits, packed):** bit 15 type (0=cmd, 1=resp), bit 14 resp-
  required, bits 13–8 unused, bits 7–0 opcode.
- **Length:** 2 bytes covering `DATA` only.
- **End tag:** `0xEF` (`JL_PACK_END_TAG`).

Endianness: SDK defines `USE_ENDIAN_TYPE = USE_LITTLE_ENDIAN`; head
bitfield serialises as `[op_byte, flags_byte]`. Multi-byte payload
fields are little-endian by default but vary per opcode (the SDK ships
both `READ_BIG_*` and `READ_LIT_*` helpers).

MTU: SDK defaults `JL_MTU_RESV = 540`, `JL_MTU_SEND = 128`, negotiable
via `NOTIFY_MTU` (op `0xD1`).

## Top-level opcode catalogue

8-bit opcode in HEAD's low byte. Names from the public headers, kept as
a short reading aid.

| Op  | Name                                  | Notes |
|-----|---------------------------------------|-------|
| 01  | `DATA` / `DATA_CMD`                   | Generic carrier; sub-opcode in payload |
| 02  | `GET_TARGET_FEATURE_MAP`              | Reserved per iOS header |
| 03  | `GET_TARGET_FEATURE`                  | Returns `ATTR_TYPE_*` TLVs |
| 06  | `DISCONNECT_EDR`                      | |
| 07  | `SYS_INFO_GET`                        | Sub-attribute follows, see below |
| 08  | `SYS_INFO_SET`                        | |
| 09  | `SYS_INFO_AUTO_UPDATE`                | Device→host push |
| 0A  | `CALL_REQUEST`                        | |
| 0E  | `FUNCTION_CMD`                        | Sub-opcode (per-class) |
| 12  | `BT_SCAN_START` / `SYS_OPEN_BT_SCAN`  | |
| 19  | `FIND_DEVICE` / `SYS_FIND_DEVICE`     | |
| 1B  | `FILE_TRANSFER_START`                 | |
| 1C  | `FILE_TRANSFER_END`                   | |
| 1D  | `FILE_TRANSFER`                       | |
| 1E  | `FILE_TRANSFER_CANCEL`                | |
| 1F  | `FILE_DELETE`                         | |
| 22  | `DEVICE_FORMAT`                       | Wipe device storage |
| D1  | `NOTIFY_MTU`                          | MTU negotiation |
| D4  | `GET_MD5`                             | |
| E1–E8 | `OTA_*`                             | OTA upgrade flow |
| FF  | `CUSTOMER_USER`                       | OEM-defined opcode space |

(See `JL_OpCode.h` / `JL_rcsp_protocol.h` for the full enum — values
above are the ones an interop client is most likely to need.)

## SYS_INFO sub-attribute IDs (opcodes 0x07/0x08/0x09)

From `JL_rcsp_protocol.h`. After the SYS_INFO opcode, the payload starts
with one of these sub-attribute bytes followed by an attribute-specific
value or TLV.

| ID | Name                      | User-visible feature |
|----|---------------------------|----------------------|
| 0  | `BATTERY`                 | Battery percentage |
| 1  | `VOL`                     | Master volume |
| 2  | `DEV`                     | Device state / power |
| 4  | `EQ`                      | EQ preset / band values |
| 6  | `FUN_TYPE`                | Active function (BT / MP3 / line-in / etc.) |
| 7  | `LIGHT`                   | RGB / status LED |
| 12 | `PRE_FETCH_ALL_EQ_INFO`   | Bulk EQ snapshot |
| 13 | `ANC_VOICE`               | ANC (n/a on bone-conduction models) |

## GET_TARGET_FEATURE attribute IDs (opcode 0x03)

| ID | Name                  |
|----|-----------------------|
| 0  | `PROTOCOL_VERSION`    |
| 1  | `SYS_INFO`            |
| 3  | `PLATFORM`            |
| 4  | `FUNCTION_INFO`       |
| 5  | `DEV_VERSION`         |
| 13 | `DEV_MAX_MTU`         |

A client should issue `GET_TARGET_FEATURE` for `PROTOCOL_VERSION`,
`SYS_INFO`, `FUNCTION_INFO`, `DEV_VERSION`, and `DEV_MAX_MTU` on
connect, to discover the session shape before issuing any feature
opcodes.

## Status codes

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

Response packets carry `(status, sn, data)` in the data area. The `sn`
is an 8-bit sequence number assigned by the sender of the original
command.

## Authentication

`JL_rcsp_api.h` exposes:

```c
void JL_rcsp_auth_init(int (*send)(void *, u8 *, u16),
                       u8 *link_key, u8 *addr);
u8   JL_rcsp_get_auth_flag(void);
void JL_rcsp_set_auth_flag(u8 auth_flag);
```

The auth function takes the BR/EDR link key + BD_ADDR as inputs, so the
handshake is *derived from the standard pairing key* rather than from a
separate vendor secret. Devices typically ship with auth required
(`JL_AUTH_NOTPASS` at protocol init); some firmwares are permissive
when the peer never invokes the handshake at all.

## Library design implication

For a pure-interop Kotlin client over RFCOMM, three layers cover the
public-header surface above:

```
RcspCodec       // pack/parse JL_PACKET ↔ ByteArray
RcspSession     // owns RFCOMM socket, sequence numbers, auth state
RcspCommand     // SysInfoGet / SysInfoSet / GetTargetFeature / Ota*
```

Battery, EQ, and volume all flow through `SysInfoGet` / `SysInfoSet` /
`SysInfoAutoUpdate`. That is enough for a first cut without needing the
`FUNCTION_CMD` sub-opcode space.

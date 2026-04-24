# Next steps

Ordered by information-per-hour, not by eventual library topology.

## 1. Extract the RCSP opcode catalogue from Jieli's iOS SDK ✅ (2026-04-24)

Done. See [rcsp.md](rcsp.md). Captured: wire framing (sync `FE DC BA`,
8-bit op + 16-bit head flags + 16-bit length + payload + `EF` end), the
top-level opcode table cross-referenced between iOS `kJL_*` and firmware
`JL_OPCODE_*`, the `SYS_INFO` / `GET_TARGET_FEATURE` sub-attribute IDs,
the status-code enum, and the auth-API surface (BR/EDR link key + BD_ADDR
based — potentially reproducible without RE-ing `JL_HashPair`).

**Gaps left** (cheap → expensive):

- `FUNCTION_CMD` (op `0x0E`) and `DATA` (op `0x01`) sub-opcode tables —
  read `apps/common/third_party_profile/jieli/JL_rcsp/rcsp_bluetooth.c`
  in the AC63 SDK and the `JL_FunctionBaseManager` subclasses in the
  iOS framework. Already sparse-clone of the AC63 SDK in `/tmp/fw-AC63`.
- CRC algorithm — same source.
- Per-opcode multi-byte endianness — confirm per-opcode at sniff time.
- Auth handshake byte sequence — best learned from a pcap (step 3).

## 2. Decompile the Shokz Android app to confirm which opcodes Shokz actually
   uses vs. the full Jieli surface

- Pull `cn.com.aftershokz.app` APK from a mirror; use `jadx` to decompile.
- Look for Shokz's own wrapper around the Jieli Android SDK — that wrapper is
  the "supported subset" list for OpenSwim Pro specifically.
- Note any Shokz-specific OEM extensions (likely includes MP3-mode switch,
  voice-prompt language, possibly the underwater audio profile selector).

This answers "does the app support switching to MP3 mode over BT?" and
"which opcodes should the library support day one?"

## 3. Live RFCOMM sniff of the Shokz app pairing (optional, only if step 2 is ambiguous)

- Enable "Bluetooth HCI snoop log" in Android developer options.
- Pair the OpenSwim Pro fresh from the phone, open the Shokz app, exercise
  every UI control.
- Pull `/sdcard/btsnoop_hci.log` (or via `adb bugreport`).
- Analyse in Wireshark — the RCSP frames on RFCOMM ch 10 will show exactly
  what the app sends and what the device returns, including any
  hash-pair handshake.

Needed only if step 1 leaves gaps. Not a substitute for step 1 because
sniffing doesn't produce a name for each opcode — just bytes.

## 4. Nail down the hash-pair handshake

The Jieli SDK ships `JL_HashPair.xcframework` — not open source. If step 3's
pcap shows an authenticated exchange on RFCOMM ch 10 before normal commands
start, we need to implement the same handshake in Kotlin. Options (in order
of preference):

- Confirm the handshake is equivalent across all Shokz models → generic
  implementation with no per-model secrets.
- If not: extract the algorithm from the iOS xcframework (Ghidra) or from
  the Android SDK's Java/Kotlin side (easier).
- If the device accepts commands without handshake (some Jieli firmwares
  are permissive when `JL_HashPair` is not invoked by the peer), skip this
  entirely.

## 5. Sketch the library's public API

Only after steps 1 & 2 — don't design the API before we know the opcodes.
Proposed top-level shape:

```
ShokzConnector (common)
├── Discovery (BT)
├── DeviceHandle
│   ├── info: DeviceInfo     // model, firmware, serial (from RCSP, not USB)
│   ├── battery: Flow<Battery>
│   ├── eq: EqPreset / customEq
│   ├── voicePromptLanguage
│   ├── autoPowerOff
│   ├── switchToMp3Mode()    // if APK confirms it
│   └── ota(...)             // later
└── Storage (USB)
    ├── mountedVolume(): Path?
    ├── listTracks()
    ├── addTrack(source: Path)
    └── removeTrack(...)
```

## Not doing

- **BLE / GATT investigation.** Ruled out — device is BR/EDR-only.
- **USB vendor-control path.** Ruled out — device is pure UMS.
- **`btsnoop` on the Linux host while the Shokz app runs from a phone.** The
  Shokz app and the device pair with each other; the laptop's adapter is
  outside that connection and can only eavesdrop with a specialised
  sniffer (e.g. Ellisys, or nRF52 in sniffer mode). Not worth it when we
  can just use Android's built-in snoop log on the phone (step 3).
- **Supporting other Shokz models.** Scope-creep until OpenSwim Pro is
  solid. The library's `Transport` abstraction should make it trivial to
  add OpenRun etc. later — each model is a subset of the same RCSP
  catalogue plus/minus physical features.

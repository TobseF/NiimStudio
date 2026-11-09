# Printer protocol notes

Technical background for developers: how NiimStudio talks to the printer, and what is known
about the official app. Users don't need any of this.

## Sources

The protocol implementation follows [niimbluelib](https://github.com/MultiMote/niimbluelib)
(MIT), the reverse-engineered NIIMBOT protocol. From the official Android app (NIIMBOT 6.6.9)
only names, constants and the Bluetooth transport were taken, as described below. No code of
the official app is used.

## What the official app (NIIMBOT 6.6.9) does

- **UI:** Flutter (`libapp.so`). Its Dart packages include `niimbot_connector`,
  `niimbot_print_setting_plugin` and `niimbot_flutter_canvas`.
- **Printer code:** a Java SDK, `com.gengcon.www.jcprintersdk`, reached over the
  `com.niimbot.printer/Method/Channel` platform channel.
- **Packing:** the code is packed with DexHelper/SecNeo. `classes.dex` is a 21 KB stub, and the
  real DEX files sit inside `lib/armeabi-v7a/libdatajar.so`. Their header and index tables are
  encrypted, but the data section is plaintext. That leaves class names, method names and
  constants readable, though the code can't be decompiled.
- **B18 classes:** `printer/b18/B18V3PrintTask`, `printer/b18/B18NewPrintTask`,
  `B18PrinterInfoGetter` / `B18PrinterInfoSetter`, `factory/printerfactory/B18Factory` and the
  `B18_PRINTER` constant.
- **Transport:** classic Bluetooth SPP via `createRfcommSocketToServiceRecord` /
  `createInsecureRfcommSocketToServiceRecord` with UUID `00001101-0000-1000-8000-00805F9B34FB`
  (`NiimbotSppPacket`, `BluetoothConnHelper`).
- **Protocol:** the SDK constants (`START_PRINT`, `START_PAGE_PRINT`, `END_PAGE_PRINT`,
  `END_PRINT`, `QUERY_PRINTER_STATUS`, `PRINT_DENSITY`, `SET_LABEL_TYPE_*`, …) match the
  publicly documented protocol of niimbluelib.

## Frame format

`55 55 | cmd | len | data | xor(cmd, len, data) | AA AA`. The Connect frame is prefixed with `03`.

## Print sequence (default "V3" variant)

| Step | TX | Expected RX |
|---|---|---|
| Connect | `C1 [01]` | `C2` |
| Model id | `40 [08]` | `48` → `0E 00` = 3584 = B18 |
| Density | `21 [d]` | `31 [01]` |
| Label type | `23 [t]` | `33 [01]` |
| Print start | `01 [pages u16, 0,0,0,0, 0]` | `02 [01]` |
| Page start | `03 [01]` | `04 [01]` |
| Page size | `13 [rows u16, cols u16, copies u16]` | `14 [01]` |
| Rows | `84` empty / `83` indexed / `85` bitmap; no reply | – |
| Page end | `E3 [01]` | `E4 [01]` |
| Poll status | `A3 [01]` | `B3 [page u16, print %, feed %, …]` until page == copies |
| Print end | `F3 [01]` | `F4 [01]` |

The B18 print head has **96 dots** (12 mm at 203 dpi). Each row is 12 bytes, MSB first. The
3-byte "black pixel count" field holds per-32-dot chunk counts.

The SDK's B18 tasks can't be decoded byte for byte, so the app offers three variants under
**Settings → Protocol variant**:

- **V3 / B1-style:** the default.
- **Legacy / D110-style:** 1-byte start, PrintClear, 4-byte size, PrintQuantity.
- **V4:** 9-byte start, 13-byte size.

"Auto" picks V4 when the printer reports protocol v4+, otherwise V3.

## Troubleshooting a new printer

1. Pair the printer in the Android Bluetooth settings (optional; SPP connect also prompts).
2. In NiimStudio open **Settings**, tap **Search**, pick the printer, keep **Classic (SPP)**,
   tap **Connect**. The info line should show the model, e.g. `model=B18 (3584)`.
3. Tap **Test pattern**: a label with a border, a black square in the top-left corner and
   mm ticks.
4. If nothing prints or the printer refuses, try another **Protocol variant**, then BLE as
   **Connection type**. If the output is mirrored or rotated, change **Rotation**.
5. **Share log** sends the full TX/RX hex log – please attach it to an issue.

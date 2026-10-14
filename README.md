# B18 Print

Etiketten-App für den **NIIMBOT B18**. Bedienung und Design folgen der Original-App (NIIMBOT 6.6.9).

## Funktionen

- **Editor:** Elemente per Drag & Drop verschieben. Anfasser: rechts für die Breite, unten für die Höhe, Ecke zum Skalieren (bei Text mit Schriftgröße). Pinch-Zoom, Doppeltippen zum Bearbeiten, magnetische Hilfslinien (Mitte, Ränder, andere Elemente), Rückgängig/Wiederholen.
- **Schnell-Leiste über der Auswahl:** Bearbeiten, Löschen, Duplizieren, 90° drehen, Ausrichten, Sperren.
- **Elemente:** Text, Bild (mit Dithering, Schwelle, Invertieren), Symbol (über 200 Vektorsymbole, Suche, Favoriten), Datum/Uhrzeit (Format, Tage-Versatz, z. B. MHD), Barcode (Code 128, EAN, UPC, Code 39/93, ITF, Codabar), QR-Code / Data Matrix / Aztec / PDF417 (mit WLAN-Assistent), Formen und Linien, Seriennummer.
- **Reiter je Element:**
  - Inhalt
  - Form: fett, unterstrichen, durchgestrichen, kursiv, invertiert, Größe, Ausrichtung, Unterschneidung, Zeilenabstand, Zeilenumbruch
  - Schriften: Systemschriften, eigene TTF/OTF hochladen, Zuletzt verwendet, Favoriten
  - Ausrichten/Spiegeln: 6 Ausrichtungen zum Etikett, Steuerkreuz mit Schrittweite, X/Y/Breite/Höhe/Drehung numerisch, Ebenen, Spiegelung, Sperren
- **Etikettenprofile:** frei editierbar (Bandbreite, Länge, Typ, Lücke, Eckenradius, Druckversatz, Dichte).
- **Vorlagen:** Layout speichern, öffnen, umbenennen, duplizieren, löschen, jeweils mit Vorschaubild.
- **Datenquelle:** Liste/CSV (Platzhalter {A}, {B}, …) oder Nummerierung ({N}). Jede Zeile ergibt ein Etikett.
- **Druck:** 1-Bit-Vorschau genau wie gedruckt, Kopien, Dichte, Fortschritt, Abbrechen.
- **Einstellungen:** Drucker suchen und wählen, SPP/BLE, automatisch verbinden, Heartbeat, Protokollvariante, Drehung, Druckkopfbreite, Paketpause, Hilfslinien, TX/RX-Log mit Teilen, Testmuster.

Code: `model/` (Datenmodell), `data/Store` (Persistenz), `render/` (gemeinsamer Renderer für Editor und Druck), `printer/PrinterManager` (Verbindung und Druck), `protocol/` + `transport/` (NIIMBOT-Protokoll), `ui/` (Jetpack Compose).

## Sprachen

Die App ist auf Englisch (Standard) und Deutsch verfügbar. Beim ersten Start folgt sie der Handysprache und nimmt Englisch, wenn es dafür keine Übersetzung gibt. Unter **Einstellungen → Sprache** lässt sie sich umstellen. Ab Android 13 geht das auch über die Pro-App-Sprache in den Systemeinstellungen.

**Neue Sprache hinzufügen** (Beispiel Französisch):

1. `app/src/main/res/values/strings.xml` nach `app/src/main/res/values-fr/strings.xml` kopieren.
2. Alle Texte übersetzen. Platzhalter wie `%1$s` und `%1$d` sowie `{N}` bleiben erhalten. Bei Symbolnamen (`sym_…`) steht nach `|` eine Liste von Suchbegriffen.
3. Die Unit-Tests ausführen: `gradlew testDebugUnitTest`.

Mehr ist nicht nötig. Der Build erkennt den neuen Ordner und nimmt die Sprache in die Auswahl auf (`BuildConfig.LANGUAGES`, `L10n.available`). `TranslationsTest` meldet fehlende Schlüssel, abweichende Platzhalter, fehlende Plural- oder Array-Einträge sowie unbekannte Schlüssel.

Code: `L10n.kt` regelt Sprachwahl und Kontext. Die Drucker- und Protokollschicht ist sprachneutral (Statuscodes); übersetzt wird erst in der UI (`ui/Texts.kt`).

## What the official app (NIIMBOT 6.6.9) does

- **UI:** Flutter (`libapp.so`). Its Dart packages include `niimbot_connector`, `niimbot_print_setting_plugin` and `niimbot_flutter_canvas`.
- **Printer code:** a Java SDK, `com.gengcon.www.jcprintersdk`, reached over the `com.niimbot.printer/Method/Channel` platform channel.
- **Packing:** the code is packed with DexHelper/SecNeo. `classes.dex` is a 21 KB stub, and the real DEX files sit inside `lib/armeabi-v7a/libdatajar.so`. Their header and index tables are encrypted, but the data section is plaintext. That leaves the class names, method names and constants readable, though the code can't be fully decompiled.
- **B18 classes:**
  - `printer/b18/B18V3PrintTask`
  - `printer/b18/B18NewPrintTask`
  - `B18PrinterInfoGetter` / `B18PrinterInfoSetter`
  - `factory/printerfactory/B18Factory`
  - the `B18_PRINTER` constant
- **Transport:** classic Bluetooth SPP. It uses `createRfcommSocketToServiceRecord` / `createInsecureRfcommSocketToServiceRecord` with UUID `00001101-0000-1000-8000-00805F9B34FB` (`NiimbotSppPacket`, `BluetoothConnHelper`).
- **Protocol:** the SDK constants (`START_PRINT`, `START_PAGE_PRINT`, `END_PAGE_PRINT`, `END_PRINT`, `QUERY_PRINTER_STATUS`, `PRINT_DENSITY`, `SET_LABEL_TYPE_*`, `NO_RIBBON`, `SET_PRINT_MODE_HEAT_TRANSFER`, …) match the publicly documented NIIMBOT protocol, as reverse-engineered by [niimbluelib](https://github.com/MultiMote/niimbluelib).

### Frame format

`55 55 | cmd | len | data | xor(cmd,len,data) | AA AA`. The Connect frame is prefixed with `03`.

### Print sequence (default "V3" variant)

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

The B18 print head is **96 dots** (12 mm at 203 dpi). Each row is 12 bytes, MSB first. The 3-byte "black pixel count" field holds per-32-dot chunk counts.

The SDK's B18 tasks can't be decoded byte for byte, so the app offers three variants:
- **V3 / B1-style:** the default.
- **Legacy / D110-style:** 1-byte start, PrintClear, 4-byte size, PrintQuantity.
- **V4:** 9-byte start, 13-byte size.

"Auto" picks V4 when the printer reports protocol v4+, otherwise V3.

## Build

```
gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test checklist

1. Pair the B18 in Android Bluetooth settings (optional; SPP connect will also prompt).
2. Open the app, grant the Bluetooth permissions, tap **Scan**, pick `B18-…`, keep **Classic SPP**, tap **Connect**.
3. The info line should show `model=B18 (3584)`. If it doesn't, share the log.
4. Tap **Print test pattern**: a 12×40 mm label with a border, a black square in the top-left corner and mm ticks.
5. If nothing prints or the printer refuses, try the other **Protocol variants**, then the **BLE** transport.
6. If the output is mirrored or rotated, change **Rotation**.
7. Use **Share log** to send the full TX/RX hex log.

# PYTE für Windows

Die Desktop-Version von PYTE ist ein eigenes Windows-Programm. Sie öffnet die Oberfläche in einem
eigenen Fenster und spielt auch weiter, wenn das Fenster minimiert ist oder eine andere App im
Vordergrund steht. Browser-Tabs können Musik im Hintergrund dagegen drosseln oder pausieren.

## Herunterladen und starten

1. `PYTE-vX.Y.Z-windows.zip` von der [Release-Seite](https://github.com/PYTEBLE/PYTE/releases) laden.
2. ZIP vollständig entpacken (nicht direkt aus dem ZIP starten).
3. `PYTE.exe` im Ordner `PYTE` starten.

Die Version ist **nicht digital signiert**. Windows SmartScreen zeigt beim ersten Start deshalb
eventuell eine Warnung: „Weitere Informationen“ → „Trotzdem ausführen“.

Ein Installer ist noch nicht vorhanden; das Programm ist portabel. Es benötigt kein Node.js.

## Hintergrundwiedergabe

- Die Wiedergabe läuft weiter, wenn das Fenster minimiert ist oder andere Programme im Vordergrund sind.
- Während Musik spielt, verhindert PYTE, dass Windows die App anhält.
- Wenn du das Fenster schließt, beendet sich PYTE. Musik spielt dann nicht mehr weiter.
- Die Medientasten der Tastatur funktionieren, sobald PYTE läuft und Musik abspielt.

Nicht garantiert: Ein Ruhezustand oder Standby des Computers kann die Wiedergabe trotzdem stoppen.
Bitte im Windows-Energiesparplan prüfen, falls das bei dir passiert. Ich habe das noch nicht auf
echten Windows-Geräten geprüft.

## Bibliothek und Daten

- Playlists, Favoriten, Verlauf und Einstellungen werden lokal im Browserspeicher der App gespeichert.
- PYTE nutzt immer den Port `47380`. Nur so bleiben deine Daten erhalten.
- Ist dieser Port belegt, startet PYTE nicht. Dann bitte eine laufende PYTE-Instanz schließen.
- Der lokale Dienst lauscht nur auf `127.0.0.1` und ist nicht im Netzwerk erreichbar.
- Exportiere regelmäßig eine Sicherung unter **Einstellungen → Web-Sicherung exportieren**.
  Bei einer Neuinstallation oder Deinstallation mit Datenlöschung geht die Bibliothek sonst verloren.

## Was (noch) nicht enthalten ist

Offline-Downloads, Equalizer, „Stille überspringen“ und synchronisierte Lyrics fehlen in der
Windows-Version. Es gibt keine funktionslosen Schalter dafür.

Musik wird gestreamt und braucht eine Internetverbindung. YouTube-Schnittstellen sind inoffiziell;
Verfügbarkeit, Region und Altersbeschränkungen können die Wiedergabe verhindern.

Nur für persönlichen Gebrauch. Stream-Extraktion widerspricht den YouTube-Nutzungsbedingungen.

## Entwicklung

```sh
npm ci
npm run build         # Oberfläche bauen (dist/)
npm test              # Unit- und Server-Tests
npx playwright install chromium
npm run test:browser  # Browser-Tests (PYTE_WEB_LIVE=true für echte YouTube-Wiedergabe)
npm run package       # Windows-ZIP erzeugen: release/PYTE-vX.Y.Z-windows.zip
```

`npm run package` lädt das offizielle Electron-Windows-Paket, prüft dessen SHA-256-Summe und
baut daraus die portable Struktur. Die Electron-Version ist in `scripts/package-windows.mjs` festgelegt.

Architektur:
- `desktop/main.cjs` startet den lokalen Server im Electron-Hauptprozess und öffnet das Fenster.
- `desktop/preload.cjs` gibt der Oberfläche nur eine eng begrenzte Funktion zum Wachhalten frei.
- `server/` enthält den lokalen Dienst für Suche, Playlist-Import, Empfehlungen und Audio-Streams.
- `src/` enthält die Oberfläche (React, TypeScript).

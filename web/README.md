# PYTE Web · Desktop preview

Die Desktop-Web-App läuft lokal auf deinem Computer, ohne Konto und ohne serverseitige
Bibliothek. Sie ist ein eigenständiges Gegenstück zur Android-App, kein Ersatz für die APK.

## Fertigen ZIP-Download starten

1. **Node.js 22 oder neuer** von <https://nodejs.org/> installieren (LTS empfohlen).
2. Das gesamte ZIP in einen Ordner entpacken.
3. Windows: `Start-PYTE.bat` doppelklicken. Linux/macOS: im Ordner `sh Start-PYTE.sh` ausführen.
4. Der Starter öffnet `http://127.0.0.1:3080`. Terminal offen lassen; mit Strg+C beenden.

Im fertigen ZIP ist **kein `npm install` nötig**. Die Oberfläche ist bereits gebaut; der Server
verwendet ausschließlich Node-Bordmittel. Node selbst ist nicht im ZIP enthalten. Die Starter
sind noch nicht auf einem echten Windows-/macOS-Gerät qualifiziert.

Die Adresse/Portnummer möglichst beibehalten: Browserdaten sind an den Ursprung gebunden.
Bei einem Portkonflikt kann `PORT` gesetzt werden; eine andere Adresse hat aber eine getrennte
Browser-Bibliothek. Der Dienst bindet ausschließlich `127.0.0.1`, nicht das Heimnetzwerk.

## Bereits umgesetzt

- Desktop-Bibliothek mit linkem Menü, Queue/Lyrics rechts und dauerhaftem Audioplayer.
- Öffentliche Playlist-Links importieren; lokale Playlists erstellen, umbenennen, aktualisieren,
  löschen; lokale Ergänzungen bleiben beim Aktualisieren erhalten.
- Online-Suche mit Folgeseiten, lokale Suche, Favoriten und optionaler Hörverlauf.
- Audiostreaming über den lokalen Dienst, Seek/Volume/Shuffle/Repeat und editierbare Queue.
- Ähnliche Songs zu einem Titel oder einer Playlist, vorhören mit Rückkehr zur ursprünglichen
  Queue/Position, lokal hinzufügen. Automatische Empfehlungen am letzten Originaltitel nur bei
  ausgeschaltetem Shuffle/Repeat und fokussiertem Browser ohne offenen Dialog.
- Normale LRCLIB-Lyrics, reduzierte Bewegung, Tastatur- und unterstützte Media-Session-Steuerung.
- Browserlokale IndexedDB-Speicherung, Web-JSON-Export und validierter Import mit Bestätigung.

## Noch keine vollständige Android-Parität

**Offline-Audiodownloads, Equalizer, Stille überspringen, synchronisierte Lyrics und die native
Android-Hintergrund-Service-Garantie fehlen in dieser ersten Web-Version.** Es gibt dafür keine
funktionslosen Schalter. Ein geschlossener Browser, Schlafmodus oder Energiesparen können Musik
stoppen. Audio startet erst nach einer Benutzeraktion; OS-Medientasten hängen vom Browser ab.

Die Sammlung bleibt in diesem Browserprofil. Löschen von Website-Daten, privater Modus oder
Speicherbeschränkungen können Daten entfernen. Regelmäßig unter Einstellungen exportieren.
Kein automatischer Android-Sync und kein Android-Backup-Import. Der Browser speichert keine
Google-Account-Cookies; Suche/Import/Empfehlungen/Audio verbinden den lokalen Dienst mit YouTube.
Lyrics übertragen Titel, Interpret und Dauer an LRCLIB. Artwork lädt von YouTube-Bildservern.

YouTube verwendet inoffizielle Schnittstellen: Verfügbarkeit/Region/Altersbeschränkungen können
die Wiedergabe verhindern. Nur für persönlichen Gebrauch; Stream-Extraktion widerspricht den
YouTube-Nutzungsbedingungen. Den lokalen Dienst nicht öffentlich oder im LAN freigeben.

## Entwicklung im Repository

Node.js 24 LTS für Entwicklung/Tests empfohlen (die Tests importieren TypeScript direkt).

```sh
npm ci
npm run build
npm start
```

Entwicklung mit Hot Reload: in einem Terminal `npm run dev:server`, im zweiten `npm run dev`.
Danach `http://127.0.0.1:5173` öffnen. Dieser Entwicklungs-Ursprung hat getrennte Browserdaten.

```sh
npm test
npx playwright install chromium
npm run test:browser
PYTE_WEB_LIVE=true npm test
PYTE_WEB_LIVE=true npm run test:browser
npm run package
```

`npm run package` erzeugt `release/PYTE-v<VERSION>-web.zip` und prüft die gemeinsame Versionsnummer
gegen `app/build.gradle.kts`. Ein gemeinsames GitHub-Release kann Android-APK und Web-ZIP enthalten;
die Feature-Unterschiede müssen in den Release-Notizen stehen. Private Android-Signierschlüssel,
Browser-Bibliotheken, Node-Abhängigkeiten und Entwicklerquellcode gelangen nicht ins Web-ZIP.

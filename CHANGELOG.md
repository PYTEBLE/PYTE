# Changelog

## 0.2.0-alpha.2

- Add a violet Settings tab with locally persisted skip-silence and listening-history switches.
- Apply skip-silence changes to active playback. Allow history recording to be disabled without
  deleting existing entries, or clear history after confirmation without touching playlists,
  favorites or the current queue.
- Show app version and local-storage/network privacy information.

## 0.2.0-alpha.1

- Violet visual refresh based on the supplied original PYTE logo: derived adaptive launcher icon,
  original wordmark, branded welcome screen, semantic dark surfaces, gradient Play controls,
  rounded library cards, and a floating mini-player. Contrast tests cover text and button colors.
- Local playlist rename, source refresh, and confirmed deletion. Refresh preserves the local name,
  original import time, favorites, and repeated occurrences of the same track.
- SQLite FTS4 prefix search across titles and artists, including Unicode/diacritic handling;
  punctuation is sanitized before MATCH queries.
- Record successfully started songs in an on-device history and show a Recently played rail.
- Track menus for Play next / Add to queue; Now Playing has an editable queue with move up/down
  and removal controls. Explicit ordering switches shuffle off so Play next is predictable.
- Fix duplicate playlist-row keys and make playlist headers / Now Playing scroll on short screens.
- Database migration 1 → 2 preserves existing data; favorites and history survive playlist deletion.
- Disable Android cloud backups and automatic device transfers to respect local-only storage.
- Add database, migration, queue/player, and Android UI tests. Offline downloads remain deferred.

Verified: 45 unit tests, APK build, Android lint, and three Android 11 emulator tests
(queue controls, track action menus, and real/background playback of the reported track).
The welcome screen was also visually checked in the emulator. Physical-device testing of
this feature/design update and long-running screen-off playback remain pending.

## 0.1.0-alpha.4

- Obtain an anonymous YouTube visitor session before requesting playback through the VISIONOS client.
- Use a fresh playback nonce in the player request and its audio URLs; retain the matching User-Agent.
- Prefer the session-backed player, keeping the previous clients as fallbacks. No login, account cookies,
  third-party extraction backend, or NewPipe library dependency is added.
- Include a copyable service-side diagnostic dialog so error details survive the Media3 controller boundary.
- Stop automatic retry loops when every extraction client is exhausted.
- Add anonymous-session tests and an opt-in real-device/emulator playback test.

Verified: 31 unit tests, debug APK build, Android lint, and the live/background Android 11
emulator test for the reported track `schönhauser (feat. Gustav)` (`YGr2G2fc48U`). The
same track failed before session support. Background progress was checked over five seconds;
long-running screen-off operation and the affected physical phone still need verification.

## 0.1.0-alpha.3

- Probe audio URLs with a small byte-range request before selecting a YouTube client.
- Fall back to iOS when Android VR returns a URL but its audio server rejects access.
- Send the selected client's User-Agent on actual audio requests, not a hard-coded Android value.
- Skip a rejected client briefly after a late HTTP 403/410 and use format-specific audio cache keys.
- Show concrete HTTP status codes without exposing signed URLs.
- Add eight resolver tests and three error-message tests (25 tests total).

Validated with a debug APK build, unit tests, and Android lint (no errors).
Both clients served audio ranges in a development-machine smoke test; the reported
phone-specific HTTP failure could not be reproduced there. Physical-device confirmation is pending.

## 0.1.0-alpha.2

- Reconstruct playback URIs inside the media session when the controller supplies track IDs.
- Show player connection and playback errors in a snackbar instead of failing silently.
- Stop automatic stream retries after two attempts; pressing Play can prepare the track again.
- Add three Android-backed unit tests for playback item resolution.

Validated with a debug APK build, 14 unit tests, and Android lint (no errors).
The playback fix still needs verification on a physical Android device.

## 0.1.0-alpha.1

- Initial Android test build: public playlist imports, local library and favorites,
  search, and a Media3 background playback service.

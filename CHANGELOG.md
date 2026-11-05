# Changelog

## Unreleased

- Discover similar songs from a playlist or the full-screen player using anonymous YouTube
  Music song radios. Sample up to four playlist seeds, combine shared matches, and exclude
  existing songs and duplicate results with per-artist diversity limits.
- Open suggestions automatically when the final original playlist queue entry starts,
  once per playback run, only with shuffle/repeat off and the app focused/unlocked. Add a
  persisted Settings toggle; background or obstructed runs never create delayed popups.
- Preview recommendations through the existing media session without losing the original
  queue, position or playback modes. Return explicitly, close the sheet, or finish a preview
  to restore playback. Add directly to the source local playlist or choose/create a playlist
  for single-song recommendations; selected offline playlists keep their download constraints.
- Add a cover-aware discovery sheet with animated save feedback, accessible Listen/Add
  controls, retry/partial/empty states and a persistent preview-return action.

## 0.2.0-alpha.6

Install as an update over alpha.5 (same signing certificate, versionCode 10).

- Living UI: the whole app takes on colors from the current cover with smooth transitions.
  Without artwork PYTE stays violet.
- Aurora background: soft color fields that move only while music plays and settle on pause.
- Cover morph between the mini-player and the full player, reversible on close. Song changes,
  lyrics navigation or a missing anchor fall back to a clean fade.
- Animated play/pause morph, favorite burst with optional haptics, animated song changes,
  shimmer placeholders while artwork loads, animated queue reordering and an offline progress ring.
- New “Dezent / Lebendig / Intensiv” effect setting. System reduced motion, battery saver and
  background state always take priority.
- Fixes: a larger cover is used when available; closing a morph cannot leave the cover hidden;
  duplicate songs in the queue keep separate identities.

## 0.2.0-alpha.5

Install as an update over alpha.4: the signing certificate is unchanged and versionCode is 9.
Versions before alpha.4 used the unavailable old key and still require a clean installation.

- Fix the reported 120×120 cover for “Nur ein Wort” (Noah Kraus & Wir sind Helden): handle
  `yt3.googleusercontent.com` resizing and verify real decoded image dimensions. Upgrade old
  offline covers instead of permanently preferring low-resolution files; do not redownload audio.
- Derive animated, contrast-checked player/Lyrics/mini-player colors from the current cover.
  Keep the library violet and provide a persisted toggle with safe missing-artwork fallback.
- Add a native, session-scoped equalizer with actual device bands, Neutral/Bass/Voices/Warm/
  Electronic presets, up to 12 named editable profiles, reset, bypass and conservative boost
  headroom. Restore saved settings after audio-session/output changes and release effects
  when playback service stops. Explain unavailable effects rather than displaying fake bands.

## 0.2.0-alpha.4

**Installation warning:** this release uses a different debug signing key from earlier
releases. Uninstall the previous app before installing; this deletes its local library,
favorites, history and settings. No export/restore is available yet.

- Opt individual library playlists into durable offline downloads on unmetered Wi-Fi. Reuse
  shared songs, resume validated partial files, and play completed audio without online URL resolution.
- Show download progress and per-song availability, plus pause/resume, usage and configurable
  storage limits in Settings. Removing an offline playlist cleans files no other selection needs.
- Add LRCLIB lyrics with synchronized highlighting, automatic scrolling, tap-to-seek, a dark
  cover backdrop and animated violet waves. Cache available lyrics with downloaded tracks;
  explicitly handle plain/instrumental/missing lyrics and provider rate limits.
- Request higher-resolution cover variants with original-image fallback for existing and new
  tracks; bound the artwork disk cache to 96 MB. Migrate Room v1/v2/v3 libraries to v4.

## 0.2.0-alpha.3

- Search songs on YouTube Music without an account, load additional result pages, and play
  results or add them to the queue. The original on-device search remains available.
- Add songs to existing on-device playlist copies or create a new local playlist directly
  from a search result. Nothing is written to a YouTube account; duplicate adds are prevented.
- Migrate Room v1/v2 libraries to v3 without resetting data. Mark local additions so they
  survive refreshes from YouTube, including when a song temporarily appears in the source.
- Use the supplied background artwork throughout the app with a shared readability scrim.
- Include the Settings tab from the previously unpublished alpha.2 changes below.

## 0.2.0-alpha.2 (not published separately)

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

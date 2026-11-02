# Playback verification

## Cover-quality correction, dynamic themes and equalizer

Final validation: all 91 unit tests passed, none skipped, with `PYTE_LIVE_ARTWORK=true` and
`PYTE_LIVE_SEARCH=true` (including real artwork resolution and search pagination). Debug APK,
instrumentation APK and Android lint tasks passed. Alpha.5 uses versionCode 9 and its signing
certificate matches the published alpha.4 APK; a physical update-install test is still pending.

- Reproduced the reported song's artwork source through live YouTube Music search:
  `7y0c6q5QoWA` uses `yt3.googleusercontent.com`, which the previous host allowlist missed.
  Actual decoded responses are 120×120 for the old URL and 1200×1200 for the corrected URL.
  The larger source image was visually inspected; this is not a device UI screenshot check.
- Opt-in regression: `PYTE_LIVE_ARTWORK=true ./gradlew :app:testDebugUnitTest`. Native-graphics
  Robolectric tests verify decoded image bounds, rejection of invalid/downgraded covers and
  replacement of a small pinned cover without requesting audio again.
- Palette tests check cover-dependent accents, grayscale/transparent fallback and text contrast.
- Equalizer fixtures test logarithmic frequency mapping, bounded gains, portable saved profiles,
  session recreation, control loss, bypass/headroom, unsupported-device recovery and resource
  release. Session 0 (global output) is never used.
- New `EqualizerUiTest` cases cover unavailable controls and confirmed profile deletion. These
  instrumentation tests require a connected device/emulator; compiling them is not running them.

Manual checks still required: audible EQ changes on a physical phone with wired/Bluetooth
outputs, rapid output switches and competing system EQs; theme transitions on small screens
and with reduced motion; offline cover upgrades on the installed app. No phone/emulator was
attached for these changes. The native EQ can differ by manufacturer and headroom is not a
limiter. Cover upgrades respect existing Wi-Fi/download pause/storage rules and can wait up
to the next maintenance run; failed or insufficient upgrades retry at most once per day.

## Offline playlists, lyrics and artwork

Local validation: debug APK, instrumentation APK and Android lint build successfully;
78 unit tests ran with 77 passed and one opt-in live-network test skipped. No device/emulator
visual check, long-running background download test, or live LRCLIB/YouTube download test was
performed for these changes. The initial lint analyzer crash did not recur after the constructor
refactoring; the complete lint task subsequently passed without disabling checks.

New automated checks cover LRC parsing/timestamps/seek positions, high-resolution URL handling,
bounded response bodies, v3 → v4 migration, playlist-only download selection and shared songs.
Offline-store fixtures exercise atomic completion, persisted audio, validator-protected range
resume after a broken response, Wi-Fi/pause restrictions, storage limits and offline cleanup.
A Media3 data-source test verifies local ranged reads without invoking the stream resolver.
LRCLIB fixtures verify metadata matching, offline pinned text, cached misses and Retry-After.

Manual device checklist (not implied by unit-test success):

1. Select a playlist for offline use on Wi-Fi; inspect progress and storage usage.
2. Pause/resume; interrupt Wi-Fi; restart the app and let downloads recover.
3. Enable airplane mode, force-stop/reopen, then play and seek downloaded songs; check covers
   and available lyrics. Non-downloaded songs are not guaranteed to play offline.
4. Share a song between two selected playlists; deselect/delete one, then the other, and check
   that files are retained only while needed. Refresh/add tracks to an offline playlist.
5. Lower the storage limit; confirm new writes stop without deleting completed songs. Raise
   it and retry; test real low-storage failures too.
6. Check synchronized/unsynchronized/instrumental/missing lyrics, backward seek, manual scroll,
   follow button, song changes and accessibility animation/font settings on a small screen.
7. Compare existing/new cover artwork in lists and full-screen; test missing max-resolution
   variants and provider/network errors. Android versions below 31 do not support Compose's
   background blur and display the darkened cover without that effect.

## Local checks

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

These checks do **not** prove YouTube playback works on a phone or a particular network.

## Opt-in Android/network smoke test

Requires a running Android emulator or a USB-debugging device:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.livePlayback=true
```

The test starts a real public YouTube track, opens the Home screen, and checks that
playback remains active and its position advances. To reproduce a particular track:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.livePlayback=true \
  -Pandroid.testInstrumentationRunnerArguments.videoId=YGr2G2fc48U
```

Without `livePlayback=true`, this network-dependent test is skipped.

## Observed results before anonymous session support (2026-11-02)

- Android 11 emulator: the default public test track passed the live/background test.
- The reported track, `schönhauser (feat. Gustav)` (`YGr2G2fc48U`), failed the live test.
  Service logs showed an audio HTTP 403, followed by a resolver failure containing
  `LOGIN_REQUIRED`. This reproduces a source-access failure, **not a verified playback fix**.
- Explicit range headers did not produce a passing test and were not retained as a fix.
- The affected physical phone/network remains unverified locally.

## Device diagnostics on current main

After all extraction clients fail, a **Wiedergabe-Diagnose** dialog lists each client's
failure stage and safe reason. It is recorded inside the service, before Media3's
controller transport can discard exception details. Copying uses the Android clipboard;
the app does not upload the report. Reports omit signed URLs, playlist names, and IPs.

Stages: `player-api` (API request), `audio-format` (player status/format selection),
`audio-probe` (actual media access), `client` (temporarily excluded after a rejection).

Do not treat repeated `LOGIN_REQUIRED`/HTTP 403 responses as transient connectivity.
The original direct-client resolver was not a reliable general YouTube extraction solution.
The anonymous visitor session and playback-nonce flow added for alpha.4 addresses missing
request context, but does not remove YouTube restrictions or guarantee future availability.

## Verified with alpha.4

- 31 unit tests passed; debug APK and instrumentation APK compiled; Android lint passed.
- The live/background test for `YGr2G2fc48U` passed on the Android 11 emulator (one test,
  zero failures, zero skipped). It required real audio playback to start, then checked that
  playback stayed active and its position advanced after opening the Home screen.
- The background interval in that test is five seconds, not a long-running battery/Doze test.
- The affected physical phone and its WLAN/mobile networks have not been tested locally.

## Protocol references

The anonymous bootstrap flow was checked against the current behavior described by
[NewPipeExtractor's YoutubeStreamHelper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamHelper.java):
obtain a valid visitor identifier, use the VISIONOS player endpoint, and accompany media
URLs with the playback nonce. PYTE implements this protocol in its existing Kotlin/OkHttp
client; NewPipeExtractor is not bundled as an application dependency.

## Online search + local playlist additions (0.2.0-alpha.3)

- 55 unit tests passed (zero skipped), including live anonymous song search and continuation,
  Room v1/v2 → v3 migrations, local additions surviving repeated source refreshes, duplicate
  prevention, local-only playlist creation, settings persistence and history controls.
- Contrast tests check semantic tokens plus every pixel of the supplied background after
  its shared dark scrim. APK, Android test APK and lint builds passed.
- Eight Android 11 emulator tests passed (zero skipped): queue controls, track action menu,
  settings toggles/clear confirmation, local-playlist dialog interactions, live search →
  create local playlist → open in library → delete, and live/background playback.
- Welcome and search screens were visually checked with the new background; captures are
  in `docs/screenshots/`. Published alpha.1 and the new APK have matching signing certificates.
- Physical-phone update, long-duration screen-off/Doze playback and restricted/region-specific
  catalog coverage still need checking. No guarantee of access to every YouTube Music track.

Run the optional live unit search check with `PYTE_LIVE_SEARCH=true ./gradlew :app:testDebugUnitTest`.
For Android tests, enable `-Pandroid.testInstrumentationRunnerArguments.liveSearch=true` as well
as the existing live playback arguments.

## Settings (0.2.0-alpha.2; included in alpha.3)

- Unit tests cover settings defaults/persistence and independent switches, disabled history
  recording, re-enabling recording, and history deletion preserving playlists and favorites.
- `SettingsUiTest` covers switch actions and the cancel/confirm deletion flow. It passed on
  the Android 11 emulator as part of alpha.3 validation.
- Manual device checks still needed: toggle skip silence while playing a track with quiet gaps,
  restart the app to check persistence, and clear history while retaining the current queue.

## Phase 4 + violet design (0.2.0-alpha.1)

- 45 unit tests passed, including Room v1 → v2 migration with existing playlists/favorites,
  Unicode prefix search, refresh preserving local names, ExoPlayer queue edits preserving
  the current item's identity/position, and semantic text/button contrast.
- APK build and Android lint passed.
- Three Android 11 emulator tests passed (zero skipped): two Compose UI tests for queue/track
  actions, plus live/background playback of `YGr2G2fc48U`.
- The welcome screen was visually reviewed; its emulator capture is in `docs/screenshots/welcome.png`.
- The user confirmed physical-phone playback on alpha.4. This newer feature/design update
  still needs a phone update test. The emulator's five-second background check is not a
  long-running screen-off, battery, or Doze qualification.

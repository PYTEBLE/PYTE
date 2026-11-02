# Playback verification

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

## Settings (0.2.0-alpha.2)

- Unit tests cover settings defaults/persistence and independent switches, disabled history
  recording, re-enabling recording, and history deletion preserving playlists and favorites.
- `SettingsUiTest` covers switch actions and the cancel/confirm deletion flow. The test APK
  compiles; this new UI test has not yet been run on an emulator or physical device.
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

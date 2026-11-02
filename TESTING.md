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

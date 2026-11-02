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

## Observed results (2026-11-02)

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
The current direct-client resolver is not a reliable general YouTube extraction solution;
it needs a maintained extraction strategy that accounts for current YouTube challenges.

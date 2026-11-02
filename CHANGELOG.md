# Changelog

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

# Changelog

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

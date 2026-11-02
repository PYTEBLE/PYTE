# Changelog

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

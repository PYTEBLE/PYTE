# PYTE

A local-first Android music player for public YouTube Music playlists.

- Paste a public playlist link once. No account, no login.
- Plays audio directly, so there are no ads.
- Plays in the background through a foreground media service, with lock-screen and notification controls.
- All data stays on the device (Room database, on-device stream cache).

> Personal use only. Extracting streams is against YouTube's Terms of Service. Do not publish this app to the Play Store or run it as a public service.

## Build

Requirements: JDK 17, Android SDK with platform 37.0 and build-tools 37.0.0.

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

Signed release builds: copy `keystore.properties.example` to `keystore.properties` and point it at your keystore. Without it, `assembleRelease` uses the debug key.

## Architecture

| Layer | Path | Notes |
|---|---|---|
| UI | `ui/` | Jetpack Compose, Material 3, dark only |
| Playback | `playback/` | `PlaybackService` (Media3 `MediaSessionService`) owns ExoPlayer. The UI only sends commands through a `MediaController`. |
| Extraction | `data/remote/` | InnerTube player (`ANDROID_VR`, falling back to `IOS`) and `WEB_REMIX` browse. Playlist paging via continuation tokens. |
| Storage | `data/local/` | Room: playlists, tracks, playlist order, favorites |
| Cache | `di/AppModule.kt` | Media3 `SimpleCache`, LRU, 1.5 GB |

Stream URLs are resolved lazily, when the player requests bytes, and cached until shortly before their `expire` time. A one-byte range probe checks audio access before selecting a client; if it fails, the resolver tries the next client. Each stream uses its originating client's User-Agent. Late HTTP 403/410 responses temporarily exclude that client during recovery. Audio cache keys include the format ID so fallback cannot mix different encodings.

See [CHANGELOG.md](CHANGELOG.md) for test-build changes and verification limits.

# APK signing from alpha.7

## Alpha.10 exception (explicitly approved)

The dedicated key is absent on this build machine. The user explicitly approved a
signature-incompatible release instead of blocking publication. Alpha.10 is the debug
variant signed by the existing machine debug key (`~/.android/debug.keystore`), not the
pinned alpha.7 identity. It cannot update alpha.7/alpha.8 in place. Uninstalling those versions
deletes local playlists, favorites, history, settings and downloads; there is no export flow.
Release notes must state this before installation instructions. `tools/sign-apk.sh` retains
its strict checks and is **not** bypassed or repurposed to claim compatibility.

This does not establish a stable production signing identity. Do not promise future
in-place updates without checking the published APK certificate.

Alpha.10 debug certificate SHA-256 (public fingerprint):

`7202ac27411fa7b98478e81ec025a269d66f985aaa9ec15b5db76995a0e674e0`

The signing key used for the published alpha.6 is unavailable. With the user's explicit
approval, alpha.7 starts a new signing identity. Android will not install it over earlier
versions. Uninstalling deletes the local library, favorites, history, settings and downloads;
no library export/import feature is available yet.

## Dedicated key and backup

The new key is generated once, outside the repository:

- `~/.android/pyte-release.keystore` — PKCS12, alias `pyte-release`.
- `~/.android/pyte-release.keystore.password` — password file.

Both files must be kept private and backed up together in secure storage. Neither belongs
in Git, screenshots, logs or release assets. Copy both files securely to a different build
machine before publishing future updates. Never generate a replacement merely because the
key is missing; that would break update compatibility again.

Public certificate SHA-256 fingerprint (not a secret):

`285fc88b841b9dd23a3ae1a188271f854bf173989acad955b5a9c71c32d2bf4b`

## Reproducible alpha packaging

The published alpha is the debug build variant with the dedicated signing identity. No
claim of production/minified-build qualification is implied.

```bash
./gradlew :app:assembleDebug
bash tools/sign-apk.sh app/build/outputs/apk/debug/app-debug.apk /path/to/PYTE-version.apk
```

Requires JDK 17 and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) with build-tools 37.0.0.
The script verifies the result and prints the public certificate fingerprints, never the
password. It deliberately fails if the key is missing instead of silently using a debug key.
It also checks the signed APK against the pinned alpha.7 certificate fingerprint.
For a non-default location, set `PYTE_SIGNING_KEYSTORE`, `PYTE_SIGNING_PASSWORD_FILE` and,
if necessary, `PYTE_SIGNING_ALIAS`.

The raw `app-debug.apk` remains signed with the machine's normal Android debug key. It is
not interchangeable with the published alpha APK. Before releasing, compare the final APK's
certificate fingerprint with the previous dedicated-key release and include a SHA256 checksum.

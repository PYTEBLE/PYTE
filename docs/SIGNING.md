# APK signing from alpha.7

## Current release identity (1.0.1 and later)

Version 1.0.2 (versionCode 20) uses the same certificate as published 1.0.1:
`285fc88b841b9dd23a3ae1a188271f854bf173989acad955b5a9c71c32d2bf4b`.
It can update 1.0.1 in place, without uninstalling or clearing app data.
The 1.0.0 certificate is different; users of that version must export a backup before
uninstalling, then install the current version and restore the backup.

## 1.0.1 signature change (explicitly approved)

Version 1.0.1 (versionCode 19) is signed with the local release key in
`~/.android/pyte-release.keystore`, certificate SHA-256
`285fc88b841b9dd23a3ae1a188271f854bf173989acad955b5a9c71c32d2bf4b`. This key does not match
the 1.0.0 certificate `10218a7d…f728`, so Android will not install 1.0.1 over 1.0.0.
Users must export their data (Einstellungen → Daten sichern), uninstall 1.0.0, then install
1.0.1 and import the backup. `tools/sign-apk.sh` now pins this fingerprint. Future updates
must use this certificate. Release notes must state this before installation instructions.

## Historical 1.0.0 signing identity (key unavailable here)

Version 1.0.0 (versionCode 18) was signed with the certificate below. Its key is no longer
available on this machine. The historical paths describe the original setup, not the
certificate currently stored there. Do not use this section to claim update compatibility.

- Keystore: `~/.android/pyte-release.keystore` (PKCS12, alias `pyte-release`, RSA 4096)
- Password file: `~/.android/pyte-release.keystore.password` (mode 600)
- Gradle config: `keystore.properties` (git-ignored, copy from `keystore.properties.example`)
- Certificate SHA-256 (public fingerprint, not a secret):

`10218a7de73ebde6b7255a45f56d5ca5062ffa019d1cc4a4f88de341cbcdf728`

Back up the keystore and password file outside this machine. Losing the key means users
must uninstall before any new version can be installed. Never commit either file.

Alpha.10 to alpha.13 use the debug certificate below and cannot update to 1.0.0 in place.
Users must uninstall them first.

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

Alpha.11 and alpha.12 use this same certificate. Alpha.12 (versionCode 16) retains the
debug-variant exception; its certificate was compared with alpha.11's published fingerprint.
Do not uninstall alpha.10/alpha.11 to update. This does not make it compatible with alpha.7/8.

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

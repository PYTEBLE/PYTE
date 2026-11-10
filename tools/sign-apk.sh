#!/usr/bin/env bash
# Sign an already-built APK with the dedicated PYTE key; never create a fallback key.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: bash tools/sign-apk.sh INPUT.apk OUTPUT.apk" >&2
    exit 1
fi

keystore="${PYTE_SIGNING_KEYSTORE:-$HOME/.android/pyte-release.keystore}"
password_file="${PYTE_SIGNING_PASSWORD_FILE:-$keystore.password}"
alias="${PYTE_SIGNING_ALIAS:-pyte-release}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" ]]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
signer="$sdk/build-tools/37.0.0/apksigner"
if [[ ! -f "$keystore" || ! -f "$password_file" ]]; then
    echo "Signing key/password file missing. Restore the existing key; do not generate a replacement." >&2
    exit 1
fi
if [[ "$1" == "$2" ]]; then
    echo "Input and output APK paths must differ." >&2
    exit 1
fi

"$signer" sign --ks "$keystore" --ks-key-alias "$alias" \
    --ks-pass "file:$password_file" \
    --out "$2" "$1"
verification="$("$signer" verify --verbose --print-certs "$2")"
printf '%s\n' "$verification"
expected_certificate="10218a7de73ebde6b7255a45f56d5ca5062ffa019d1cc4a4f88de341cbcdf728"
if [[ "$verification" != *"certificate SHA-256 digest: $expected_certificate"* ]]; then
    echo "Certificate differs from the pinned PYTE release key. Do not publish this APK as a compatible update." >&2
    exit 1
fi

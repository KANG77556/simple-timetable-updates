# Android release signing

The signed release workflow is `.github/workflows/android-release.yml`.

Configure these repository Actions secrets before running it:

- `ANDROID_KEYSTORE_B64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The keystore must remain stable for the lifetime of the Android application. Replacing it changes the app signing identity and prevents an in-place update over APKs signed by a different key.

## Build and verification

Run **Build Signed Android Release** from GitHub Actions.

The workflow:

1. restores the keystore from `ANDROID_KEYSTORE_B64`;
2. builds `assembleRelease`;
3. verifies the APK with `apksigner verify --verbose --print-certs`;
4. checks package/version metadata with `aapt dump badging`;
5. calculates SHA-256;
6. uploads `SCERP-Android-signed-release`.

Do not commit the keystore, passwords, base64 keystore content, or generated secret values to the repository.

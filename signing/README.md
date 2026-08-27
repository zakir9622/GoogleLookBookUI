# APK signing

## Stable releases

Stable GitHub Releases preserve update compatibility by using the existing sideload certificate stored in GitHub Actions Secrets. Configure these repository secrets before pushing a stable `vX.Y.Z` tag:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded preserved sideload keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Preserved signing alias |
| `KEY_PASSWORD` | Signing-key password |

The release workflow fails closed when these secrets are absent. The keystore and credentials must never be committed to the public repository.

## Test prereleases

Tags containing `-test.` or `-test-` generate a disposable runner-local keystore. These APKs are intentionally not update-compatible with previous builds; uninstall the previous app before installing a new test prerelease.

## Play Store

Do not use the sideload certificate for Google Play uploads. Use a separate private Play App Signing/upload-key setup.

# Sideload signing

`lookbook-sideload.keystore` is the **stable public signing key** for GitHub Release /
CI sideload APKs (`com.zakir.vestra`).

Android only allows in-place updates when the new APK is signed with the **same**
certificate. Older Lookbook releases regenerated a random keystore on every CI
run, which forced uninstall + “App not installed” / Play Protect re-verify loops.

## Credentials (intentionally public — sideload only)

| Field | Value |
|-------|--------|
| Store password | `lookbook-sideload` |
| Key password | `lookbook-sideload` |
| Alias | `lookbook` |
| SHA-256 | `2F:60:3A:F1:E3:BE:46:C4:27:73:DF:44:6C:49:0D:3A:B5:B0:E1:F7:55:3B:8D:86:F9:FF:1B:E0:23:CD:9D:D3` |

Do **not** use this keystore for Play Store uploads — use a private upload key.

## One-time migration

If you installed an APK from before **v3.0.16**, uninstall once, then install
`the-lookbook-v3.0.16.apk` (or newer). After that, later versions update in place
without uninstall.

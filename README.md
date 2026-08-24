# The Lookbook — Local Modest-Wear AI Studio

An Android app that turns a garment photo into a photorealistic model wearing it — abaya, hijab, niqab, shalwar kameez, kurta, and more. **Everything runs on-device.** Model packs download once; generation works fully offline after that.

> Internal package: `com.zakir.vestra` · Product name: **The Lookbook**

## Features

- **Fully local AI** — SD1.5 + ControlNet-Depth + IP-Adapter try-on via ONNX Runtime
- **Casting parameters** — ethnicity, skin tone, body type, hair coverage, garment color, scenario
- **Modest-wear first** — abaya, hijab, niqab, dupatta, Pakistani traditional wear
- **Spatial Material 3 UI** — elevated cards, light spatial palette
- **Pixel 9 ready** — 10 GB+ RAM gate, NNAPI acceleration, INT8 pack support
- **Sideload build** — unrestricted local generation, no cloud dependency

## Generation engines

| Tier | Where | Devices | Pack |
|---|---|---|---|
| **Pro** | On-device diffusion | 10 GB+ RAM (Pixel 9) | `pro-v1` (~4.3 GB FP16) on HF; `pro-v2-int8` (~2 GB) export ready, upload pending |
| **Lite** | On-device compositor | Android 15+ (`minSdk 35`) | `lite-v1` (~68 MB) |
| **Auto** | Best installed on-device | — | Never uses network |

## Build & install (Pixel 9)

Requires JDK 17+, Android SDK (platform 36), and a device/emulator on **Android 15 (API 35)+**.


```bash
# Sideload release uses the committed stable key (in-place updates across versions):
./gradlew :composeApp:assembleSideloadRelease

# Install / update on Pixel 9 (after v3.0.16, no uninstall needed between builds)
# Latest RC: https://github.com/zakir9622/Agentic-AI/releases/download/v3.1.0-rc1/the-lookbook-v3.1.0-rc1.apk
adb install -r composeApp/build/outputs/apk/sideload/release/*.apk
```

> **Note:** APKs before v3.0.16 used a random CI keystore each build. Uninstall once,
> then install v3.0.16+ — later updates install over the existing app.

After install: open app → **Model packs** → download Pro pack over Wi-Fi → create a look.

## Project layout

- `composeApp/` — Android UI (Jetpack Compose, Spatial Material 3)
- `shared/` — KMP core: engines, pack manager, domain models
- `ml/` — Python tooling to export/quantize model packs (not shipped in APK)
- `docs/` — architecture, pipeline, compliance

## Model packs

Pro packs are hosted on Hugging Face (`Iamzakirzr/vestra-packs`). To rebuild:

```bash
cd ml && ./download_pro_models.sh pro_src
python convert_pro_pack.py --src pro_src --out exports/pro-v1
python export_depth.py --out exports/pro-v1/depth.onnx
python quantize_pro_pack.py --src exports/pro-v1 --out exports/pro-v2-int8
python manifest_gen.py exports/ --base-url https://huggingface.co/datasets/Iamzakirzr/vestra-packs/resolve/main
```

## Tests

```bash
./gradlew :shared:testDebugUnitTest
./gradlew :composeApp:lintDebug
```

## License

GPL-3.0 — see [LICENSE](LICENSE).

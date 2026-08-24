# The Lookbook — Project Status

> Modest-wear AI studio for Android. Last updated: 2026-08-22 (**v3.1.0-rc1**).

## What this app is

**The Lookbook** is an Android app for virtual try-on and AI studios — abaya, hijab, niqab, shalwar kameez, and more. It runs **on-device** (Fast/Pro ONNX packs) and optional **free cloud** studios (HF Spaces + Inference Providers).

- **Package:** `com.zakir.vestra`
- **Target device:** Pixel 9 class (10 GB+ RAM) for Pro; Fast (lite-v1) on **Android 15+** (`minSdk = 35`)
- **License:** GPL-3.0

## Current status

| Component | Status |
|---|---|
| Fast local try-on (lite-v1 ONNX seg + parse) | ✅ |
| Pro on-device engine (SD1.5 + ControlNet + IP-Adapter) | ✅ |
| Cloud image / edit / code / video (free HF + Groq + OpenRouter) | ✅ |
| HF Inference Providers + image-to-image edit | ✅ |
| HF router model discovery on token save | ✅ |
| Home pager (Try-on · Image · Video · Code · News) | ✅ |
| Unified composer + on-device model group + advanced sampler | ✅ |
| Settings hub + section split (C4) | ✅ **v3.0.6** |
| Diagnostics export (runs + usage ledger) | ✅ |
| CI: manifest verify + integration-local-models + benchmarks | ✅ |
| Quality packs (BiRefNet, Real-ESRGAN) on HF manifest | ✅ birefnet-v1 + realesrgan-v1 |
| Quality pack runners + local crash hardening | ✅ **v3.0.4** |
| pro-v2-int8 HF manifest | ⏳ export ready; app prefers pro-v1 until upload |
| Offline Create Studio (SD-Turbo pack) | ⏳ `Txt2ImgPipeline` @ 3.1.0-rc1; weights + `SAMPLER_WIRED` pending |
| Big release R2 (true limits) | [`docs/plans/big-release-r2/PLAN.md`](plans/big-release-r2/PLAN.md) — **v3.1.0-rc1** |
| Full ATR Auto classify (14 categories) | ✅ **v3.1.0-rc1** |
| Stable release checklist | [`docs/plans/stable-release/PLAN.md`](plans/stable-release/PLAN.md) — **R0 shipped**; handoff to R2 |
| Plan completion (Claude + follow-up) | [`docs/plans/COMPLETION.md`](plans/COMPLETION.md) — **~95%** in-repo |
| Live model health UI + blank-frame reject | ✅ **v3.0.5** |
| Settings C4 split + durable CTA on download | ✅ **v3.0.6** |
| Auto crash troubleshooting (append-only + abrupt-exit watchdog) | ✅ **v3.0.11** |
| ORT CPU-default + soft startup verify (Pixel NNAPI kill fix) | ✅ **v3.0.12** |
| Stable sideload signing (in-place APK updates) | ✅ **v3.0.16** |

## Build

```bash
./gradlew :composeApp:assembleSideloadRelease   # signed sideload APK
./gradlew :shared:testDebugUnitTest
python3 scripts/integration-local-models.py --skip-hf-download
python3 scripts/benchmark-local.py
python3 scripts/probe-models.py --quick          # live cloud smoke (needs tokens)
bash scripts/visual-verify.sh                    # device screenshots
```

## Architecture

```
composeApp/     HomeScreen pager, UnifiedStudioPane, settings hub, pack downloads
shared/         EngineRouter, cloud clients, discovery, diagnostics, safety gate
ml/             export_lite_pack.py, export_birefnet_pack.py, manifest_gen.py
```

## Model packs

Hosted at `Iamzakirzr/vestra-packs` (manifest published 2026-08-22):
- `lite-v1` — Fast ONNX (~68 MB) — **on HF manifest** ✅
- `pro-v1` — FP16 SD1.5 pack (~4.3 GB) — **on HF manifest** ✅ (default Pro download)
- `pro-v2-int8` — INT8 UNet/ControlNet (~2 GB) — export ready; manifest upload pending
- `birefnet-v1` — BiRefNet Swin-Tiny matte (~224 MB) — **on HF manifest** ✅
- `realesrgan-v1` — Real-ESRGAN upscale (~5 MB) — **on HF manifest** ✅
- `lite-v2` — optional Fast upgrade (export via ml/; publish pending)

Verify live manifest: `python3 scripts/verify-manifest.py`  
Publish updates: `python3 scripts/publish-packs.py` (requires HF credentials)

# Vestra Architecture

## Overview

Vestra generates images of a person wearing a garment, from a photo of the garment alone. Everything the user does day-to-day — capture, generation, history — works **fully offline**; the network is used only to download model packs once and, when the user explicitly opts in, for the cloud tier.

```
┌────────────────────────────── composeApp (Android) ──────────────────────────────┐
│  Jetpack Compose UI · navigation · CameraX capture · photo picker · AGSL shaders │
└───────────────┬───────────────────────────────────────────────────────────────────┘
                │
┌───────────────▼────────────────── shared (KMP) ───────────────────────────────────┐
│ commonMain (pure Kotlin — iOS-reusable)                                            │
│   domain/    TryOnRequest · TryOnResult · GenerationState · ModelPack · EngineTier │
│   engine/    TryOnEngine interface · EngineRouter (AUTO policy)                    │
│   packs/     ModelPackManager: HF manifest, downloads, checksums     (M3)          │
│   cloud/     CloudEngine → Supabase Edge Function                    (M5)          │
│   wardrobe/  generation history (JSON index via TextFileStore seam)  (M2)          │
│   settings/  AppSettings (multiplatform-settings)                                  │
│   safety/    consent gate · content-filter orchestration             (M6)          │
│ androidMain                                                                        │
│   LiteEngine (LiteRT)                                                (M3)          │
│   DiffusionEngine (ONNX Runtime + QNN EP)                            (M4)          │
│   DeviceCapabilities probe                                           (M4)          │
└────────────────────────────────────────────────────────────────────────────────────┘
```

## Module choices

- **`composeApp` is a plain Android module** using Jetpack Compose (androidx artifacts), not the JetBrains Compose Multiplatform plugin. Reason: maximum build reliability and full access to Android-only APIs the cinematic UI needs (AGSL `RuntimeShader`, haptics, CameraX). Compose code migrates to CMP nearly verbatim when the iOS port starts; the real portability boundary is `shared/`.
- **`shared` is Kotlin Multiplatform** with `commonMain` kept free of Android imports. ML engines are `androidMain` implementations of the common `TryOnEngine` interface; iOS later supplies CoreML `actual`s (see `IOS_PORT.md`).
- **Manual DI** in `VestraApp` — the graph is a handful of singletons; a DI framework adds no value at this size.

## Engine routing

`EngineRouter.resolve`:

- `AUTO` → `PRO` if its pack is installed **and** the device passes the capability gate, else `LITE`.
- `CLOUD` is **never** selected implicitly. Images leave the device only when the user has explicitly chosen the Cloud tier in Settings. The Play data-safety declaration ("data collected: none by default") depends on this invariant — do not weaken it.

Engines never throw for expected failures; they emit `GenerationState.Failed(TryOnError)` so the UI can render every failure state cinematically instead of crashing.

## Try-on pipelines

### Lite (M3) — all devices, ~300 MB pack
1. Garment segmentation (U²-Net-class, INT8 LiteRT) → cutout + mask
2. Person analysis: pose landmarks + human parsing → body-region masks
3. TPS/appearance-flow warp of the garment onto the target regions
4. Harmonization net blends lighting/color at the seams

### Pro (M4) — flagships, ~2.5–4 GB pack
CatVTON-class single-UNet try-on diffusion, INT8-quantized, executed with ONNX Runtime + QNN EP (NPU) with CPU/GPU fallback. Reuses Lite's stage-1/2 outputs for the inpaint mask. Gated on `DeviceCapabilities` (RAM ≥ 8 GB, supported accelerator).

### Cloud (M5)
App uploads garment+person to Supabase Storage (short-TTL signed URLs) → Edge Function calls Replicate → result streamed back → inputs deleted. Replicate API key never ships in the APK.

### AI-model mode
"Generate on an AI model" = try-on onto a base image from a curated gallery of synthetic/licensed model photos (downloadable pack). Identical code path across all tiers; no on-device text-to-image model needed.

## Model packs (M3)

`manifest.json` on a Hugging Face Hub repo lists packs → files → sha256/bytes → `DeviceSpec` gate. Downloads are resumable (HTTP Range) via Ktor inside a WorkManager `dataSync` foreground worker; files verify against sha256 before an atomic move into `filesDir/packs/<id>/<version>/`.

## Play compliance invariants (full checklist in PLAY_COMPLIANCE.md)

- No `READ_MEDIA_*` permissions — system photo picker only.
- Person-photo mode is gated behind a one-time likeness-consent acknowledgement.
- Every generated image is watermarked + metadata-tagged as AI-generated, and every result screen exposes a Report action.
- Safety classifiers run on inputs before any engine (including Cloud) executes.

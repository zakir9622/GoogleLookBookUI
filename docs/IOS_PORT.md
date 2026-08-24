# iOS Port Plan

The iOS app is a later phase; this file records what today's code already guarantees and the concrete steps when the port begins.

## What is already portable

- Everything in `shared/src/commonMain` is pure Kotlin (coroutines, serialization, Ktor, multiplatform-settings) and compiles for `iosArm64`/`iosSimulatorArm64` unchanged: domain models, `EngineRouter`, pack manifest handling, cloud client, settings, safety orchestration.
- The `TryOnEngine` interface is the porting seam: iOS supplies CoreML-backed implementations without touching callers.

## Port steps

1. Add `iosArm64()` + `iosSimulatorArm64()` targets to `shared/build.gradle.kts` (requires a macOS host) and an XCFramework export.
2. Convert model packs: LiteRT/ONNX exports get sibling CoreML packages (`.mlpackage`). Apple NPUs prefer FP16/BF16 over INT8 — re-quantize accordingly (Mobile-VTON's published results confirm BF16 viability on A-series).
3. Implement `LiteEngine`/`DiffusionEngine` actuals in Swift (called through the interface via a thin Kotlin/Native wrapper), or in `iosMain` Kotlin using the CoreML C interop.
4. UI: either SwiftUI from scratch reusing shared ViewState, or migrate `composeApp` to Compose Multiplatform (the code is written in CMP-compatible style — androidx imports swap to multiplatform artifacts, AGSL shaders swap to Skia `RuntimeEffect`).
5. Pack manifest gains per-platform file lists (`android/`, `ios/` subtrees per pack) so one manifest serves both apps.

## Things intentionally kept out of commonMain

- Bitmap/image types (platform-specific; engines exchange file paths instead)
- WorkManager (Android download scheduling lives in `androidMain`; iOS will use `URLSession` background downloads)
- AGSL shaders (Android-only; Skia equivalents exist in CMP)

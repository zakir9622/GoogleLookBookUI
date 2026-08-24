# LiteRT-LM integration — Gallery-class models in Lookbook

> **Baseline:** v3.1.0-rc16. Supplements [`../five-star-quality/`](../five-star-quality/) and
> [`../true-local/`](../true-local/). No new visual design system (Loom Ink stays).
> **Status:** Implemented (rc17).

## Re-check vs tree (2026-08-23)

| Item | Current tree |
|------|--------------|
| Code offline | `AndroidLocalCodeGenerator` → MediaPipe `.task` (`local-gemma-v1`, ~530 MB) |
| LiteRT-LM | **Integrated** — `litertlm-android:0.10.2`, Gemma 4 Code + vision + audio scribe |
| Kotlin | **2.3.0** |
| Image / try-on | ONNX Runtime (`local-sdturbo-v1`, `lite-v1`, `pro-v1`) — **keep** |
| Audio offline | System TTS + DSP voice changer — **keep** |
| Pack pipeline | HF manifest → `ModelPackManager` → handshake — **reuse** |
| HF publish | **Live** — `local-gemma-4-e2b-v1` (~2.6 GB) + `local-functiongemma-v1` (~284 MB) on vestra-packs |
| Engine cache | **Warm reuse** — `LiteRtLmEngineCache` + 90s timeout; no per-shot cold load |
| Gallery models | Published as `.litertlm` packs + LiteRT-LM engine |

---

## Why this plan exists

Google AI Edge Gallery is the reference app for on-device GenAI on Android. It runs Gemma 4,
multimodal “Ask Image”, audio transcription, and experimental tool use through **LiteRT-LM** —
Google’s current stack. Lookbook’s Code path still uses **MediaPipe LLM** (`.task`), which Google
documents as maintenance mode. Migrating to LiteRT-LM unlocks:

- **Gemma 4** (better reasoning than Gemma 3 1B) for Code Studio
- **Vision-language** assists for fashion (describe garment, suggest prompts) without cloud
- Optional **STT / translate** and **function calling** later

This plan does **not** replace try-on diffusion or tiny-SD Create — those stay on ONNX Runtime
where we already have working packs, benchmarks, and rc16 stability fixes.

---

## Architecture — three on-device runtimes

```
┌─────────────────────────────────────────────────────────────────┐
│                     Lookbook (composeApp)                        │
├─────────────────────────────────────────────────────────────────┤
│  Try-on Studio     │  Create / Edit / Video  │  Code / Assist   │
│  Lite + Pro        │  tiny-SD                │  LLM + VLM       │
├────────────────────┼─────────────────────────┼──────────────────┤
│  ONNX Runtime      │  ONNX Runtime           │  LiteRT-LM       │
│  (.onnx packs)     │  (.onnx packs)          │  (.litertlm)     │
├────────────────────┴─────────────────────────┴──────────────────┤
│  Audio TTS: Android system engine (unchanged)                      │
│  Cloud: GenerativeCloudService (unchanged invariants)              │
└─────────────────────────────────────────────────────────────────┘
         ▲                           ▲                    ▲
    lite-v1, pro-v1            local-sdturbo-v1      local-gemma-4-* 
    birefnet, realesrgan       (shared for video)    (new packs)
```

**Rule:** one capability → one primary runtime. Do not run the same studio action through both
MediaPipe and LiteRT-LM in production; pick LiteRT-LM after L1 ships and deprecate MediaPipe.

---

## Gallery capabilities → Lookbook mapping

| Gallery feature | LiteRT-LM model examples | Lookbook capability | Priority |
|-----------------|--------------------------|---------------------|----------|
| Chat / Prompt Lab | `gemma-4-E2B-it.litertlm` | **Code Studio** (+ general chat later) | **L1** |
| Ask Image | Gemma 4 multimodal `.litertlm` | **Assist**: describe garment / reference photo | **L2** |
| Audio Scribe | Whisper-class / Gemma audio | **Audio STT** tab or mic → text | **L3** |
| Mobile Actions / FunctionGemma | `functiongemma-270m` | **Studio assists** (local tool calls) | **L4** |
| Model benchmark | built-in | Extend `OnDeviceOrtBenchmarkTest` or sibling | L1+ |
| Try-on / SD image gen | — | **Not from Gallery** — keep ORT packs | — |

Models come from [litert-community](https://huggingface.co/litert-community) (pre-converted
`.litertlm`). Lookbook downloads via existing **Model packs** — not from Gallery’s storage.

---

## Part L0 — Foundation (prerequisite)

### L0.1 Kotlin / Gradle uplift

LiteRT-LM 0.10.x is built with Kotlin **2.3** metadata. Before adding the SDK:

1. Bump `gradle/libs.versions.toml` `kotlin = "2.3.0"` (or latest 2.3.x)
2. Fix any `kotlinOptions { jvmTarget }` → `compilerOptions` if compiler errors
3. Full CI green on `main` branch **before** LiteRT-LM dependency

**DoD:** `./gradlew :shared:testDebugUnitTest :composeApp:testSideloadDebugUnitTest` green on Kotlin 2.3.

### L0.2 LiteRT-LM spike (no UI)

Add to `composeApp/build.gradle.kts`:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2") // pin, not floating
```

Manifest (GPU backend):

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

Spike module: `shared/src/androidMain/.../engine/litert/LiteRtLmEngine.kt`

- Load a test `.litertlm` from app files dir (manual push or debug asset)
- `EngineConfig(modelPath, backend = Backend.CPU())` on `Dispatchers.Default`
- Single-turn generate; close engine in `finally`
- Log cold load ms + tokens/sec to logcat tag `LookbookLiteRtLm`

Reference: [LiteRT-LM Android guide](https://developers.google.com/edge/litert-lm/android),
[Gallery source](https://github.com/google-ai-edge/gallery).

**DoD:** Instrumented or manual test proves one prompt/response on Pixel 9 without ANR.

### L0.3 Pack format spec

New pack type in manifest schema (extend, don’t break existing):

| Field | Value |
|-------|-------|
| `id` | e.g. `local-gemma-4-e2b-v1` |
| `runtime` | `litert-lm` |
| `primaryFile` | `gemma-4-E2B-it.litertlm` |
| `minRamMb` | 8192 (E2B); 12288 for E4B |
| `license` | Gemma Terms of Use |
| HF source | `litert-community/gemma-4-E2B-it-litert-lm` |

Integrity: file size + sha256 (same as today). Optional: LiteRT-LM `Engine` open probe in
`PackIntegrityChecker` (like ONNX graph load).

---

## Part L1 — Code Studio: Gemma 4 E2B (replace MediaPipe path)

### L1.1 Engine abstraction

Introduce commonMain contract (mirror `LocalCodeGenerator`):

```kotlin
// shared/.../engine/local/LocalLlmEngine.kt
interface LocalLlmEngine {
    fun isReady(): Boolean
    fun generate(prompt: String, system: String, config: LlmGenerateConfig): LocalCodeResult
}
```

Android implementation: `AndroidLiteRtLmCodeGenerator` implementing `LocalCodeGenerator` **or**
delegating from `AndroidLocalCodeGenerator` when `local-gemma-4-e2b-v1` is selected.

**Engine lifecycle:**

- One `Engine` per pack id, scoped to generation job (not Application singleton)
- `OrtSessionCache`-style guard: `LiteRtLmEngineCache` with `enterInference` / `leaveInference`
- Reuse rc16 pattern: never close engine from `onTrimMemory` while inference active

### L1.2 Pack publish

1. Script: `scripts/assemble-local-gemma4-pack.py` — download from HF `litert-community`, verify
   sha256, write `pack.json` + `config.json` (`runtime: litert-lm`, backend hints)
2. Upload to HF packs repo (same bucket as `local-gemma-v1`)
3. Manifest entry with `minRamMb: 8192`, `totalBytes: ~2.6 GB`

Keep **`local-gemma-v1`** (MediaPipe) as fallback until L1 verified on Pixel 9 — then mark
deprecated in catalog.

### L1.3 Catalog + picker

`LocalModelCatalog.kt`:

```kotlin
LocalModelEntry(
    id = "local-gemma-4-e2b-v1",
    displayName = "Local Gemma 4 E2B (code)",
    description = "LiteRT-LM on-device — Gallery-class Gemma 4 for Code Studio.",
    capability = AiCapability.CODE,
    packId = "local-gemma-4-e2b-v1",
    approxSizeLabel = "~2.6 GB",
    runnable = false, // flip true when pack hosted + engine wired
    ...
)
```

`AppSettings.setLocalGenerator(CODE, id)` — same pattern as `local-sdturbo-v1`.

### L1.4 GenerativeCloudService wiring

No change to cloud fallback order. When user selects local Gemma 4:

1. `localCode.isReady()` → LiteRT-LM pack installed
2. `generate()` on `Dispatchers.Default` (already done rc16)
3. Emit `GenerativeState.Running` with honest stage: `"Loading Gemma 4…"`, `"Generating…"`

### L1.5 Deprecate MediaPipe

After L1 DoD on Pixel 9:

- Remove `com.google.mediapipe:tasks-genai` if no other references
- Keep `local-gemma-v1` manifest row as `"legacy"` or remove in rc+2

**L1 DoD:**

- [ ] `local-gemma-4-e2b-v1` downloadable from Model packs
- [ ] Code Studio offline returns coherent Kotlin/code on Pixel 9
- [ ] Cold load + generate benchmark logged (extend `docs/BENCHMARKS.md` LiteRT-LM section)
- [ ] No ANR; engine closed after job; trim-safe
- [ ] Handshake lists Code Studio when pack verified

---

## Part L2 — Vision assist (“Ask Image” for fashion)

### L2.1 Product surface

**Not** a replacement for Image Create. New assists only:

| Surface | Input | Output |
|---------|-------|--------|
| Create composer | Reference photo + “Describe fabric” | Text appended to prompt or side panel |
| Try-on casting | Garment photo | Category / color / style tags (ATR supplement) |
| Wardrobe | Saved look thumbnail | Short listing description |

UI: reuse `LiveGenConsole` log pattern; no new visual design system.

### L2.2 Engine

- Pack: `local-gemma-4-e2b-it-vision-v1` (multimodal `.litertlm` from litert-community)
- API: LiteRT-LM multimodal API (image bytes + text prompt) — follow Gallery `AskImage` source
- Gate: `minRamMb: 10240`; show honest “heavy model” warning in picker

### L2.3 Integration points

```kotlin
interface LocalVisionAssist {
    fun isReady(): Boolean
    fun describeImage(imagePath: String, question: String): LocalAssistResult
}
```

Call from:

- `GenerativeViewModel` optional pre-step before cloud/local image gen (user toggle: “Analyze reference”)
- Try-on garment picker (optional “Auto-describe” chip)

**L2 DoD:**

- [ ] Offline describe works on garment JPEG from gallery
- [ ] Does not block try-on ORT path
- [ ] Toggle off by default; no cloud call

---

## Part L3 — Audio Scribe (optional)

Gallery’s **Audio Scribe** = speech-to-text / translate, not TTS.

Lookbook mapping:

- New sub-mode in Audio Studio: **Transcribe** (mic or file → text)
- Or: feed transcript into TTS / cloud audio chain

Pack: litert-community audio model (TBD — verify Gallery’s current default in
[`google-ai-edge/gallery`](https://github.com/google-ai-edge/gallery) `models` config).

**L3 DoD:** Record 30s clip offline → text in Result pane; no ANR.

**Priority:** after L1 + L2 — Lookbook’s Audio value today is **speak**, not transcribe.

---

## Part L4 — FunctionGemma / local tools (optional)

Gallery **Mobile Actions** uses FunctionGemma 270M for device-local tool calls.

Lookbook mapping — wire to existing **studio assists** without cloud:

| Tool | Action |
|------|--------|
| `set_backdrop` | Maps to `TryOnViewModel.setBackdrop` |
| `set_engine_tier` | Maps to `AppSettings` Lite/Pro/Auto |
| `append_prompt_clause` | Maps to composer text |

Use LiteRT-LM Tool Use APIs ([overview](https://developers.google.com/edge/litert-lm/api_overview)).

**Risk:** high QA burden; defer until L1 stable.

**L4 DoD:** One demo tool works offline in debug build; disabled in release until reviewed.

---

## Device gates & honesty

| Model | Approx size | minRamMb | Pixel 9 (12 GB) | Notes |
|-------|-------------|----------|-----------------|-------|
| Gemma 3 1B (legacy) | 530 MB | 6144 | ✅ | MediaPipe — deprecate |
| Gemma 4 E2B | 2.6 GB | 8192 | ✅ CPU/GPU | Gallery default class |
| Gemma 4 E4B | ~5+ GB | 12288 | ⚠️ tight | Offer only if free RAM > 4 GB |
| Vision E2B | +multimodal | 10240 | ✅ | Slower; label in picker |
| FunctionGemma 270M | small | 4096 | ✅ | L4 only |

`ModelPackManager.deviceMeets()` already checks `minRamMb` — extend catalog entries.

**Backend selection (honest UI):**

- Default: `Backend.CPU()` on Tensor Pixels until GPU plugin verified
- Opt-in: `Backend.GPU()` in Settings → Engines (advanced)
- NPU: experimental; hide until Google documents stable Tensor SDK for LiteRT-LM

Show in Model Health / picker: `"On-device · CPU"` not `"NPU accelerated"` unless proven.

---

## File / module map (expected touch points)

| Area | Files |
|------|-------|
| Engine | `shared/.../engine/litert/LiteRtLmEngine.kt`, `LiteRtLmEngineCache.kt` |
| Code | `AndroidLocalCodeGenerator.kt` → delegate or replace |
| Contracts | `LocalCodeGenerator.kt`, new `LocalVisionAssist.kt` |
| Packs | `scripts/assemble-local-gemma4-pack.py`, HF manifest, `PackIntegrityChecker` |
| Catalog | `LocalModelCatalog.kt`, `PackHandshake.kt` |
| Settings | `AppSettings.kt` local generator id |
| UI | `UnifiedStudioPane.kt`, optional assist chip |
| Benchmark | `OnDeviceOrtBenchmarkTest.kt` or `LiteRtLmBenchmarkTest.kt` |
| Docs | `docs/BENCHMARKS.md` LiteRT-LM section |
| Gradle | `libs.versions.toml`, `composeApp/build.gradle.kts` |

---

## Implementation order (strict)

```
L0 Kotlin 2.3 + spike
  → L1 Gemma 4 E2B Code pack + engine + catalog
    → L2 Vision assist
      → L3 Audio Scribe (if product wants)
        → L4 FunctionGemma tools (experimental)
```

**Do not start L2 until L1 Pixel 9 benchmark is committed.**

Parallel safe work: pack upload scripts, manifest schema, catalog stubs (`runnable = false`).

---

## Non-negotiable invariants (unchanged)

- AUTO tier never selects cloud
- Free-tier only for cloud models
- Watermark + EXIF on generated images
- No secrets in release builds
- Pro try-on requires `lite-v1`
- Engines emit failure states, never throw for expected failures
- ONNX try-on / tiny-SD paths remain independent of LiteRT-LM

---

## Definition of done (program level)

- [ ] LiteRT-LM is a first-class runtime alongside ORT (documented in Help + Model packs)
- [ ] At least one Gemma 4 `.litertlm` pack ships on HF manifest
- [ ] Code Studio runs Gemma 4 offline on Pixel 9 with benchmark numbers in `docs/BENCHMARKS.md`
- [ ] MediaPipe Code path deprecated or removed
- [ ] Vision assist available as optional offline feature (L2)
- [ ] No regression to rc16 stability (ANR, native kill, LCM image quality)

---

## References

- [Google AI Edge Gallery (GitHub)](https://github.com/google-ai-edge/gallery)
- [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview)
- [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android)
- [litert-community models](https://huggingface.co/litert-community)
- [Gemma 4 E2B litert-lm card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- Lookbook today: `AndroidLocalCodeGenerator.kt`, `LocalModelCatalog.kt`, `ModelPackManager.kt`

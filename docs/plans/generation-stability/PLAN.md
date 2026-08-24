# Generation Stability — audit + iterative execution plan

> **Baseline:** v2.9.16 (versionCode 41). Source audited at `06a24f1`.
> **Status (2026-08-22):** M1–M3 + M2 UI + blank-frame **done** through v3.0.5–3.0.7.
> M4 code path (`AndroidLocalImageGenerator`) **shipped**; **weights still pending**.
> M5 harness scripts + catalog matrix **done**; Pixel baseline PNGs still need a device.
> Findings A–O below are the original audit — many are **superseded** by later releases; use the Status header + `../stable-release/` as the live checklist.
> Read-only review — no source file was changed by this document.
>
> **Overlaps** [`../lookbook-v3-followup/`](../lookbook-v3-followup/) on model health, quality
> packs, and CI gates. Per `docs/plans/README.md`: implement once, mark done in both.

## Context

You hit repeated image-generation failures across app versions (11 screenshots, Aug 22):
`Unable to resolve host "black-forest-labs-flux-1-schnell.hf.space"`, `HTTP 400: Model not
supported by provider nscale`, `Hugging Face Space error: event: error data: null`,
`Only free Hugging Face Spaces are supported for images`, and a Cloud-usage ledger reading
**"1 requests · 0 ok · 1 failed"** while Model Health simultaneously claims every model is
**"Ready"**.

You asked for: a deep code review / audit of this codebase (no edits — Cursor is working on
it), and a single copy-paste **prompt for Cursor** that drives an iterative loop: refactor →
build → fix failures → auto-test → visual-test → review → plan next → repeat, until image
generation is stable and multiple local models work.

Decisions you confirmed:
- **Local scope:** wire the app *and* ship a local image-gen pack (offline Create Studio).
- **Test target:** real Pixel 9 over adb.
- **Live probes:** mock-first; live HF probes only at milestone gates, quota-exhausted = WARN.

## Revalidation against `main` (done)

Audited `origin/main` @ `06a24f1` — v2.9.16, versionCode 41, four commits ahead of the branch
I first read. Cursor's v2.9.16 work is **UI, navigation, and diagnostics only**:

| Changed on main | Effect on this audit |
|---|---|
| `NewsChatScreen.kt`, `HomeScreen.kt`, `VestraNavHost.kt` (nav consolidation) | none |
| `ModelPickerSheet.kt` — now groups models by platform (Hugging Face / Groq / OpenRouter) | M2's health-badge work should **extend** this grouping, not replace it |
| `DiagnosticsHook.kt`, `DiagnosticsExport.kt`, `DiagnosticsScreen.kt`, engine wiring | useful stage timings; see two new findings below |

**Every file in the generation stack is byte-identical to what I audited** — verified with
`git diff HEAD origin/main` per file: `GenerativeCloudService`, `CloudModelContracts`,
`CloudModelRouting`, `HfGradioClient`, `HfInferenceClient`, `SpacePayloads`,
`FreeCloudDiscovery`, `CloudOutputValidator`, `CloudModelCatalog`, `AppSettings`,
`LocalModelCatalog` — all `UNCHANGED`. **Findings A–O below all still stand on `main`.**

Two additions from the v2.9.16 code:

- **P.** `DiagnosticsHook` (`shared/.../diagnostics/DiagnosticsHook.kt`) is a mutable global
  singleton holding a single `activeTryOn` builder. Two concurrent runs clobber each other's
  timings, and `completeTryOn` can close the wrong run. It also adds an **eighth**
  `System.currentTimeMillis()` call to commonMain (finding M).
- **Q.** `versionName` is `2.9.16` but `CHANGELOG.md` on main stops at `2.9.15` — no entry for
  the shipped release.

---

## What the codebase is

Kotlin Multiplatform Android app, `com.zakir.vestra` / "The Lookbook". 138 Kotlin files.

| Module | Role |
|---|---|
| `composeApp/` | Compose UI — studios (image/video/code), settings hub, packs, usage, diagnostics |
| `shared/commonMain` | Catalogs, routing, contracts, Gradio + Inference clients, settings, usage, safety |
| `shared/androidMain` | ONNX engines: `LiteEngine` (compositor), `DiffusionEngine` (SD1.5+ControlNet+IP-Adapter) |
| `ml/` | Python export/quantize/manifest tooling for HF packs |
| `scripts/` | `probe-models.py`, `integration-*.py`, `benchmark-*.py`, `visual-verify.sh` |

Cloud path: `GenerativeViewModel` → `GenerativeCloudService.generateImage()` →
`CloudModelRouting.fallbackChain()` → per candidate either `HfGradioClient.predict()`
(Spaces) or `HfInferenceClient.textToImage()/imageToImage()` (router.huggingface.co) →
`GradioOutput.extractMediaRef()` → `AndroidCloudIo.downloadResult()` →
`CloudOutputValidator` → Wardrobe.

---

## Audit findings (evidence-backed, ranked)

### A. Fallback chain is broken — `continue` targets the wrong loop  ⭐ root cause
`GenerativeCloudService.kt:66-165`. Two nested loops: outer over model candidates, inner over
3 prompt variants. Every `continue` in the catch block (`:159`, `:161`, `:162`) sits **inside
the variant loop**, so "jump to Inference Providers next" actually retries *the same dead
model with a softer prompt*, three times. The comment at `:158` states the intended behaviour;
the code does not do it. This is why `InstructPix2Pix (HF Inference) HTTP 400: Model not
supported by provider nscale` surfaces as a terminal failure instead of falling through.

Also: when no `continue` condition matches, control falls out of the `catch` and loops to the
next variant anyway — so an unclassified error burns 3 attempts per model with no benefit.

### B. Per-candidate preflight is skipped for images only
`generateCode` (`:253-254`) and `generateVideo` (`:337-338`) call
`CloudModelContracts.preflightOrNull(candidate)` + `requireKeyIfNeeded(candidate)` **inside**
the candidate loop. `generateImage` calls both only for the *selected* provider (`:56-57`).
Result: an HF-token-requiring candidate is attempted with no token and throws
`"Add your HF token in Settings…"` (`:87`) from mid-chain, attributed to the wrong model.

### C. Model health is a hand-maintained static table
`CloudModelContracts.kt` — "Live Gradio / chat contracts audited against Space `/info`
(Aug 2026)". `ModelSupportLevel` never changes at runtime. There is **no observed-health
tracking, no cooldown, no circuit breaker**. A Space that failed 20 seconds ago is still
labelled `Ready`, still sorted first by `CloudModelRouting.modelPriority()` (`:72-79`), and
still tried first. Your screenshots show exactly this: `MODEL HEALTH · IMAGE GEN · FLUX.1
Schnell · Ready` directly above `RECENT · FLUX.1 Schnell · failed`.

### D. Worst-case latency per model ≈ 9 minutes, no global deadline
`HfGradioClient.predict()` (`:39-78`): `wakeRetries+1` = 3 rounds × 2 credentials (anonymous,
then token) × `maxPolls` 90 × `pollDelayMs` 2s = up to ~6 attempt-cycles of 3 min each, plus
`wakeDelayMs` 12s between rounds. Multiply by the candidate chain. Nothing bounds total
wall-clock; the user just watches a spinner.

### E. Errors are classified by substring-matching human prose, in three places
`HfGradioClient.formatGradioError()` builds an English sentence → `GenerativeCloudService`
re-matches substrings of it (`isAccountQuotaExhausted`, `isNetworkError`,
`isBrokenInferenceRoute`, `:188-222`) → `CloudModelContracts.friendlyFailure()` matches *again*
(`:274-324`). Any wording change silently breaks routing. This is why raw
`Unable to resolve host "timbrooks-instruct-pix2pix.hf.space"` reached the UI. There is no
typed error model.

### F. Discovery can surface models that are guaranteed to fail
`FreeCloudDiscovery.discoverHf()` mints `hf-disc-*` providers (`:155-173`) with
`platform = HF_INFERENCE`, `apiName = "inference"`. They have no `CloudModelContract`, so
`forProvider()` falls back to a synthetic READY/DEGRADED entry (`CloudModelContracts.kt:226`),
and `HfInferenceClient.routesFor()` else-branch (`:298`) sends them to **nscale** — which
returns the exact `400 Model not supported by provider nscale` in your screenshots. Likewise
`SpacePayloads.forImageGen` else-branch (`:34`) hands any unknown Space a 1-argument payload
that pydantic rejects with an empty `event: error / data: null`.

### G. `requiresSpace()` contradicts itself; video hard-blocks the fallback chain
`CloudModelCatalog.kt:31-34` declares IMAGE_GEN "requires Space", but
`AppSettings.usableFor()` (`:127-136`) accepts HF_INFERENCE for the same capability. The stale
invariant still lives in `generateVideo` as `require(provider.platform == CloudPlatform.HF_SPACE)`
(`:329-331`), which throws **before** `fallbackChain` is built. The 5:14 screenshot
(`stable-diffusion-xl-base-1.0: Only free Hugging Face Spaces are supported for images`) is
this legacy path.

### H. Token save silently re-routes image gen — twice
`AppSettings.setHfToken()` (`:97-103`) migrates `flux-schnell-hf` → `flux-schnell-inference`;
`migrateProviderId()` (`:234-242`) does it again on next launch. Inference burns *monthly
credits* (402 when spent) rather than daily-refilling ZeroGPU. Saving a token can make image
gen strictly worse, and the 7:56 screenshot shows `IMAGE GEN · SDXL Turbo (HF Inference)`
selected — a model the user never picked.

### I. Output validation is nearly a no-op
`CloudOutputValidator.MIN_IMAGE_BYTES = 64`. A valid 1×1 PNG is 68 bytes. Header-sniffing only
— a fully black or blank frame passes as success and lands in the Wardrobe.

### J. Dead / duplicated catalog weight
`deepseek-r1-free-or` (`CloudModelCatalog.kt:318-331`) has an identical endpoint to
`openrouter-free`; both render in pickers. `isMonthlyCreditsExhausted()` is defined in
`GenerativeCloudService` but never called from `generateImage`, so a 402 doesn't skip the
remaining HF_INFERENCE candidates.

### K. Concurrency
`HfGradioClient.prefixCache` (`:36`) is a bare `mutableMapOf` mutated from concurrent
coroutines. `ModelPackManager` uses JVM `synchronized(Any())` in **commonMain** (`:44`).

### L. No local image generation exists at all
`LocalModelCatalog`: `local-image-gen-planned` is `runnable = false`, `packId = null`. Create
Studio is 100% cloud — so any HF outage is a total outage. **But most of a txt2img stack is
already shipped for Pro try-on**: `LatentCodec`, `DdimScheduler`, `ClipTokenizer`,
`UnetRunner`, `OrtGraph`, `SdControlNetPipeline` (`shared/androidMain/.../engine/pro/`).
Text-encoder → UNet → DDIM → VAE-decoder is ~80% of the code already present.
`birefnet-v1` / `realesrgan-v1` are wired in `QualityPostProcessor` but the packs were never
exported/uploaded, so both read `runnable = false`.

### M. KMP portability already broken
`System.currentTimeMillis()` appears in **7 commonMain files** (`UsageLedger`,
`ChatRepository`, `NewsRepository`, `ModelPackManager`×3, `CloudEngine`, `RunDiagnostics`),
contradicting `docs/ARCHITECTURE.md` ("commonMain kept free of Android imports") and
`docs/IOS_PORT.md` ("commonMain stays pure Kotlin so the existing code compiles for iOS
unchanged"). Adding an iOS target today fails to compile.

### N. minSdk 35 contradicts the docs
`composeApp/build.gradle.kts` and `shared/build.gradle.kts` both set `minSdk = 35`
(Android 15). README says "Lite runs on Android 8+" / "All phones";
`docs/LOCAL_MODEL_RESEARCH.md` says "Android 8+".

### O. Test + CI gaps
20 unit-test files with decent cloud coverage, but **no test reproduces finding A** —
`GenerativeCloudServiceTest` doesn't assert candidate advancement. `.github/workflows/android-ci.yml`
runs unit tests + lint + assemble only; `scripts/run-integration-tests.sh` (the real gate,
including `integration-local-models.py`, `integration-edge-cases.py`, `probe-models.py`,
benchmarks) is **not wired into CI**. `visual-verify.sh` captures 10 deep-linked screenshots
but nothing compares them to a baseline. No ONNX-graph shape tests for the Pro pack contract.

---

## Deliverable — copy-paste this into Cursor

Everything below the line is the prompt. It is self-contained: findings, milestones, loop
protocol, and gates are all inline so Cursor doesn't re-derive them.

---

````markdown
# MISSION — The Lookbook (com.zakir.vestra): make generation stable, local-first, and provably tested

You are working in the `Agentic-AI` repo (KMP Android app "The Lookbook"), branched from
`main` @ `06a24f1` (v2.9.16, versionCode 41).
Work autonomously in a **continuous loop** until every milestone gate below is green.
Do not stop to ask permission between iterations. Commit after every green gate.

## Ground truth: what is broken today

Users see image generation fail with, verbatim from device screenshots:
- `Unable to resolve host "black-forest-labs-flux-1-schnell.hf.space": No address associated with hostname`
- `InstructPix2Pix (HF Inference): HTTP 400: Model not supported by provider nscale`
- `Qwen Image Edit: Hugging Face Space error: event: error data: null`
- `stable-diffusion-xl-base-1.0: Only free Hugging Face Spaces are supported for images`
- Cloud usage ledger: `1 requests · 0 ok · 1 failed`, while Model Health says every model is `Ready`.

An audit already located the causes. Verify each before fixing — do not trust this list blindly,
but do not re-derive it from scratch either.

| # | Finding | Where |
|---|---|---|
| A | **`continue` in the image fallback chain targets the prompt-variant loop, not the candidate loop** — a dead model is retried 3× with softer prompts instead of advancing to the next model | `shared/.../cloud/GenerativeCloudService.kt:66-165` |
| B | Per-candidate `preflightOrNull` + `requireKeyIfNeeded` run for code/video but **not** for images | same file, `:56-57` vs `:253,:337` |
| C | `ModelSupportLevel` is a static hand-audited table — no runtime health, no cooldown, no circuit breaker | `cloud/CloudModelContracts.kt` |
| D | Worst case ~9 min per model (3 wake rounds × 2 credentials × 90 polls × 2s), no global deadline | `cloud/HfGradioClient.kt:39-78` |
| E | Error classification is substring-matching English prose across 3 layers | `HfGradioClient.formatGradioError` → `GenerativeCloudService:188-222` → `CloudModelContracts:274-324` |
| F | `hf-disc-*` discovered models have no contract and no payload → nscale 400 / empty Gradio error | `cloud/FreeCloudDiscovery.kt:155-173`, `HfInferenceClient.kt:298`, `SpacePayloads.kt:34` |
| G | `requiresSpace()` contradicts `AppSettings.usableFor`; `generateVideo` hard-`require`s HF_SPACE before the chain builds | `CloudModelCatalog.kt:31`, `AppSettings.kt:127`, `GenerativeCloudService.kt:329` |
| H | Saving an HF token silently re-routes image gen Space→Inference in two places; Inference burns monthly credits | `settings/AppSettings.kt:97,234` |
| I | `MIN_IMAGE_BYTES = 64` — a 1×1 PNG (68 B) or a blank frame passes as success | `cloud/CloudOutputValidator.kt` |
| J | `deepseek-r1-free-or` duplicates `openrouter-free`; `isMonthlyCreditsExhausted()` unused in `generateImage` | `CloudModelCatalog.kt:318`, `GenerativeCloudService.kt:188` |
| K | `HfGradioClient.prefixCache` is an unsynchronized map; `ModelPackManager` uses JVM `synchronized` in commonMain | `HfGradioClient.kt:36`, `ModelPackManager.kt:44` |
| L | **No local image generation exists** — Create Studio is 100% cloud, so any HF outage is a total outage | `local/LocalModelCatalog.kt` |
| M | `System.currentTimeMillis()` in 8 commonMain files — iOS target cannot compile | `UsageLedger`, `ChatRepository`, `NewsRepository`, `ModelPackManager`, `CloudEngine`, `RunDiagnostics`, `DiagnosticsHook` |
| N | `minSdk = 35` (Android 15) contradicts README/docs claiming Android 8+ | both `build.gradle.kts` |
| O | `scripts/run-integration-tests.sh` is the real gate but CI runs only unit tests + lint + assemble | `.github/workflows/android-ci.yml` |
| P | `DiagnosticsHook` is a global singleton with one `activeTryOn` builder — concurrent runs clobber each other; `completeTryOn` can close the wrong run | `shared/.../diagnostics/DiagnosticsHook.kt` (new in v2.9.16) |
| Q | `versionName = "2.9.16"` but `CHANGELOG.md` stops at 2.9.15 — no entry for the shipped release | `CHANGELOG.md`, `composeApp/build.gradle.kts` |

**Verified on `main` @ `06a24f1` (v2.9.16):** the v2.9.16 commits changed UI, navigation, and
diagnostics only. `GenerativeCloudService`, `CloudModelContracts`, `CloudModelRouting`,
`HfGradioClient`, `HfInferenceClient`, `SpacePayloads`, `FreeCloudDiscovery`,
`CloudOutputValidator`, `CloudModelCatalog`, `AppSettings`, and `LocalModelCatalog` are all
unchanged. Findings A–O are live, not already-fixed. Re-confirm each with a failing test
before you touch it.

## Non-negotiable invariants — never weaken these

1. **AUTO never selects cloud.** `EngineRouter.resolve` picks Pro→Lite only. The Play
   data-safety declaration depends on it (`docs/ARCHITECTURE.md`, `docs/PLAY_COMPLIANCE.md`).
2. **Free-tier only.** `CloudModelCatalog.init` rejects any paid platform or non-zero cost.
   Never add Replicate / fal.ai direct / any keyed paid host.
3. **Every generated image keeps its watermark + EXIF provenance tag** (`Watermark`,
   `AndroidCloudIo.stampProvenance`). The `store` flavor must keep `APPLY_WATERMARK = true`.
4. **No secrets in release builds.** `DEFAULT_*_TOKEN` stays `""` for release.
5. **Pro try-on requires `lite-v1`** for human parsing. Do not break that dependency.
6. Engines emit `GenerationState.Failed(TryOnError)` — they never throw for expected failures.

## Milestone progress (@ v3.0.1)

- [x] **M1** Typed failures + correct fallback (A, B, E partial, G, J partial)
- [x] **M2** Live model health + output validation + generation deadline
- [x] **M3** Self-healing Space contracts (GradioSchemaClient + discovery allowlist)
- [~] **M4** Local image generation pack (scaffold `local-sdturbo-v1` + export script; weights/HF publish pending)
- [x] **M5** Test + visual harness (compare mode, verify-all-models, e2e-matrix scripts)
- [x] **M6** Cleanup and portability (EpochClock, DiagnosticsHook handles, token migration, CHANGELOG; minSdk docs agree on 35)

## Milestones

Work them in order. Each has a hard gate. Do not start N+1 until N's gate is green.

### M1 — Typed failures + correct fallback  (fixes A, B, E, G, J, K)
- Introduce `sealed interface CloudFailure` in `shared/commonMain/.../cloud/`, with at minimum:
  `Offline`, `QuotaExhausted(scope: ACCOUNT|MODEL)`, `CreditsExhausted`, `AuthRejected`,
  `RouteUnsupported`, `SchemaRejected`, `Busy`, `Waking`, `Timeout`, `SafetyBlocked`,
  `BadOutput`, `Unknown(raw)`. Each carries `retryable: Boolean` and `advanceModel: Boolean`.
- Classify **once**, at the client boundary (`HfGradioClient`, `HfInferenceClient`, `LlmClient`).
  Downstream code branches on the type, never on `message.contains(...)`.
  `CloudModelContracts.friendlyFailure` becomes a pure `CloudFailure → user-facing String`.
- Restructure `generateImage` so model advancement and prompt-variant retry are **separate,
  explicitly labelled loops** (`modelLoop@` / `variantLoop@`) — or better, extract
  `runCandidate(candidate, variants): Result<...>` and let the outer loop decide from the
  typed failure. Delete the misleading comments once behaviour matches them.
- Move `preflightOrNull` + `requireKeyIfNeeded` **inside** the candidate loop for images, and
  skip (don't throw on) candidates whose key is missing.
- Delete the `require(platform == HF_SPACE)` in `generateVideo`; fix or delete `requiresSpace()`
  so it agrees with `AppSettings.usableFor`.
- Wire `isMonthlyCreditsExhausted` into the image path: a 402 skips all remaining HF_INFERENCE
  candidates and jumps to Spaces.
- Remove the `deepseek-r1-free-or` duplicate (keep an id-migration in `migrateProviderId`).
- Make `prefixCache` concurrency-safe; replace `synchronized` in commonMain with a
  `kotlinx.coroutines.sync.Mutex`.

**Gate M1:** `./gradlew :shared:testDebugUnitTest :composeApp:testSideloadDebugUnitTest` green,
plus new tests that fail on the old code:
- image chain **advances to the next model** on `RouteUnsupported` / `SchemaRejected` /
  `QuotaExhausted(ACCOUNT)` and does **not** waste prompt variants on it;
- image chain **retries variants** only on `SafetyBlocked`;
- a candidate with a missing required key is skipped, not thrown;
- `Offline` short-circuits the whole chain immediately (no per-model retry);
- every `CloudFailure` variant maps to a user string with no raw hostname or stack text in it.

### M2 — Live model health + budget  (fixes C, D, I)
- Add `ModelHealthTracker` (commonMain, persisted via `multiplatform-settings`): per-provider
  rolling success/failure, `lastFailureMs`, `cooldownUntilMs`, consecutive-failure count.
  Exponential cooldown on repeat failure (e.g. 30s → 2m → 10m → 30m, cap 1h).
- `CloudModelRouting` sorts by **effective** health = static `ModelSupportLevel` ∧ observed
  health; a model in cooldown drops to the back and is skipped entirely if a healthy
  alternative exists.
- Model Health / picker UI (`UsageScreen`, `ModelPickerSheet`, `SettingsScreen`) renders the
  live state — a model that just failed must **not** read `Ready`. Show
  `Ready · verified 2m ago` / `Cooling down · 8m` / `Degraded · 3 recent failures`.
  `ModelPickerSheet` already groups by platform as of v2.9.16 — **extend that grouping with the
  health badge and cooldown sort; do not rewrite the sheet.**
- Add a global generation deadline (default 120 s image, 300 s video) enforced with
  `withTimeout`, and cut `HfGradioClient` defaults to `wakeRetries = 1`, `maxPolls` derived
  from the remaining budget. Surface remaining time in the `Running` stage text.
- Strengthen `CloudOutputValidator`: raise `MIN_IMAGE_BYTES` to ~2 KB, parse actual dimensions
  from the PNG/JPEG/WebP header and reject < 64×64, and add a blank-frame check (reject when
  luminance variance ≈ 0) on the Android side where a `Bitmap` is available.

**Gate M2:** unit tests for cooldown math, health-aware ordering, deadline enforcement, and
the new validator (fixtures: 68-byte PNG, 1×1 JPEG, all-black 512×512 PNG, valid image).
Plus: on the Pixel 9 with airplane mode ON, an image generation fails in **< 5 seconds** with
"No internet connection" and no raw hostname — capture the screenshot as evidence.

### M3 — Self-healing Space contracts  (fixes F)
- Add `GradioSchemaClient`: `GET https://{host}/gradio_api/info` (fallback `/info`), parse
  `named_endpoints`, cache per host with a TTL in settings.
- `SpacePayloads` gains a schema-driven path: given the live parameter list, fill known roles
  (prompt / image / seed / steps / guidance / negative / width / height) and use each
  parameter's declared default for the rest. Keep the hand-tuned per-id payloads as the
  preferred path; use the schema path when the id is unknown **or** when a hand-tuned payload
  was just rejected with `SchemaRejected`.
- Delete the 1-argument `else ->` fallbacks in `forImageGen` / `forImageEdit` / `forVideo`;
  a model with neither a hand-tuned payload nor a live schema is `UNSUPPORTED`, not a guess.
- `FreeCloudDiscovery`: stop minting `hf-disc-*` providers that route to nscale blindly.
  Probe `router.huggingface.co/v1/models` for the provider actually serving the model and
  record it on the provider; drop any discovery that resolves to no usable route.
- `HfInferenceClient.routesFor` / `editRoutesFor`: drive from the resolved provider, and treat
  `Model not supported by provider` as `RouteUnsupported` → try the next provider route, then
  advance the model.

**Gate M3:** recorded-fixture tests (checked into `shared/src/commonTest/resources/`) of real
`/info` payloads for FLUX Schnell, Qwen Image Edit (fast mirror), InstructPix2Pix, OOTDiffusion,
LTX-Video — asserting the generated payload matches the hand-tuned one arg-for-arg. Plus one
fixture of an **unknown** Space proving the schema path produces a valid payload.
Then run `python3 scripts/probe-models.py --json /tmp/probe-m3.json` once and attach the report.

### M4 — Local image generation (offline Create Studio)  (fixes L)
This is the structural fix: HF outages must stop being total outages.
- **Reuse, do not rewrite.** `shared/androidMain/.../engine/pro/` already ships
  `OrtGraph`, `UnetRunner`, `LatentCodec`, `DdimScheduler`, `ClipTokenizer`,
  `SdControlNetPipeline`. A txt2img pipeline is text-encoder → UNet(CFG) → DDIM → VAE-decode:
  ~80% of it exists. Extract the shared scheduler/CFG loop rather than duplicating it.
- Add `LocalImageEngine` (androidMain) + a `LocalImageGenerator` interface in commonMain, and
  route `GenerativeCloudService.generateImage` through a **local-first** policy: when a local
  image pack is installed and ready, run it locally; use cloud only when the user explicitly
  selects a cloud model or no local pack is present. (Preserve invariant 1 — AUTO never
  reaches for cloud on its own.)
- Ship the pack: add `ml/export_image_gen_pack.py` producing `local-sdturbo-v1`
  (SD-Turbo or LCM-distilled SD1.5, INT8/FP16 ONNX, 512×512, 1–4 steps, ~1–1.5 GB) with a
  `config.json` mirroring `ProPackConfig`'s contract. Then
  `python3 ml/manifest_gen.py` + `python3 scripts/publish-packs.py` to
  `Iamzakirzr/vestra-packs`, and `python3 scripts/verify-manifest.py` to confirm.
- Flip `local-image-gen-planned` in `LocalModelCatalog` to a real runnable entry
  (`packId = "local-sdturbo-v1"`). Surface it in the image-studio model picker alongside cloud
  models, with a clear "on-device · works offline" badge.
- While you're in `ml/`: finish `birefnet-v1` and `realesrgan-v1` (export scripts already
  exist — `export_birefnet_pack.py`, `export_realesrgan_pack.py`), publish them, and flip both
  `runnable` flags. The `QualityPostProcessor` wiring is already there and inert.

**Gate M4:** on the Pixel 9, with **Wi-Fi and mobile data OFF**, Create Studio generates an
image from "Emerald abaya in a Lahore bazaar, soft afternoon light" and saves it to the Looks
gallery. Capture: the prompt screen, the progress screen, the result, and the gallery entry.
Record wall-clock and peak RSS in the diagnostics run record. Add
`scripts/benchmark-local.py` coverage for the new pack.

### M5 — Test + visual harness  (fixes O)
- Wire `scripts/run-integration-tests.sh` into `.github/workflows/android-ci.yml` as a job
  that runs everything not needing a device, and mark live-probe steps non-blocking
  (quota-exhausted = WARN, per the script's existing contract).
- Extend `scripts/visual-verify.sh`: add a `--baseline` / `--compare` mode that diffs each
  captured screenshot against `docs/screenshots/baseline/` with a perceptual threshold, writes
  a side-by-side contact sheet, and exits non-zero on regression. Add routes for every studio
  and every failure state.
- Add a `scripts/verify-all-models.sh` that, for each capability, iterates every catalog model,
  runs one generation, and writes a matrix report (model × outcome × duration × output SHA +
  thumbnail). Local models run offline; cloud models honour the quota-WARN rule.
- Add Compose UI tests for the composer: prompt empty → inline preflight message; model pill
  opens the picker; failure banner shows Retry/Dismiss and the retry re-runs.
- Add ONNX contract tests asserting each pack's graphs load and their input/output shapes match
  `config.json` (the existing `integration-local-models.py` is the right place).

**Gate M5:** CI green on a PR. `bash scripts/run-integration-tests.sh` green locally.
`scripts/verify-all-models.sh` produces a matrix where **every model is either OK or carries an
explicit, classified reason** — no `Unknown`, no raw hostnames, no empty Gradio errors.

### M6 — Cleanup and portability  (fixes H, M, N, P, Q)
- Give `DiagnosticsHook` a per-run handle instead of a global `activeTryOn` — either return an
  opaque token callers pass back to `completeTryOn`, or key active builders by run id. Add a
  test that two interleaved runs record two correct, non-overlapping records.
- Add the missing `2.9.16` CHANGELOG entry, and make releasing update `CHANGELOG.md` in the
  same commit as `versionName` from here on.
- Replace `System.currentTimeMillis()` in all 8 commonMain files with a `Clock` abstraction
  (`kotlin.time.Clock.System.now().toEpochMilliseconds()` or an injectable `TimeSource`) so an
  iOS target compiles. Then actually add `iosArm64`/`iosSimulatorArm64` to `shared` behind a
  host check and prove `commonMain` compiles.
- Remove the double token→provider migration in `AppSettings` (`setHfToken` + `migrateProviderId`).
  Replace with: never silently change a user's explicit selection. If the current selection is
  unusable, surface a one-tap "Switch to X" suggestion instead of rewriting the setting.
- Resolve the minSdk contradiction: either lower `minSdk` toward the documented Android 8+
  target (and fix whatever API usage blocks it), or update README + `docs/*` to state Android 15+.
  Decide with evidence, then make code and docs agree.
- Refresh `CHANGELOG.md`, `docs/PROJECT_STATUS.md`, `docs/LOCAL_MODEL_RESEARCH.md`,
  `docs/ARCHITECTURE.md`, `docs/PIPELINE.md` to match reality after M1–M5.

**Gate M6:** full build + all tests + lint green; `docs/` contains no statement contradicted
by the code.

## The loop protocol — run this every iteration

```
1. PLAN     Restate the current milestone's remaining work as a checklist. Pick ONE item.
2. PROVE    Write the failing test first (unit test, fixture test, or a device repro with a
            screenshot). It must fail against current HEAD. No test → no fix.
3. FIX      Smallest change that makes it pass. Reuse what exists — search before creating.
            Named to search for first: OrtGraph, UnetRunner, LatentCodec, DdimScheduler,
            ClipTokenizer, QualityEnhancer, CloudModelRouting, GradioOutput,
            CloudOutputValidator, RunDiagnostics, ModelPackManager.
4. BUILD    ./gradlew :shared:testDebugUnitTest :composeApp:testSideloadDebugUnitTest \
                     :composeApp:lintSideloadDebug :composeApp:assembleSideloadDebug
5. DEVICE   adb install -r the sideload debug APK on the Pixel 9.
            bash scripts/visual-verify.sh <serial> docs/screenshots/run-<n>
            Exercise the actual path you changed and capture it.
6. REVIEW   Re-read your own diff adversarially. Ask: does this weaken any invariant above?
            Does it add a substring match on an error message? Does it duplicate existing code?
            Does the failure path still emit GenerationState.Failed rather than throwing?
7. RECORD   Append to docs/AUDIT_LOG.md: iteration #, item, root cause, fix, evidence
            (test name + screenshot path), and what you learned that changes the plan.
8. NEXT     Update the milestone checklist. If the gate is green, commit, tag the milestone in
            the log, and advance. Otherwise loop to 1.
```

**Stop conditions.** Only stop when: all six gates are green; or you hit something that needs a
human decision (a paid API, a licence question, a destructive migration, a spend). If you stop,
say exactly what you need and what you already finished.

**Never do these:** skip/disable/quarantine a failing test to get green; call a failure a
"flake" without evidence it passed on the same commit; commit a token or keystore; add a paid
provider; weaken the watermark or the AUTO-never-cloud invariant; push an empty commit to kick CI.

## Commits and PR

- Branch: `claude/image-gen-code-review-audit-chrjon` (create from latest `main` if absent).
- One commit per green gate. Message: `fix(<area>): <what changed> — <milestone>`, body listing
  the finding IDs closed and the evidence.
- After the first push, open a PR (ready for review, not draft) titled
  `Stabilize image generation: typed failures, live model health, local image pack`.
  Body: table of findings A–Q with status, before/after screenshots, and the model matrix from
  `scripts/verify-all-models.sh`.
- Keep the PR green. On CI failure, reproduce locally first, then push one validated fix.

## Definition of done

- Image generation succeeds on the Pixel 9 with: (a) network + HF token, (b) network + no
  token, (c) **airplane mode** (local pack), (d) HF ZeroGPU allowance exhausted.
- No user-visible error contains a raw hostname, a stack trace, or `event: error data: null`.
- Model Health never reports `Ready` for a model that failed in the last cooldown window.
- Every catalog model appears in the verification matrix with a classified outcome.
- `docs/AUDIT_LOG.md` records every iteration with reproducible evidence.
````


# Honest drawbacks — The Lookbook

This is a plain list of real, current limitations — not a marketing page. Every item here is
either something verified directly against the code/models this session, or a known
architectural tradeoff the team should not pretend away. Update this file when a drawback is
actually fixed, not when it's merely reworded.

## Generation quality

- **On-device image generation uses small, distilled models (SD-Turbo / LCM-class, ~4 steps).**
  These trade quality for speed and offline capability. Output can look soft, low-detail, or
  under-conditioned compared to a full-step cloud diffusion model — this is an expected
  characteristic of the model class, not necessarily a bug. If a generation looks unusually
  flat or near-featureless, it is worth comparing against a fresh run before assuming a defect;
  we do not yet have an automated way to distinguish "expected 4-step softness" from a real
  regression (e.g. an execution-provider numerical difference) on a specific device.
- **NNAPI/hardware acceleration is a partial, per-graph offload, not all-or-nothing.** On real
  devices, only a subset of a model's graph nodes may be assigned to NNAPI, with the rest
  falling back to CPU inside the same inference session. This is normal ONNX Runtime behavior,
  but it means generation quality and speed can differ between devices, and even between runs
  on the same device if drivers change — we have not built tooling to detect or report which
  execution provider actually served a given generation.
- **No committed, on-device latency/quality benchmark exists yet.** Every "fast" or "good
  quality" claim in this repo's docs is either a desktop CPU measurement or an estimate, not a
  number captured from a real phone and checked into the repo with a date and device model.

## Reliability

- **Fixed this session: a prompt could leak from the News/Chat headline tap into whichever
  studio tab (Image/Video/Code/Audio) was active.** `HomeScreen.openNewsChat()` and
  `VestraNavHost`'s `onOpenNewsChat` callback both wrote the tapped headline's text into
  `GenerativeViewModel.prompt` — a single `StateFlow` every studio tab reads — even though
  `NewsChatScreen` already fills its own separate local chat-input state with that same text and
  never reads `GenerativeViewModel.prompt` at all. Both writes were dead code whose only real
  effect was overwriting whatever the user had typed in the currently-bound studio. Fixed by
  deleting both; per-tab prompt isolation itself (`GenerativeViewModel.bindStudio`/`StudioBag`)
  was already correctly implemented — this was a leak from *outside* that mechanism, not a flaw
  in it. Regression-covered in `appium/test_prompt_isolation.py` (unexecuted — see Testability).
- **GPU delegate initialization can fail on some devices for the local LiteRT-LM models**
  (confirmed via a real user device log: a GPU-backend engine-load failure with no fallback).
  This session added an automatic CPU fallback (`LiteRtLmEngine.initialize()`) so a failed GPU
  init no longer permanently blocks loading — but CPU is slower, and a device whose GPU
  delegate fails will silently run slower than one whose GPU delegate works, with only a debug
  log line noting which backend actually loaded.
- **Cloud generation depends on free-tier Hugging Face Spaces / Inference Providers**, which can
  be slow to wake, rate-limited, or occasionally serve schema/route errors outside this app's
  control. Typed failure classification and model-health-aware routing exist to route around a
  known-bad model, but a systemic HF outage still degrades cloud generation across the board.
- **Video and audio generation lag image and code generation in maturity** — they were built to
  the same typed-failure/health-tracking pattern, but have had less real-device exercise this
  session than the image path (which had two real device-reported bugs fixed and verified this
  cycle).

## Design/UX parity with the reference app (lookbookweb)

- **`docs/plans/lovable-parity-local-first/PLAN.md` is a large plan; a real slice has landed, not
  all of it.** Done: per-modality accent color tokens (partial rollout — only the studio header
  uses them so far), a derived radius scale, a press-lift micro-interaction on `GlassCard`,
  confirmation that cloud generation is off by default everywhere (it was already correct; only
  misleading "Cloud by default" copy in the studio subtitle was fixed to reflect that), a
  resumable-job "interrupted" banner for local generations killed mid-run, correlation-ID-first
  error messages for local failures, a storage-used rollup + device-requirement checklist in
  Model Packs, a single honestly-labeled Processing Mode card replacing the old cloud switch, a
  shared shimmer-loading component wired into the one real gap found, and version lineage for
  local generations (retries in the same studio tab chain as a discoverable history). **Not
  done:** the bottom-dock navigation pattern (deliberately deferred — needs a design decision,
  not a unilateral call), a voice-studio DSP layer (real-time meters/scope, latency
  auto-calibration — needs a device to verify `AudioRecord`/`Visualizer` behavior), a local
  safety/blur post-process (this app has no face/region detector to build one on top of — see
  the plan's own README for why that's a scope mismatch, not a skipped task), and Part D's
  planned real-model output-quality testing for code and audio. Treat any claim that this app
  "matches lookbookweb's design" as false until those close too — it currently matches on
  typography, several interaction/UX patterns, generation-lifecycle UX, and color-identity
  direction, not the complete UX described in the plan.

## Testability

- **Appium/UiAutomator visibility was added this session, not something the app shipped with.**
  `testTagsAsResourceId` is now enabled at the app root and the core generation flow — prompt
  input, model chip, assist toggle, send/stop, home tabs, every `GenerativeState` result card,
  the live generation console, retry/cancel, model-pack install/handshake buttons, and the
  model picker's per-model rows — now carry stable `testTag`s (see
  `composeApp/src/main/kotlin/com/zakir/vestra/ui/TestTags.kt`). Coverage is not yet
  exhaustive: Settings screens, Wardrobe/gallery browsing, and some secondary dialogs
  (report-content sheet, durable-storage prompt) do not yet carry tags. Extend `TestTags.kt`
  and its call sites incrementally as automation needs grow, rather than tagging speculatively
  ahead of an actual test.
- **A real Appium test suite now exists (`appium/`) but has never been run.** No Android device,
  emulator, or Appium server exists in the environment that authored it (verified directly: no
  `adb`, no `ANDROID_HOME`, no Appium binary) — every test in it is a first draft that needs a
  real run on a real device before it's trusted, per `appium/README.md`'s own honesty note. It
  covers: prompt isolation across studio tabs, local image/code/chat generation reaching a real
  terminal state, the image-edit/img2img entry point, and the Processing Mode card. Not yet
  covered: video/audio generation end-to-end, Model Packs install/handshake, Wardrobe history
  navigation, and anything about generation *quality* rather than "a result exists."

## Platform

- **iOS is not supported.** `shared/build.gradle.kts` does not declare an iOS target, and
  several commonMain files still call JVM-only APIs (e.g. `System.currentTimeMillis()`
  directly rather than through a `Clock` abstraction), so commonMain would not compile for iOS
  without further work.
- **`minSdk` is high (Android 15)** relative to some of the app's own documentation, which in
  places still describes broader device support. Treat any "works on Android 8+" style claim
  elsewhere in the docs as aspirational until the `minSdk` and the docs are reconciled.

## What "verified" means in this repo

Several rounds of this project's own audit history found status claims ("done", "fixed") that
did not hold up under direct code inspection — a UI control that changed no request payload, a
health tracker whose display function was never called, a benchmark harness with no actual
numbers. Nothing in this file should be read as more certain than what a `git grep`, a real
device run, or a passing test that fails on the old code can back up. When a drawback here is
closed, replace it with a one-line "closed in <version>, verified by <evidence>" note rather
than deleting it silently.

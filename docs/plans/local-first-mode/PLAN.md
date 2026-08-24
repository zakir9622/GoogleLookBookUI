# Local-first mode — hide try-on, global cloud toggle, on-device generation focus

> **Baseline:** v3.1.0-rc17 (`5903b0a`). Driven directly by Claude (not Cursor — user reports
> Cursor's usage is exhausted). Read this alongside
> [`../generation-transparency/`](../generation-transparency/) and
> [`../litert-lm-integration/`](../litert-lm-integration/), which this plan builds on.
> **Status:** Iteration 1 shipped (this PR). Iterations 2–3 scoped below, not started.

## Why this plan exists

Direct product feedback after using the latest release APK: the News refresh control gave no
feedback on tap, Try-on needs to come off entirely for now (temporarily, not deleted), the app
should default to **local-only** generation with cloud gated behind one explicit master toggle
(currently cloud is always available with no such gate), and the newly-shipped Google AI Edge
Gallery / LiteRT-LM models (Gemma 4 Code, vision, audio) are unexpectedly slow on-device. The
product focus going forward is **on-device image/video generation**, using Google's AI Edge
Gallery reference app as the efficiency bar.

## Iteration 1 — shipped in this PR (verified: compiles, full unit test suite green)

### Fixed: News refresh button gave no feedback
`NewsChatScreen.kt`'s refresh `IconButton` was correctly wired to `newsRepository.refresh()` —
the actual bug was that a tap gave **zero visible feedback**: no spinner, and if the network
call hung or failed silently there was nothing to distinguish "working" from "broken." Fixed:
the icon now swaps for a `CircularProgressIndicator` while `refreshing` is true, and the refresh
call is wrapped in `try/finally` so an unexpected exception can no longer leave `refreshing`
stuck `true` forever (it previously wasn't guarded at this call site, only inside
`NewsRepository.refresh()`'s per-feed `runCatching`).

### Try-on temporarily disabled, app-wide, without deleting anything
Try-on touches 11 UI files. Rather than literal comment-blocks scattered across all of them
(fragile, easy to leave half-done), a single flag now gates every entry point:
`HomeTab.TRY_ON_TAB_ENABLED = false` in `HomeScreen.kt`. Flipping it back to `true` is the entire
revert.
- `HomeTab.visible` filters the tab out of the pager/tab row; `fromRouteKey()`'s fallback no
  longer defaults to the hidden tab. (This required fixing a latent bug the filter would have
  introduced: page-index code was using raw `HomeTab.ordinal` values, which stop matching pager
  indices once a tab is filtered out of the list — replaced every such usage with
  `tabs.indexOf(...)`.)
- `SettingsCloudSection.kt`'s "CLOUD TRY-ON" model-picker row is removed from the capabilities
  list (the underlying `CloudCapabilityDropdown` composable is untouched, just not invoked).
- `VestraNavHost.kt`'s `WardrobeScreen` call no longer passes `onStartTryOn`, which removes the
  "Start try-on" CTA from the empty-wardrobe state (the parameter already defaulted to `null` —
  this is a net *removal* of a lambda, not new code).
- Native try-on engines, routes (`GARMENT`/`CASTING`/`PERSON`/`GENERATE`/`RESULT`), and
  `TryOnViewModel` are untouched and still compile — they're just unreachable from the UI now.

Not touched, deliberately: `LocalModelCatalog`'s try-on pack entries, `ResultScreen.kt`,
`UsageScreen.kt`'s per-capability model-health row, `WardrobeScreen.kt`'s gallery items tagged
as past try-on results. These are either historical-data displays (deleting them would delete the
user's own past results from view) or dead-but-harmless code paths that cost nothing left in
place and everything to reconstruct later. Revisit if "temporarily" becomes "permanently."

### Investigated: why LiteRT-LM (Gemma 4 / Google AI Edge Gallery) models feel slow

**Finding, not yet fixed — needs a decision before touching it:**
`AppSettings.preferLiteRtLmGpu` defaults to **`false`** (`AppSettings.kt:48`) — every LiteRT-LM
engine (Code, vision, audio-scribe, tools; all four wired through the same setting in
`VestraApp.kt`) runs on **CPU only** unless the user finds and flips a toggle buried in
Settings → Engines (`SettingsEnginesSection.kt:88-104`). A cold CPU load + first inference of a
2.6 GB Gemma 4 E2B model is genuinely slow — this matches the reported symptom closely.
Compounding it: **no UI anywhere distinguishes "cold-loading a multi-GB model for the first time"
from the app's generic busy/spinner state** — `LiteRtLmEngine.initialize()`'s timing
(`coldLoadMs()`) is captured and logged, but never surfaced to any composable. A user watching a
generic spinner for a long time with no explanation has no way to tell "this is normal for a
first run" from "this is stuck."

This is a real, verified code-level finding (confirmed via direct read of
`LiteRtLmEngine.kt`/`LiteRtLmEngineCache.kt`/`AppSettings.kt`/`SettingsEnginesSection.kt`), not a
guess — but it was **not fixed in this iteration**, because the two candidate fixes both carry
real risk without a device to test on:
- Flipping the GPU default to `true` could be faster, or could break on devices where the GPU
  delegate has coverage gaps for this graph shape — the same class of risk already documented for
  ONNX's NNAPI/QNN cascade in `generation-transparency/PLAN.md`. Don't flip a safety-conscious
  default blind.
- Adding a "loading model, this may take a minute" UI state is safe and should happen, but needs
  wiring `coldLoadMs()`/an in-progress signal up through `LocalCodeGenerator`'s synchronous
  interface into the Compose layer — a small, well-scoped iteration-2 task, not a same-PR
  addition next to a Try-on removal.

## Iteration 2 — next (not started): global "Enable cloud models" toggle

**Design, anchored to what already exists — don't build a parallel mechanism:**
`AppSettings.preflight(capability)` (`AppSettings.kt:246`) is already the single choke point
every studio calls before attempting a cloud generation — it currently checks network
availability, API-key presence, and per-model contract status, returning `PreflightResult.Ok` or
`.Blocked`. Add one more check at the top of it: a new `AppSettings.cloudModelsEnabled: StateFlow<Boolean>`
(default `false`, matching "local-only until toggled"), and if it's off, `preflight()` returns
`Blocked("Cloud models are off — enable them in Settings to use $capability via cloud.")`
regardless of what else is true. This closes the gate at the one place all five capabilities
(`TRY_ON` — moot while hidden, `IMAGE_GEN`, `IMAGE_EDIT`, `CODE`, `VIDEO`) already funnel through,
rather than adding per-studio checks that could drift out of sync.

**Settings UI:** one master switch at the top of the Cloud settings section
(`SettingsCloudSection.kt`), and the existing per-capability `CloudCapabilityDropdown` rows +
API-key fields (Groq/OpenRouter/HF) render **only when the master switch is on** — matches the
literal ask ("I want those in settings only for cloud based models"). When off, Settings should
say plainly that generation is local-only, not just hide the section with no explanation.

**What this does *not* need to touch:** `EngineTier`/`AiCapability`/`LocalModelCatalog` — the
local-vs-cloud selection machinery per capability already exists (confirmed in
`generation-transparency`'s prior audit: `AiCapability` + `LocalModelEntry.runnable` is already
the right axis). This is purely a gate in front of the cloud half of paths that already exist,
not new routing logic.

**Verification plan:** a unit test asserting `preflight()` returns `Blocked` for every capability
when the toggle is off regardless of network/key state, and `Ok`-eligible when on — cheap, JVM-only,
no device needed, and it's exactly the kind of test that would have caught earlier regressions
in this codebase's cloud-routing history if it had existed from the start.

## Iteration 3 — next (not started): studio tab cleanup + LiteRT-LM loading UX

- Apply the `generation-transparency` plan's already-shipped `LiveGenConsole`/timer pattern
  consistently across Image, Video, Code, and News-chat — check what's already using it
  (`TryOnViewModel._liveLog` is confirmed wired; verify whether `GenerativeViewModel`, which
  backs Image/Video/Code/Audio via `UnifiedStudioPane`, has the same log/timer or a separate,
  earlier mechanism — reconcile rather than duplicate).
- Wire a real "loading model — first run may take a while" state for LiteRT-LM cold loads
  (the iteration-1 finding above), sourced from `LiteRtLmEngine.coldLoadMs()`/an in-progress
  flag, not a generic spinner.
- Only after that: revisit whether `preferLiteRtLmGpu`'s default should change, informed by
  real user reports (or, if a device becomes available, `docs/BENCHMARKS.md`-style measured
  numbers per `generation-transparency/PLAN.md`'s A0 harness pattern) rather than guessed.

## Non-negotiable invariants (unchanged across every plan in this repo)

AUTO tier never selects cloud today, and the new master toggle makes that guarantee explicit and
user-controlled rather than implicit. Free-tier only for cloud models. Every generated image
keeps its watermark + EXIF provenance tag. No secrets in release builds. Try-on's `lite-v1`
dependency for Pro human-parsing is untouched — hiding the tab doesn't touch pack requirements.

## Verification for this PR

`./gradlew :composeApp:compileSideloadDebugKotlin` — clean, no new warnings in touched files.
`./gradlew :composeApp:testSideloadDebugUnitTest :shared:testDebugUnitTest` — full suite green,
no regressions from the `HomeTab` filtering change (ordinal→index fix specifically verified by
this, since a wrong index would have broken `SettingsTierSmokeTest`'s route-constant assertions
had they overlapped — they test a different `HomeTabRoute` object, so this needs a device or a
dedicated pager-index test in a future iteration to fully close the loop; noted honestly rather
than claimed as covered).

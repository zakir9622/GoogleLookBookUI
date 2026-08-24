# Lovable design/UX parity — local-first

Adopt what's genuinely worth adopting from the user's other app, `zakir9622/lookbookweb`
(Lovable-built web/PWA/Android studio) — its glass/spatial design system and its generation-
lifecycle UX (job status, resumability, lineage, diagnostics, storage management, a single
honest processing-mode setting) — applied only to this app's **local** generation surfaces.

Key finding that shapes the whole plan: lookbookweb's own image/video generation is cloud-only
(`CLOUD_ONLY_MODALITIES` in its `src/lib/providers.ts`, confirmed by its own test suite) — this
app already ships real on-device diffusion (tiny-SD/LCM + Bonsai Image 4B) that lookbookweb does
not have. So this plan borrows lookbookweb's *design and UX patterns*, not new generative
capability, and never routes anything to cloud that isn't already cloud-routed today.

See [`PLAN.md`](PLAN.md) for the full breakdown (Parts A–D), explicit out-of-scope items, and
open questions for the user before implementation starts.

## Status: implementation started (3.1.0-rc21)

- **A0 (color tokens) — partial.** Added four per-modality accent tokens (`VestraColors.Modality
  Image/Video/Code/Audio`) to `VestraPalette`, brass-family tints rather than lookbookweb's own
  hues (keeps the Loom Ink identity, per the plan's own instruction not to replace it), plus a
  derived `RadiusTokens` corner-radius scale. Wired into the Studio header label
  (`UnifiedStudioPane`'s `GlassSectionLabel`) so far — not yet propagated to every chip/progress
  accent the plan describes; extend call-by-call as those surfaces are touched next.
- **A1 (typography) — already done**, found already in place when this phase started: `Type.kt`
  already pairs Syne (display) with Outfit (body), matching lookbookweb's split exactly.
- **A2 (glass/spatial interaction) — partial.** `GlassCard` now has a subtle press-lift (scale to
  ~97% on press, spring back on release), gated by `rememberReduceMotion()` — lookbookweb's
  `press-3d`/`lift-3d` language ported at Compose-native cost. No 3D perspective tilt yet.
- **A3 (nav pattern) — not started, deliberately.** The plan itself flags this as needing an
  explicit design decision before coding (top tab/pager vs. a bottom dock + center Create
  action). Not decided unilaterally — per-tab session isolation is real, hard-won infrastructure
  built around the current pager, and a nav change is the one item in this plan big enough to
  risk regressing it without a decision.
- **B1 (resumable job state) — done.** `LocalJobStore` (Settings+JSON, same pattern as
  `RunDiagnostics`) records QUEUED/RUNNING/DONE/FAILED/CANCELLED per local generation. A row
  still RUNNING/QUEUED from a previous app process surfaces on Home as an "Interrupted" card
  with Dismiss — not a literal one-tap resume (ONNX/LiteRT sessions aren't checkpointable), just
  the honest memory that something didn't finish. Wired into image/video/audio/code; chat is
  intentionally excluded (its replies stream live, so they don't silently vanish the same way).
- **B3 (correlation-ID error UX) — done.** `RunDiagnostics.RunBuilder` exposes its `id` before
  completion; local-generation failure messages (image/video/audio/code, and local chat) now
  thread "(ref &lt;id&gt;)" so a failure on screen is look-up-able in Settings → Diagnostics.
  Cloud failures are untouched — `CloudFailure` already carries enough context.
- **B4 (storage/download management) — done.** PacksScreen now shows an aggregate "X GB used
  across N packs · Y GB free" header, and `PackStatus.INCOMPATIBLE` expanded from one terse line
  into a scannable RAM/Android-version/NPU checklist with real have/need numbers. No explicit
  pause distinct from cancel-and-resume — that already works today, just not reframed as "pause"
  (lower priority, not done this pass).
- **B5 (processing mode) — done.** Replaced the "Enable cloud models" `Switch` with a single,
  prominent Processing Mode card (On-device only / Cloud allowed) in the same plain-language
  framing lookbookweb uses. Honestly two states, not three: this app has no true "Auto" fallback
  router, and the card's own copy says so rather than implying a mode that doesn't exist.
- **B8 (shimmer loading) — done.** `ShimmerBlock`/`ShimmerRows` (gradient sweep, falls back to a
  static fill under reduced motion) wired into the one real gap found: News/Chat's headline list
  used to show blank space while the first refresh was in flight.
- **Appium/chat testability** — see `docs/DRAWBACKS.md` and `TestTags.kt`; the News/Chat window,
  the processing-mode card, and the interrupted-jobs banner all carry stable tags alongside the
  rest of the generation flow tagged in the prior phase.
- **B2 (version lineage) — done.** `WardrobeEntry.parentGenerationId` chains consecutive
  generations in the same studio tab as retries; the look-detail dialog shows a HISTORY section
  of earlier attempts, tap to view any of them. While wiring this, found and fixed a real,
  in-pattern bug: `WardrobeEntry.tier` was hardcoded to `CLOUD` for every Create Studio result
  regardless of how it was actually generated — the exact "Tier: CLOUD" mislabeling class already
  fixed for diagnostics in an earlier cycle, at a call site that fix didn't reach.
- **B7 (safety post-process) — reassessed, not attempted this pass.** The plan describes an
  "optional on-device blur/redact pass" — but this app has no face/region detector anywhere in
  its model catalog, and building or bundling one is a materially larger undertaking than "wire
  into the existing `QualityPostProcessor` insertion point" (which is model-pack-driven for
  upscale/matte-refine, not a fit for manual region redaction either). A user-drawn manual-blur
  tool is possible without a new model, but it's gesture/canvas UI this remote session cannot
  visually verify, so it isn't included here rather than shipped unverified. Scope this as its
  own follow-up once either a lightweight face detector is added to the local-model catalog, or
  a manual-region tool is explicitly requested and can be verified on a device.
- **Not yet started:** A3 (nav pattern — deliberately deferred, needs the user's decision), B6
  (voice studio DSP depth — real-time meters/scope, latency auto-calibration), and Part D's
  real-model output-quality testing for code/audio. B6 touches a live `AudioRecord`/`Visualizer`
  pipeline this session cannot verify without a device — treat as higher-risk than B1–B5/B8/B2
  and worth extra scrutiny before landing.

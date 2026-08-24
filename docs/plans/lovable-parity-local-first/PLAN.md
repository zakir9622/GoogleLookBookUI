# Lovable design/UX parity — local-first only

**Baseline:** v3.1.0-rc19, build #230 (`ceaa304f`, merged PR #68).
**Reference app:** [`zakir9622/lookbookweb`](https://github.com/zakir9622/lookbookweb)
("The Lookbook Studio" / "Lookbook Studio", live at https://lookbookweb.lovable.app), a
Lovable-built TanStack Start + React + Capacitor multimodal studio the user also owns.
**Status:** planning — this document, no source changes yet.

## Why this plan exists, and its one hard boundary

The user asked for "the exact same design and functionality" from lookbookweb, but drew an
explicit line: **only for on-device local generation — the core of this app.** That line is
easier to honor than it first looks, because of what the research below found: lookbookweb's own
code is self-auditing about a hard limit — `CLOUD_ONLY_MODALITIES = ["image", "video"]` in
`src/lib/providers.ts`, an `EDGE_SUPPORT` table asserting `image.onDevice === false` and
`video.onDevice === false`, and a dedicated passing test
(`tests/edge.test.ts`, `"is honest that image and video generation are cloud-only"`). Its own
`RELEASE_PLAN.md` lists **"Image and video generation are cloud-only; no on-device diffusion
yet"** as a known gap, with "On-device image generation once a small enough model is viable"
still on its *future* roadmap. Its local "video" (`src/lib/localVideo.ts`) is a canvas Ken-Burns
+ procedural-gradient recording via `MediaRecorder` — not a generative model.

**Net effect: on real local generative capability, this Android app is already ahead of
lookbookweb, not behind it.** We already ship real on-device diffusion (tiny-SD/LCM ONNX +
Bonsai Image 4B LiteRT, both text-to-image and img2img edit, both verified this cycle against
real published weights — see `CHANGELOG.md` 3.1.0-rc19), real on-device code generation across
four model families, and local video/audio/vision/transcription. So this plan does **not** chase
lookbookweb for generative capability. It chases it for two things it is genuinely stronger at:
**visual design system** and **generation-lifecycle UX** (job status, resumability, lineage,
diagnostics, safety, storage management) — then applies both exclusively to our already-superior
local generation surfaces. Where a lookbookweb feature only exists because it calls a cloud API
(image/video generation itself, the Lovable AI Gateway chat/code routes, Replicate/OpenRouter
fallbacks, Supabase-backed accounts), it is explicitly **out of scope** — porting it would
violate both the user's instruction and this repo's own standing invariant (AUTO tier never
selects cloud; see every prior plan in `docs/plans/`).

## Research basis

Two sources, both real, not assumed:
1. A full read-only pass over `/home/user/lookbookweb` (design tokens, every `src/routes/*.tsx`
   screen, every `src/lib/*` module touching local/edge/provider-routing logic, and four test
   files) — see the file-by-file citations throughout this doc.
2. This repo's current state as of `ceaa304f`: `Theme.kt` (Loom Ink palette), `HomeScreen.kt`
   (tab/pager nav), `PacksScreen.kt` (model management), `DiagnosticsScreen.kt`/
   `DiagnosticsExport.kt`, `AppSettings.kt` (`prefersLocal(capability)`), and this session's own
   local-generation work (streaming, `LiveGenConsole`, the sdturbo/Bonsai engines).

A live-app screenshot pass was attempted (Playwright + this session's proxy) and blocked by a
CONNECT-tunneling issue between headless Chromium and the sandbox's egress proxy (curl and
Python `requests` reach the same URL fine; raw urllib and Chromium's proxy path do not) — not
worth chasing further, because the source-level extraction below (exact OKLCH values, exact
Tailwind utility recipes, exact font choices) is *more* implementation-ready than a screenshot
would have been anyway. If a screenshot pass matters later, it needs to run from a normal
network path, not this session's proxy.

---

## Part A — Design system translation

### A0. Color system: OKLCH tokens → Compose

lookbookweb (`src/styles.css`, 478 lines, Tailwind v4 `@theme inline`) defines the whole palette
in OKLCH on `:root`/`.dark`, with a single `--radius: 1.5rem` base driving a derived `sm..4xl`
scale (`calc(var(--radius) ± N)`) — generous rounding (`rounded-2xl/3xl/4xl`) is the dominant
shape language, not Material's `4dp` defaults. Four **per-modality brand hues** —
`--brand-image` (blue/violet), `--brand-video` (orange), `--brand-voice` (magenta/pink),
`--brand-chat` (teal/green) — tint chip backgrounds and background "orbs" consistently across
home/library/studio screens, so each tool has a recognizable color identity at a glance.

Our `Theme.kt` (`VestraPalette`, Loom Ink: cool mist silk + brass thread on deep ink) already has
a comparable *structure* — a light/dark palette pair, a glass-surface family
(`glassFill`/`glassBorder`/`glassHighlight`/`glassShadow`), a single accent (brass `#9A7340` /
`#D4A85C`) — but no per-modality hue system and a single, fixed corner-radius approach per
component rather than a derived scale.

**Action:** keep Loom Ink's identity (the user has iterated on this brand across many prior
cycles — do not replace it), but adopt two structural ideas from lookbookweb's token system:
- Add four modality accent tokens to `VestraPalette` (Image/Video/Code/Audio — reuse or adapt the
  existing `AiCapability` set) derived as brass-family tints/shifts rather than lookbookweb's
  hues, so Create/Video/Code/Audio Studio headers, chips, and progress accents are each subtly
  distinguishable without breaking the single-accent Loom Ink identity.
- Add a derived radius scale (`RadiusTokens.sm/md/lg/xl` off one base) so cards, chips, and
  sheets stop each hand-picking `RoundedCornerShape` values ad hoc.

### A1. Typography

lookbookweb pairs **Syne** (`--font-display`, headings, tight tracking) with **Outfit**
(`--font-sans`, body) — a distinctive display/body split that gives headings real character
instead of the body font just set larger. Check `composeApp`'s current typography
(`VestraTypography`) for whether it already does a display/body split; if it's one family at
multiple weights, this is a concrete, low-risk visual upgrade: pick a Google Fonts pairing with
similar character (a geometric/display sans for headings + a warm humanist sans for body) bundled
as Compose downloadable/embedded fonts, applied only to `MaterialTheme.typography.headlineX`/
`displayX` styles so body text and dense UI (chips, labels) are unaffected.

### A2. The "glass/spatial" visual language and interaction language

lookbookweb's whole surface language is one Tailwind `@utility` layer worth studying directly:
`soft-card` (translucent gradient fill, `backdrop-filter: blur(22px) saturate(160%)`, a 1px
gradient-highlight rim via a masked `::before`, layered shadow), `glass-tile` (lighter inner
variant for nested rows), `press-3d`/`tilt-3d`/`lift-3d` (hover/press micro-interactions — full
3D perspective tilt on home tool cards via `scene-3d { perspective: 1200px }`, gentle vertical
lift on list rows), `shimmer`/`blur-placeholder` (skeleton loading sweep + blurred media
placeholder), all defined once and reused everywhere. Genuinely and completely reduced-motion
aware: an OS-level `@media (prefers-reduced-motion: reduce)` block *and* an app-level
`.reduce-motion` class (toggled from Settings) neutralize every transform/animation utility
identically.

Our glass tokens (`GlassFill`/`GlassBorder`/`GlassHighlight`/`GlassShadow` in `Theme.kt`) already
cover the *color* half of this; per the `five-star-quality` plan (B3, shipped rc10) we also
already have `rememberReduceMotion()` wired into the generation screen. What's likely missing is
the **shared component layer**: a single reusable `GlassCard`/`GlassTile` pair with a consistent
press/lift interaction (Compose `pointerInput` + `animateFloatAsState` for a subtle scale/elevate
on press, gated by `reduceMotion`), and a shared `ShimmerBlock`/`SkeletonLines` composable used
everywhere a result is loading instead of ad hoc `CircularProgressIndicator`s. Check
`ui/components/GlassCard.kt` (referenced elsewhere in this codebase) for how much of this already
exists before building anything new — extend, don't duplicate.

### A3. Navigation: floating dock + center FAB vs. top tab/pager

This is the single biggest structural UI difference. lookbookweb's `AppShell.tsx` uses a
**fixed bottom dock** (`soft-card` pill, safe-area aware) with five slots — Home, Library, a
**raised circular center "+" button** that opens a modal listing every tool
(`src/lib/tools.ts`'s `TOOLS` array: Effects/Video/Image/Voice/Code/Chat, each a colored icon
chip), Chat, Settings. Our current nav (`HomeScreen.kt`) is a horizontal `TabRow` + `HorizontalPager`
across `HomeTab` (Try-on/Image/Video/Audio/Code/News) — a different metaphor: swipe-between-tabs
vs. dock-plus-modal-launcher.

**Recommendation, not a mandate:** don't rip out the pager (per-tab session isolation work from
`five-star-quality` U1/U2 is real, hard-won infrastructure built around it — a nav change must
not regress that). Instead, adapt the *pattern*, not the exact widget: keep the pager for
in-studio swiping between generation modalities, but consider adding a persistent bottom bar with
a raised center "Create" action (bottom-sheet or dialog picker across Image/Video/Code/Audio, the
four *local* generation surfaces) as the primary entry point from Home/Library, matching
lookbookweb's "one obvious button starts anything" affordance — a genuine, testable UX
improvement, not a like-for-like widget swap. This needs a design decision with the user before
implementation (see Open questions).

---

## Part B — Generation-lifecycle UX, ported to local-only generation

Every item below is scoped **only** to this app's local engines (`AndroidTxt2ImgEngine`,
`BonsaiImageEngine`, the four LiteRT-LM/MediaPipe code generators, `AndroidLocalVideoGenerator`,
`AndroidLocalAudioGenerator`/voice changer, local vision/transcription) — never to cloud calls.

### B1. Structured, resumable job state (not just a live console)

This session already shipped live per-step streaming (`LiveGenConsole`, `LocalImageStreamEvent`,
`LiteRtLmStreamEvent`) — real progress, not a static spinner. What lookbookweb has that we don't:
**job state that survives the app being backgrounded or killed mid-generation.**
`src/lib/videoJobs.ts` persists jobs (status/progress/error/`failedStage`) to Supabase and
`create.video.tsx` auto-resumes any `status === "running"` row on next open with a toast telling
the user how many were resumed. We have no backend and shouldn't add one for this — but a Bonsai
Image 4B run is "several minutes on CPU" (per its own catalog `testingNote`) and nothing currently
protects that work if the user switches apps and the process is reclaimed.

**Action:** define a small on-device job record (Room or DataStore — whichever this module
already uses for local persistence) keyed by a generated job id, capability, prompt, and a status
enum (`queued/running/done/failed/cancelled` — lookbookweb's exact vocabulary is a good default),
written at job start and on terminal state. On app relaunch, check for any `running` row older
than a sane engine-runtime ceiling and surface it as "interrupted — tap to retry" rather than
silently losing it. This does **not** mean resuming mid-inference (ONNX/LiteRT sessions aren't
checkpointable mid-run) — it means the *user-facing memory* of "I asked for X and it didn't
finish" survives, instead of vanishing.

### B2. Version lineage for local generations

lookbookweb's video studio groups a job and all its retries by `retry_of` into a `v1, v2, v3…`
chain with per-version re-run (`versionChain()`, `create.video.tsx`'s "Prompt history" panel).
Local image/edit already saves outputs to the Wardrobe with metadata; check whether retries
(same prompt, tweaked seed/strength) are discoverable as a *chain* there or just as unconnected
gallery entries. If the latter, a lightweight `parentGenerationId` field + a "View history" action
on a Wardrobe item (chain of prior attempts, tap to re-run with that seed) is a real, scoped
improvement — genuinely more useful for local generation than cloud, since local generation is
the one path where re-running with a tweaked seed is free and instant feedback matters most.

### B3. Correlation-ID-first error UX

lookbookweb stamps a `correlationId` on every generation attempt (`src/lib/diagnostics.ts`) and
echoes it in every user-facing error string ("… (ref {id})"), with a Diagnostics panel to look it
up (Recent/Summary tabs, "Report issue" flow, JSON/ZIP export). We already have
`DiagnosticsScreen.kt`/`DiagnosticsExport.kt`/`DiagnosticsHook` — verify whether local-generation
failures (`LocalImageResult.Unavailable(reason)`, engine `warmUp()` failures, LiteRT-LM timeouts)
actually thread a lookup-able id into their user-facing message the way cloud failures do via
`CloudFailure`. If local failures currently show only a plain reason string with no way to
correlate it to a diagnostics record, that's the gap to close — small, mechanical, high value for
support/debugging.

### B4. Per-model storage/download management, consolidated

lookbookweb's `EdgeDeviceLab.tsx`/`LocalModelLab.tsx` give a unified per-model view: download
progress, pause/resume (not just cancel-and-restart), update, delete, and a used/quota storage
bar, plus a live device-capability preflight checklist (RAM/storage/64-bit/GPU, each with a
plain-language `fix` hint when red). Our `PacksScreen.kt` already has real per-pack install
state, resumable downloads (HTTP range via `PackDownloadWorker`), and per-pack `Force stop`, but:
- No aggregate "X GB used across N packs, Y GB free" rollup — each pack shows its own size only.
- `Force stop` cancels; there's no explicit user-facing *pause* (vs. cancel-and-resume-on-next-tap,
  which already works per this session's audit but isn't framed as "pause").
- Device preflight (RAM/storage/minSdk) already gates install via `ModelPackManager.deviceMeets`,
  but is it shown as a scannable checklist with fix hints, or only as a terse "device doesn't meet
  requirements" string on `PackStatus.INCOMPATIBLE`? If the latter, upgrading that single string
  into a 2-3 line checklist (RAM: have X, need Y; storage: have X, need Y) closes a real gap
  cheaply.

**Action:** add a storage rollup header to `PacksScreen`, and expand the `INCOMPATIBLE` state's
copy from one line to a short checklist. Both are additive, no architecture change.

### B5. Processing-mode setting, made explicit and singular

lookbookweb surfaces one named 3-way setting — **Auto / On-device only / Cloud only**
(`ProcessingMode` in `providers.ts`) — as three selectable cards with plain-language blurbs in
`EdgeDeviceLab`, enforced end-to-end in its fallback chain (`device` mode never silently falls
back to cloud; it throws instead). We have the equivalent *logic* scattered as
`AppSettings.prefersLocal(capability)` (per-capability, not global) plus the "Enable cloud
models" master toggle from `local-first-mode`. Given the user's explicit instruction that local
generation is this app's core, **making a single, prominent, honestly-labeled Auto/On-device-only/
Cloud-only card in Settings** (mirroring lookbookweb's exact framing, adapted to this app's real
capability set) is directly on-brief — not a nice-to-have. On-device-only mode should behave like
lookbookweb's: a capability with no local candidate available fails with a clear message
("On-device only is on and this needs a pack — install one in Model packs, or switch modes"),
never silently reaching for cloud (this repo's AUTO-never-cloud invariant already guarantees the
non-explicit case; this closes the loop for a user who explicitly asked for device-only).

### B6. Voice studio depth (DSP, not ML — matches what we already have)

Confirmed: lookbookweb's voice engine (`src/lib/voice.ts`) is **real-time DSP** — a custom
`AudioWorkletProcessor` pitch shifter, timbre/distortion/ring-mod/convolution-reverb chain, and
"cloning" that's really autocorrelation pitch/brightness *matching* (`analyseVoice()`,
`semitonesBetween()`), not a trained voice model. This matches our own local voice changer's
nature closely, so this is genuinely portable without any capability gap:
- **Real-time level meters + waveform/spectrum scope** — `create.voice.tsx`'s `useMeters()` +
  `LevelMeter`, and `src/components/LiveScope.tsx` (canvas waveform + spectrum bars + dB readout,
  driven off `AnalyserNode` in one `requestAnimationFrame` loop so it never triggers React
  re-renders). Android equivalent: an `AudioRecord`/`Visualizer` amplitude/FFT read driving a
  Compose `Canvas` redraw loop outside Compose's normal recomposition (e.g. via
  `LaunchedEffect` + `withFrameNanos`), same "don't recompose the whole tree at audio rate"
  discipline.
- **Latency auto-calibration** — `measureRoundTrip()` (play a tone, time its return via analyser
  threshold) + `suggestQuality()`. Directly applicable to our voice changer if it has adjustable
  buffer/quality tiers.
- **Preset grouping by category** with inline badges (semitone/timbre/reverb at a glance) instead
  of a flat list, if ours is currently flat.
- **Cloning-from-sample**: record/upload a few samples → measure the target's average pitch →
  compute the semitone shift to match it live. This is a small, well-scoped DSP feature (not ML),
  directly implementable against our existing local voice-changer pipeline.

### B7. Safety presets, scoped to local output

lookbookweb's `safety.ts` (Off/Standard/Blur identities/Redact details) appends a prompt-guard
string and applies a client-side canvas blur/redact post-process, surfaced directly on the
generation screen with a confirm-before-generate gate on the stronger presets. For local
generation specifically (no server-side moderation exists or is needed — everything stays on
the phone), the useful subset is the **local image post-process**: an optional on-device
blur/redact pass over a locally-generated or locally-edited image before it's saved to the
Wardrobe, as a user-controlled privacy option (e.g. before sharing a generated image that
happened to include a recognizable face from a reference photo). Lower priority than B1/B5, but
cheap to build once `QualityEnhancer`'s post-process pipeline (already wired this cycle for
Real-ESRGAN) has a face/region-blur step added to it — same insertion point, different processor.

### B8. Skeleton/shimmer loading, consistently

lookbookweb reuses one small set of loading primitives (`ShimmerBlock`/`SkeletonLines`/
`SkeletonRows`/`OutputPlaceholder`) everywhere instead of ad hoc spinners. Audit
`ui/components/` for whether we already have an equivalent shared composable; if results/history
panels each roll their own loading state, consolidating into one `ShimmerBlock`-equivalent is a
small, high-leverage consistency win alongside A2.

---

## Part C — Explicitly out of scope

Per the user's own instruction and this repo's standing invariants, the following lookbookweb
features are **not** to be ported, even though they're real and well-built in that codebase:

- Cloud image/video generation itself, and any of its cloud providers (Lovable AI Gateway,
  OpenRouter, Replicate, Hugging Face Inference as used there) — this app's image/video
  generation stays local-first; cloud image/video already exists here as a separate, pre-existing
  fallback system with its own plan history (`generation-stability/`) and is untouched by this
  plan.
- Supabase-backed accounts, cross-device library sync, or any server-side memory/sources-sync
  system (`chat.tsx`'s "Information used" citations, `sources.tsx` RSS/page syncing) — these are
  fundamentally server-dependent; this app's local generation has no equivalent need and adding a
  backend would contradict "local generation is the core of this app."
- Usage/spend/balance dashboards (`ProviderSettings.tsx`) — not applicable; local generation has
  no per-request cost.
- The code *workspace* metaphor's ZIP/multi-file/diff machinery as a 1:1 port — genuinely useful
  (see B2's lighter-weight lineage idea instead), but a full file-tree-plus-diff IDE is a much
  larger build than this plan's local-generation focus justifies; flag for a future, separate
  plan if the user wants Code Studio to grow into a real workspace.

---

## Part D — Deep testing on generations (ongoing discipline, not a one-time pass)

The user's standing instruction from this session — "test the models... rather than pretending
and depending on the code" — applies to this plan's own execution, not just the sdturbo/Bonsai
work already done. Two local generation paths have **not yet** been genuinely tested this way and
should be, independent of anything else in this plan:

1. **Local code generation correctness** (Gemma 3, Gemma 4 E2B, Qwen3 0.6B, FunctionGemma via
   LiteRT-LM/MediaPipe) — this session verified the *streaming plumbing* (delta-chunk semantics
   against Google's AI Edge Gallery reference) but never verified *output quality/correctness* by
   running the actual published packs against representative prompts and checking the generated
   code compiles/runs. Do this the same way sdturbo was done: download the real published
   `.litertlm`/`.task` files, run them via their real runtime (LiteRT-LM Python bindings or
   MediaPipe's Python GenAI package) on a representative prompt set, and inspect actual output.
2. **Local audio path** (system TTS + DSP voice changer + transcriber) — verify the voice changer
   DSP chain against known-frequency test tones the way lookbookweb's own `voice.test.ts` does
   (autocorrelation pitch detection accuracy, no unexpected clipping), and verify local
   transcription against a few real audio clips with known transcripts, not just that it returns
   *a* string.

Both should follow the same evidence bar this cycle set: real weights/real runtime, a saved
artifact (transcript, audio file, or generated code file) as evidence, and a written note of what
was found — not "the code looks right."

---

## Open questions for the user (before implementation starts)

1. **Nav pattern (A3)**: keep the top tab/pager as primary and add a bottom dock + center Create
   action as a secondary/entry-point pattern, or a fuller redesign toward lookbookweb's dock-only
   nav? This is the one change big enough to want an explicit decision before coding.
2. **Typography (A1)**: keep the current single-family type system, or take on a display/body
   font pairing? Low risk either way, but worth confirming before adding new font assets.
3. **Priority order**: this plan doesn't rank B1–B8 against each other yet. Suggested order by
   effort-to-value (smallest/highest-value first): B4 (storage rollup + checklist copy) → B5
   (single processing-mode setting) → B3 (correlation-ID threading for local failures) → B8
   (shared shimmer component) → B1 (resumable job state) → B6 (voice studio depth) → B2 (version
   lineage) → B7 (safety post-process) → A0–A2 (design-system work, larger and more visible, best
   done once the UX-behavior items are settled so the new visuals aren't immediately churned).

## Definition of done for this plan (once implementation starts)

- Every Part B item is either shipped with a test/evidence trail, or explicitly deferred with a
  reason recorded here (matching this repo's standing "verify before claiming done" discipline).
- No item in Part C has been implemented.
- Part D's two testing gaps are closed with real evidence artifacts, following this cycle's
  sdturbo/Bonsai methodology exactly.
- `docs/plans/README.md` row for this plan is updated from "Planning" to reflect real progress
  as work lands — not marked done from a code read alone.

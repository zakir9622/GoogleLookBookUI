# Generation transparency — measure what's real, show what's happening

> **Baseline:** v3.1.0-rc14 (`b76c215` + DoD pass). Supplement to
> [`../five-star-quality/`](../five-star-quality/) — not a replacement. No new visual design
> system (Loom Ink stays).
> **Status:** rc15 complete — all code items shipped; Pixel 9 on-device benchmark table is a post-install device run.

## Re-check vs tree (2026-08-23, post-rc14)

| Item | Plan claim | Current tree |
|------|------------|--------------|
| A2 export collision | open | **Done in rc14** — `export_diffusion_pack.py` refuses; CatVTON → `export_catvton_legacy_pack.py`; `verify-manifest.py` asserts conditioning files |
| B4 sampler UI | open | **Done in rc14** — Steps/CFG/Seed removed from composer (never reached cloud payloads) |
| OrtGraph QNN→NNAPI→XNNPACK cascade | claimed at OrtGraph.kt:19-29 | **Outdated** — production uses `ProOrtSessions` (CPU + `NO_OPT`, QNN never; NNAPI only if `OrtEpPolicy.preferNnapi`) |
| A0 on-device numbers | missing | Harness shipped; [`docs/BENCHMARKS.md`](../../BENCHMARKS.md) has desktop reference — **Pixel 9 table TBD** |
| B1/B2/B3 | open | **Done rc15** — per-tab log + try-on `GenerationScreen`, elapsed timer, reduce-motion |
| A1 OrtSessionCache in Pro | open | **Done rc15** — `SdControlNetPipeline` uses `OrtSessionCache.openGraph()` |
| A3 minSdk | open | **Done rc15** — `deviceMeets()` checks `sdkInt >= minSdk` |
| B4 quality retry | open | **Done rc15** — `rejectReason()` + blank-frame on Inference; classifier maps blank → `BadOutput` |

---

## Why this plan exists

Local generation (image, code, video, audio) shipped fast across the "true local" and
"big release R2" trains — genuinely impressive velocity. But two things never got checked along
the way: **nobody has ever measured how any of it actually performs on real hardware**, and the
export-script collision that could silently produce an under-conditioned Pro pack (documented in
`docs/plans/generation-stability/PLAN.md`, finding R1) is still unresolved — runtime code now
detects and recovers from a bad pack gracefully, which is good, but doesn't stop a future rebuild
from producing one again. Separately, the generation UI still shows exactly one line of text that
overwrites itself — no history, no persistent timer — even though the harder problem (keeping
each studio tab's state independent) is already solved.

This plan closes both: establish real, on-device, committed numbers before anyone reasons further
about local-generation performance, and give users an honest, per-tab view of what's happening
while they wait.

## Part A — Measure, then resolve the one structural ambiguity left

### A0. Build a real on-device benchmark harness
`scripts/benchmark-local.py:22` runs on desktop `CPUExecutionProvider` — replace or extend it
with an Android-instrumented equivalent (`composeApp/src/androidTest/`) that runs the actual
production code path: real `OrtGraph`/`OrtSessionCache` construction, the real
`addQnn()`→`addNnapi()`→`addXnnpack()` cascade, on a connected device. Add EP-selection logging —
today every fallback in that cascade is silently swallowed by `runCatching`
(`OrtGraph.kt:19-29`); log which provider actually initialized, per session. Time every graph in
the Pro pipeline individually and end-to-end (depth, image-encoder, controlnet, unet,
vae-decoder, text-encoder) plus the shipped Lite/local-image/local-video/local-audio paths. Write
the result to a **committed** artifact (e.g. `docs/BENCHMARKS.md` with a date and device model) —
not `/tmp`, so a number exists somewhere a human can read it. Run this on the Pixel 9 first.

This is the one item that changes what everything downstream should even prioritize: it tells you
whether NNAPI/QNN is actually accelerating these graphs or silently falling back to per-op CPU.

### A1. Wire `OrtSessionCache` into the Pro/diffusion path
`SdControlNetPipeline` still builds 6 fresh sessions per generation
(`SdControlNetPipeline.kt:68,77,98,99,119,139`). `LiteEngine`/`HumanParsing` already prove the
caching pattern works — reuse it here. Re-measure with A0's harness before/after to confirm the
win is real, not assumed.

### A2. Resolve the Pro-pack export-script collision, permanently
One source of truth for `exports/pro-v1`: keep `convert_pro_pack.py` (the one that produces
`controlNet`/`depthModel`/`imageEncoder`), and delete or clearly rename
`export_diffusion_pack.py` so a future rebuild cannot silently collide with it again. Verify the
live HF-published `pro-v1` pack is fully-conditioned (`isFullyConditioned == true`) using the
sticky graph-incompat detection that already shipped — if it isn't, republish. Make the degraded
fallback path surface in `RunDiagnostics` (already the right seam, already used elsewhere in this
codebase for exactly this kind of signal) rather than `Log.e` alone.

### A3. Small cleanup, low priority
`DeviceSpec.minSdk` — verify whether it's still a dead field in `deviceMeets()`; wire or delete
it. `synchronized` in commonMain / `EpochClock`'s JVM-only clock — if `five-star-quality` or
another active train hasn't already touched these, close them here; if it has, mark this item
done and move on rather than redoing it.

## Part B — Generation transparency UI

### B1. Turn the single-line status into a real per-tab log
`GenerativeViewModel.kt:141,526` overwrites `_state.value` outright on every emission — change to
append each `Running` stage to a bounded, per-ViewModel list instead of replacing it. Per-tab
isolation already ships (`five-star-quality` U1/U2) — this is additive on top of that, not a
rebuild. Render as a small scrollable log in the existing Loom Ink visual language (monospace
stage lines, not a redesign) inside `ResultPane`/`UnifiedStudioPane`, and give `GenerationScreen`
(the native try-on flow, which still renders progress independently — verify whether it's been
unified with the cloud-studio state type since the last check, and if not, apply the same log
there too).

### B2. Add a real elapsed-time indicator
The existing "(45s left)" countdown text is a *remaining-budget* hint embedded in the stage
string, not a running clock. Add an actual ticking elapsed-time value (started when generation
begins, stopped on completion/failure) next to the log from B1 — small, cheap, and it's the
literal "timer showing start of generation" the product ask was for.

### B3. Close the reduced-motion gap on the generation screen
Extend `rememberReduceMotion()` coverage to `GenerationScreen.kt`'s `animateFloatAsState` calls
and `DevelopShader.kt`'s infinite loop — currently zero references, confirmed. The longest-running
screen in the app is the one place this accessibility setting doesn't reach.

### B4. Tighten output quality, using what already exists
Don't build a parallel mechanism — wire what's already there. If `generation-stability`'s R2
finding (composer sliders for steps/CFG/seed that set ViewModel state nobody downstream reads) is
still open, close it here: either the controls actually change the payload sent to the model, or
remove them. Extend `CloudOutputValidator` with a `QualityRating`-based check that catches
genuinely bad (not just malformed) outputs and retries, the same way blank-frame rejection already
works for Model Health.

## The loop

Same discipline as every prior plan in this repo: verify the premise against current code before
touching it (this doc's own claims included — re-check file:line references, things move fast
here), write a test/benchmark that fails without the fix, fix, build, run on the real device,
record the evidence, commit stating what was verified not attempted. Work A0 before A1/A2 — you
cannot responsibly claim a latency win or resolve the pack ambiguity without a number to check
against. Check `five-star-quality/PLAN.md`'s own Q3/Q4 checklist before starting each item here —
if it's already been picked up there, don't duplicate it, just cross-link and move to the next.

## Non-negotiable invariants (unchanged across every plan in this repo)

AUTO tier never selects cloud. Free-tier only for cloud models. Every generated image keeps its
watermark + EXIF provenance tag. No secrets in release builds. Pro try-on requires `lite-v1`.
Engines emit failure states, never throw for expected failures.

## Definition of done

- A committed, dated, device-attributed benchmark artifact exists for every local generation
  path, with EP selection logged per graph.
- `OrtSessionCache` covers the Pro pipeline; the win is measured, not assumed.
- Exactly one script produces the Pro pack's conditioning files; the collision is structurally
  impossible to reintroduce.
- Every studio tab shows an accumulating log and a real elapsed-time counter, not one overwriting
  line.
- Reduced-motion is honored on the generation screen.
- Every visible composer control changes what's actually sent to the model, or has been removed.

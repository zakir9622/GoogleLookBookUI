# Five-star quality — iterative release plan

**Status:** Active · baseline **v3.1.0-rc9** on `main`  
**Branch convention:** readable kebab names only (e.g. `cursor/five-star-quality-…`). Avoid random hex soup in the descriptive part.  
**Goal:** Ship a sideload APK that feels production-trustworthy — honest local/cloud, no cross-tab contamination, crash-hard native paths soft-failed, UI that earns a 5★ mental model.

---

## What is already on `main` (do not lose)

| Train | Tip | Contents |
|-------|-----|----------|
| True local | rc7–rc9 | SD-Turbo Create/Edit, still-clip Video, Gemma Code, system TTS, handshake |
| Cloud reliability | rc6 | ORT R8 keep, FLUX default, audio budget, credit copy |
| Release policy | PR #54 | APK **only** on `main` merge/push; old releases pruned |

Canonical download: GitHub Release tag **`latest`**.

---

## Audit snapshot (2026-08-23)

### P0 — must fix before 5★

| ID | Issue | Area |
|----|-------|------|
| U1 | Shared `GenerativeViewModel` wiped by adjacent pager tabs (`prepareStudio`) | UI |
| U2 | Wrong studio shows another tab’s result | UI |
| S1 | Local image/code/video never `markPackInUse` | Stability |
| S2 | `OrtGraph` lacks safe session + output size caps | Stability |
| S3 | Still-clip MediaCodec missing PTS → unplayable MP4s | Local video |
| S4 | Abrupt-exit logcat scrape no-op on main thread | Diagnostics |

### P1 — reliability / honesty

| ID | Issue |
|----|-------|
| U3 | ON-DEVICE picker rows not selectable |
| U4 | Help / product blurb still cloud-only |
| U5 | Handshake dumps `HANDSHAKE_OK` machine strings |
| U6 | Local always wins over explicit cloud model pick |
| S5 | Code/video abort whole chain on missing key (image skips) |
| S6 | MediaPipe Gemma no timeout |
| U7 | Gallery treats video paths as stills |

### P2 — polish (later cycles)

Glass-card density, hero height, sampler fields only when supported, hide non-runnable from studio picker, per-pack handshake busy, audio Cancel in ResultPane.

---

## Release trains

| Train | Version | Scope | Gate |
|-------|---------|-------|------|
| **Q1** | **3.1.0-rc10** | Studio isolation · pack-in-use · OrtGraph soft · MediaCodec PTS · honesty copy · human handshake · honor cloud pick when online | Unit + CI |
| **Q2** | 3.1.0-rc11 | Selectable local picker · MediaPipe timeout · gallery media types · CrashReporter scrape · code/video key skip | Unit + CI |
| **Q3** | 3.1.0-rc12 → **rc13** → **3.1.0** | Device matrix · Pro graph probe · sticky incompat · picker honesty | Unit + CI + manual matrix |
| **Q4** | 3.1.x | UI declutter · accesslint · visual baselines | Device evidence |

---

## Iterative cycle protocol

Each cycle:

1. **Pick** ≤6 P0/P1 items from the scorecard  
2. **Fix** with tests where JVM-possible  
3. **Matrix** (at least document; device when available):

| Surface | Offline pack ready | Offline pack missing | Online prefer cloud | Online local ready |
|---------|--------------------|----------------------|---------------------|--------------------|
| Image Create | local PNG | fail soft / CTA packs | selected cloud | local unless cloud selected |
| Image Edit | img2img | CTA | cloud | local if edit-ready |
| Video | still-clip | cloud or CTA | cloud | still-clip unless cloud |
| Code | Gemma | cloud | cloud | Gemma unless cloud |
| Audio | system TTS | — | TTS first | TTS first |
| Try-on | Lite/Pro | CTA | N/A | N/A |

4. **Score** 1–5 on: Stability · Honesty · Clarity · Offline · Cloud fallback  
5. **Stop** a cycle when average ≥4.5 or open a new cycle for remaining gaps  
6. **Merge to `main`** only when CI green → triggers Release APK `latest`

### Star rating rubric

| Stars | Meaning |
|-------|---------|
| 1 | Crashes / wrong studio results / lying copy |
| 2 | Works sometimes; confusing packs |
| 3 | Reliable online; offline half-broken |
| 4 | Honest local+cloud; rare soft fails |
| 5 | Feels intentional; failures teach next action; no cross-tab ghosts |

---

## Q1 checklist (shipped · rc10)

- [x] U1/U2 — per-studio generative session (no adjacent wipe)
- [x] S1 — `markPackInUse` on local image/code
- [x] S2 — `OrtGraph` safe session + output cap
- [x] S3 — MediaCodec PTS on still-clip
- [x] U4 — Help + product blurb true-local
- [x] U5 — Human handshake labels
- [x] U6 — Prefer local only when offline; honor cloud when online
- [x] Tests for handshake label + offline local routing
- [x] Version **3.1.0-rc10**

---

## Q2 checklist (shipped · rc11 · PR #56)

- [x] U3 — Selectable ON-DEVICE picker rows (`setLocalGenerator` / `prefersLocal`)
- [x] S6 — MediaPipe Gemma generate timeout (90s)
- [x] U7 — Gallery/wardrobe video frame thumbs (`MediaThumb`)
- [x] S4 — CrashReporter abrupt logcat scrape off main thread
- [x] S5 — Code/video fallback skips missing-key candidates (like Image)
- [x] Pro ORT FP16 / ControlNet soft-fail + AUTO→Lite (device diagnostics)
- [x] Version **3.1.0-rc11**

---

## Q3 checklist (active · rc20)

- [ ] Run [DEVICE_MATRIX.md](./DEVICE_MATRIX.md) on Pixel 8/9 with `latest` APK — **still blocked**:
      this repo's Claude sessions have no `adb`/physical-device access, so the "Stability"/"Offline"
      cells of the rubric cannot be genuinely scored to 5/5 from here. Closing this line requires a
      human running the matrix on real hardware; don't claim a device-verified average without it.
- [x] Automated routing matrix tests (offline + online `prefersLocal`)
- [x] Hide non-runnable scaffolds from studio ON-DEVICE picker
- [x] Audio ResultPane Cancel during generation
- [x] Pro sticky graph-incompat (`markGraphIncompatible`) + handshake UNet probe
- [x] Skip legacy Pro after ControlNet ORT incompat (AUTO→Lite one shot)
- [x] Composer honesty: Steps/CFG/Seed removed everywhere they never reached the model (rc14)
- [x] Model Health uses runtime `effectiveSupport`, not the static catalog table (rc14)
- [x] Local image/edit generation streams live per-step progress, not one static message (rc19)
- [x] Real desktop-verified fix for two local Create Studio bugs found by actually running the
      published `local-sdturbo-v1` weights (FP16 timestep tensor, LCM boundary-condition math +
      img2img strength/timestep slicing) — see CHANGELOG 3.1.0-rc19
- [x] Real-ESRGAN quality upscale reaches local Create output, not just try-on (rc19)
- [x] Per-pack handshake busy state — `PacksScreen` used one shared flag so every installed
      pack's "Verify device link" button went busy at once with no way to tell which pack was
      actually being checked; now tracked per-pack (`ModelPackManager.handshakeAll(onPackStarted)`)
- [x] Real on-device crash fixed (rc20) — a user's Pixel 9 screenshots caught
      `ORT_INVALID_ARGUMENT — Invalid rank for input: timestep` in local Create Studio; the local
      txt2img engine built the timestep tensor with no shape. Reproduced against the real
      published graph before/after the fix to confirm.
- [x] Local-vs-cloud mislabeling fixed (rc20) — a user's diagnostics export caught local runs
      recorded and announced as whatever cloud model was selected (e.g. a CHAT run tagged
      `llama33-70b-groq` while its own note field said local Qwen3 actually ran, cloud off).
      Fixed across image/code/video/audio generation and the diagnostics run records/Tier field.
- [x] Video offline hard-stop (rc20) — video was the one capability still soft-continuing to
      cloud when offline ("Network probe uncertain — trying cloud anyway…"); now hard-stops like
      image/code/audio.
- [x] Video pack-in-use through generate+encode (rc20) — `AndroidLocalVideoGenerator` didn't hold
      `local-sdturbo-v1` in use across its still-image + MediaCodec encode stages.
- [x] U5 correction (rc20) — Q1 marked "Human handshake labels" done at rc10, but the actual
      Toast text in `PacksScreen`/`SettingsScreen` still built `"${result.signal} · ..."` (a raw
      `HANDSHAKE_OK`/`HANDSHAKE_FAIL` machine string) — the human-readable path
      (`PackHandshakeWires.formatUserSummary`) existed but was never wired into the toast calls.
      Fixed; see the Honesty score correction below.
- [ ] Scorecard ≥4.5 average → tag **3.1.0** (or open Q4 for polish gaps) — see honest self-score
      below; code/desktop-verifiable dimensions are strong, the on-device dimension is the one
      real gap left, not more code work
- [ ] Confirm Pro AUTO→Lite on device with installed `pro-v1` — same device-access blocker

**Interim builds:** rc12 polish · rc13 Pro graph probe · rc19 streaming + verified local image-gen
fixes + quality upscale + per-pack handshake honesty · **rc20** real user-reported device fixes
(timestep crash, local/cloud mislabeling, video offline parity, handshake toast honesty).

### Honest self-score (code + real desktop model execution, no physical device — 2026-08-24)

| Dimension | Score | Why |
|---|---|---|
| Stability | 4/5 | ORT/LiteRT sessions soft-fail; a real crash (timestep tensor rank) found via a user's actual device was fixed and re-verified against the real graph. Can't reach 5 without a full device soak — ARM/XNNPACK-specific behavior beyond what's been reported is still unverified. |
| Honesty | 4/5 | **Corrected from a 5/5 claimed earlier this same cycle** — that score was written before a user's diagnostics export caught two live honesty bugs this doc had not found: local runs mislabeled as cloud everywhere, and a stale `HANDSHAKE_OK`-leaking toast that Q1 had marked fixed but wasn't. Both are now fixed, but the corrected score is the honest one: don't claim 5/5 on a dimension a fresh source of evidence (the actual user, running the actual app) just disproved. |
| Clarity | 4/5 | Live streaming output + per-pack progress close the two biggest "what is it doing" gaps. Not yet re-verified against a fresh design pass (glass-card density / hero height are unverified without a rendered screenshot). |
| Offline | 4/5 | Local image gen (tiny-SD + Bonsai), code, video, audio, try-on all verified reachable offline in code; video's offline hard-stop gap (this cycle's finding) is now closed, bringing it to parity with the others. |
| Cloud fallback | 5/5 | Typed `CloudFailure`, health-aware routing, and the fallback-loop tests already in place from earlier cycles are unchanged and still green. |
| **Avg** | **4.2/5** | Two real gaps keep this off ≥4.5: the device matrix, and the reminder that "verified against code" is not the same bar as "verified against a real device" — the two bugs this cycle fixed were both found by a user, not by this repo's own review process. |

---

## Non-goals (Q1)

- Full accesslint device sweep  
- New visual design system (Loom Ink stays)  
- Diffusion video on-device  
- Larger than Gemma 1B on-device  

---

## Branch naming (going forward)

Use readable kebab-case:

```
cursor/five-star-quality-367c
cursor/studio-session-fix-367c
cursor/pack-handshake-polish-367c
```

Avoid opaque hashes in the *descriptive* segment. (Cursor cloud agents may append a short workspace suffix — keep the words clear.)

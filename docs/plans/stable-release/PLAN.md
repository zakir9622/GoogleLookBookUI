# Stable release plan — The Lookbook

**Status:** Track **R0 shipped** as **v3.0.4** · Track **R1** through **v3.0.6** (health UI + blank-frame @ 3.0.5; Settings C4 split @ 3.0.6; weights/device still open for v3.1.0)  
**Baseline:** tagged tip of `cursor/stable-r1-plans-367c` (PR #49)  
**Goal:** one installable, trustworthy sideload APK that matches the shipped plans, then a short path to “perfect” offline + cloud fluency.

---

## Plan / cycle verification (as of 2026-08-22)

### Iterative UX cycles (`iterative-*`, v2.7.x → merged)

| Cycle | Version | Outcome |
|-------|---------|---------|
| Quality cycle | 2.7.1 | Live contract readiness, reduced motion, Result copy |
| Atelier cycle 2 | 2.7.2 | Cancel generation, permission refresh |
| Local cycle 3 | 2.7.3 | Back cancels jobs, Share/Report, privacy |
| Cycles 4–5 | 2.7.4–2.7.5 | A11y composer/gallery, model chip → Settings |
| **Verdict** | — | **DONE** — ancestors of `main`; no open work |

### Claude expansion cycles (`docs/plans/claude-code-expansion`)

| Cycle | Verdict | Evidence |
|-------|---------|----------|
| cycle1 discovery | **DONE** | HF router discovery, inference edit, probes |
| cycle2 quality packs | **DONE @ 3.0.4 RC** | birefnet/realesrgan on HF; runners + integrity on PR #48. **pro-v2-int8 HF still open** |
| cycle3 UI/settings | **DONE @ 3.0.5–3.0.6** | Hub/wizard/preflight; live health badges; SettingsScreen split |
| cycle4 speed/compliance | **PARTIAL** | QNN/LCM/safety hooks present; not fully productized |

### v3 follow-up (`lookbook-v3-followup` A1–E4)

| Band | Verdict |
|------|---------|
| A1–A5, B3–B6, C1, C5–C6, D3, E1–E2 | **DONE** |
| A6, B1, D1–D2 | **PARTIAL** (HF upload / device / a11y remain) |
| C2–C4 | **DONE** @ 3.0.6 (SettingsScreen file split + durable CTA on download) |
| B2 quality packs | **DONE on HF**; **runners fixed in 3.0.4 RC** |
| E3 Gemma / E4 SD-Turbo weights | **OPEN / deferred** |

### Generation stability (`generation-stability` M1–M6)

| Milestone | Verdict |
|-----------|---------|
| M1 typed failures + fallback | **DONE** (minor hostname-string residue) |
| M2 health + budget + validator | **DONE** @ 3.0.5 — live labels + blank-frame + 2 KB floor |
| M3 Gradio schemas | **MOSTLY DONE** |
| M4 local image gen | **CODE DONE** @ 3.0.7 (`AndroidLocalImageGenerator`); weights pending |
| M5 visual harness | **PARTIAL** — scripts exist; baselines/device evidence thin |
| M6 cleanup/portability | **MOSTLY DONE** — EpochClock/hooks + minSdk 35 docs; iOS stretch open |

---

## Two release tracks

### Track R0 — **Stable cut now → v3.0.4** (recommended)

Ship PR #48. This is the honesty cut: packs that claim to work actually run, and local pack churn does not crash ORT.

**Must ship**
- [x] Real-ESRGAN FP16 + `denoise_strength` runner
- [x] BiRefNet sigmoid matte
- [x] Pack in-use refcount; block uninstall/update while generating
- [x] ORT session cache invalidate on pack replace
- [x] Integrity smoke for ESRGAN; catalog sizes; minRam 2 GB for realesrgan
- [x] CI green on PR #48 (`Android CI` + `Release APK` preview)
- [ ] Mark PR ready → merge to `main` (PR #48 open; tag already cut from branch tip)
- [x] Tag `v3.0.4` → versioned GitHub Release + APK asset (workflow running)
- [x] Docs: `PROJECT_STATUS`, plan READMEs, this plan status → **R0 shipped**

**Acceptable deferrals inside R0** (do not block tag)
- Offline Create Studio (M4 / E4)
- `pro-v2-int8` / `lite-v2` HF upload
- Live cooldown badges in Model Health UI
- Full accesslint sweep + Pixel computerUse recording
- Gemma / GFPGAN

**R0 gate commands**
```bash
./gradlew :shared:testDebugUnitTest :composeApp:testSideloadDebugUnitTest
./gradlew :composeApp:assembleSideloadRelease
python3 scripts/verify-manifest.py
python3 scripts/integration-local-models.py --skip-hf-download
# with network:
python3 scripts/integration-local-models.py
```

**R0 device smoke (manual / computerUse when Pixel available)**
1. Install APK → Settings → download `lite-v1` (or debug seed) → Fast try-on
2. Optional: `birefnet-v1` + `realesrgan-v1` → matte/upscale path without crash
3. Start try-on → attempt Remove pack → toast “Can't remove… while generating”
4. Image / Video / Code / News headline handoff
5. Diagnostics export shareable

### Track R1 — **Perfect stable → v3.1.0**

Close the remaining plan gates so offline + cloud feel “perfect,” not just “honest.”

| Priority | Item | Closes | Status |
|----------|------|--------|--------|
| P0 | Ship `local-sdturbo-v1` weights + `LocalImageEngine` + airplane Create Studio proof | M4, E4 | `AndroidLocalImageGenerator` @ 3.0.7; weights + sampler TBD |
| P0 | Publish `pro-v2-int8` to HF (or remove contradictory download copy) | A6, cycle2 | Catalog `runnable=false` @ 3.0.6 until HF; copy prefers pro-v1 |
| P1 | Model picker / Usage show `ModelHealthTracker.observedLabel` + cooldown | M2 UI | **DONE @ 3.0.5** |
| P1 | Blank-frame / low-variance reject in download path | M2 | **DONE @ 3.0.5** |
| P1 | Commit `docs/screenshots/baseline/` + `visual-verify.sh --compare` green on Pixel | M5, D2 | README + `--dry-run` + catalog-matrix @ 3.0.7; PNG baselines need device |
| P1 | accesslint full sweep on `accesslint.config.json` routes | D1 | Routes expanded @ 3.0.6; live sweep needs device |
| P2 | Optional `lite-v2` on manifest | B1 | Open |
| P2 | Finish SettingsScreen split; durable-storage CTA clarity | C4 | **DONE @ 3.0.6** |
| P2 | Doc matrix: minSdk 35 (Android 15) everywhere | M6 / N | **DONE @ 3.0.6** (release notes + plans) |
| Stretch | Gemma LiteRT-LM prototype | E3 | Open |
| Stretch | GFPGAN pack | expansion | Open |

**R1 exit criteria**
- Airplane mode: Create Studio still produces an image from local pack
- Every catalog `runnable=true` model either works or is demoted
- `verify-all-models.sh` → OK or classified WARN (no raw hostnames)
- Visual baseline compare + accesslint report attached to release notes

### Track R2 — Stretch (post-perfect)

- iOS KMP targets / replace remaining JVM `synchronized` in commonMain
- FitDiT / Leffa productization
- Play Store listing (separate from sideload `v*` tags)

---

## Invariants (never weaken)

1. AUTO engine never selects Cloud for try-on
2. Free-tier only for cloud; no paid-provider defaults
3. Watermark + EXIF AI provenance on outputs that require it
4. No secrets in release `BuildConfig` / committed tokens
5. Pro try-on depends on verified `lite-v1`
6. Pack files are not deleted/replaced while `isPackInUse`

---

## Immediate next actions (agents)

1. Land PR #48 → tag **v3.0.4** (Track R0)
2. Open Track R1 issues/todos from the table above; start with M4 weights **or** pro-v2-int8 upload (whichever unblocks more users)
3. Keep this file as the single “are we release-ready?” checklist

---

## Related

- [`../lookbook-v3-followup/PLAN.md`](../lookbook-v3-followup/PLAN.md)
- [`../generation-stability/PLAN.md`](../generation-stability/PLAN.md)
- [`../claude-code-expansion/PLAN.md`](../claude-code-expansion/PLAN.md)
- PR: https://github.com/zakir9622/Agentic-AI/pull/48
- Latest stable release before RC: [v3.0.3](https://github.com/zakir9622/Agentic-AI/releases/tag/v3.0.3)

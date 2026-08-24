# Big release R2 — True limits (v3.1.0)

**Status:** **R2.0 shipped** — `v3.1.0-rc1` (ATR + Loom Ink + Txt2Img scaffold) · tip **`v3.1.0-rc2`** (Audio Studio add-on)  
**Baseline:** v3.0.16 (`041ab40`)  
**APK (R2.0):** https://github.com/zakir9622/Agentic-AI/releases/download/v3.1.0-rc1/the-lookbook-v3.1.0-rc1.apk  
**APK (Audio tip):** https://github.com/zakir9622/Agentic-AI/releases/download/v3.1.0-rc2/the-lookbook-v3.1.0-rc2.apk  
**PR:** https://github.com/zakir9622/Agentic-AI/pull/50  
**Goal:** Ship the largest honest release yet — full ATR Auto on Try-on, real-input classification harness, atelier UI overhaul, and on-device Create Studio **scaffolding that is ready the day HF weights land**.

---

## True limits (contract with the user)

| Surface | What is true on-device today | What R2 delivers | Blocked externally |
|---------|------------------------------|------------------|--------------------|
| **Try-on Lite** | `lite-v1` ORT (seg + parse + warp) | **Full ATR Auto** (all 14 categories) + geometry fallback + single-pass parse | — |
| **Try-on Pro** | Needs `pro-v1` / `pro-v2-int8` graphs | Same ATR Auto before mask | `pro-v2-int8` HF publish |
| **Image (Create Studio)** | Cloud only | `Txt2ImgPipeline` + pack contract; `isReady=false` until weights | `local-sdturbo-v1` ONNX + sampler wire |
| **Video** | Cloud HF Spaces | Honesty copy only | No mobile video pack planned |
| **Code** | Cloud LLMs | Honesty copy only | Gemma on-device = stretch / greenfield |

**Hard honesty rule:** Pro try-on UNet (9-ch inpaint) ≠ SD-Turbo txt2img. Lite/Pro packs never power Create Studio.

---

## Release trains

| Train | Version | Scope | Gate |
|-------|---------|-------|------|
| **R2.0** | **3.1.0-rc1** ✅ | ATR taxonomy + Auto + full chips + unit + fixture harness + UI overhaul + Txt2Img scaffold | Unit tests + ATR script green; APK on Releases |
| **R2.0+** | **3.1.0-rc2** | Audio Studio: personas, voice-changer knobs, cloud TTS, on-device DSP | Tip CI + Release APK |
| **R2.1** | 3.1.0 | Device soak (Pixel): Lite Auto on real worn photos; sideload update from 3.0.16 | Manual device matrix |
| **R2.2** | 3.1.x | HF unlock: flip `local-sdturbo-v1` / `pro-v2-int8` runnable when weights land | Pack size + airplane Create Studio proof |

---

## Phase checklist

### R2.0a — Full ATR Auto classification

- [x] `AtrTaxonomy` (commonMain): ATR/LIP 18-class histogram → all `GarmentCategory` values
- [x] `HumanParsing.parse` + region-from-map (single ORT pass for Auto)
- [x] `GarmentClassifier` geometry expanded (fallback when no person / no pack)
- [x] Lite + Pro engines: Auto prefers ATR on person, else geometry on garment
- [x] Garment screen chips: full taxonomy (not the old 8)
- [x] Unit tests: synthetic class maps for Abaya / Hijab / Niqab / Shalwar / Kurta / Dupatta / Dress / Trousers / Lehenga / …

### R2.0b — Real-input ATR harness

- [x] `scripts/test_atr_classify.py` — mirrors taxonomy; loads `scripts/fixtures/atr/*.json`
- [x] Fixtures: synthetic “real shape” histograms (worn abaya, niqab, trousers, …)
- [x] Document how to drop real Pixel dumps later (`classMap` export)
- [x] Audio Studio (R2 add-on @ 3.1.0-rc2): personas + knobs + cloud TTS + local DSP changer

### R2.0c — UI atelier overhaul (“Loom Ink”)

Direction: cool ink silk + brass thread (not purple, not cream+terracotta, not broadsheet).

- [x] Theme tokens: cool mist canvas, brass accent, deep teal-ink atelier
- [x] `AtelierHero`: cooler silk panels, stronger brand hierarchy
- [x] Home / Create Studio honesty copy for local limits
- [x] Motion: keep scan + panel drift + home fade (2–3 intentional motions)

### R2.0d — On-device Create Studio scaffold

- [x] `Txt2ImgPipeline` documents required graphs + `SAMPLER_WIRED=false`
- [x] `AndroidLocalImageGenerator` reports precise unlock steps
- [ ] HF: publish real `local-sdturbo-v1` weights (external)

### R2.0e — Non-goals (document only)

- On-device video decode/gen
- On-device Gemma / code LLM
- Claiming Pro pack = Image Studio

---

## Architecture — Auto classify path

```
garment pick  →  geometry-free worn heuristic only (no human_parse.onnx)
generate Auto →  human_parse once → AtrTaxonomy.classify(classMap)
              →  TargetRegion from same classMap (no second ORT)
              →  if parse null → GarmentClassifier.classify(garmentCut)
manual chip   →  skip classify; atrClassIds() for mask only
```

---

## Test matrix (R2.0)

| Test | Command |
|------|---------|
| ATR unit | `./gradlew :shared:testDebugUnitTest --tests '*AtrTaxonomy*'` |
| Fixture harness | `python3 scripts/test_atr_classify.py` |
| Local image contract | `./gradlew :shared:testDebugUnitTest --tests '*LocalImage*'` |
| Assemble RC | `./gradlew :composeApp:assembleSideloadRelease` |

---

## Version / ship

| Field | R2.0 (shipped) | Tip (Audio) |
|-------|-----------------|-------------|
| versionName | `3.1.0-rc1` | `3.1.0-rc2` |
| versionCode | `59` | `60` |
| Tag | `v3.1.0-rc1` | `v3.1.0-rc2` |
| APK | GitHub Release via `release-apk.yml` | same |

Sideload note (from 3.0.16): stable keystore — updates install in place. Pre-3.0.16 still needs one uninstall.

---

## Success criteria

1. Auto on a worn abaya photo resolves `ABAYA` (or Jilbab/Kaftan family) via ATR, not always Kurta.
2. All 14 categories selectable as chips; Auto still default.
3. Fixture script + Kotlin tests agree on the same fixture set.
4. Create Studio never claims offline ready without graphs + sampler.
5. First home viewport still reads as one atelier composition (brand-led).

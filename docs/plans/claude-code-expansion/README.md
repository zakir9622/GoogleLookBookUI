# Claude Code — Expansion & improvement roadmap

**Source:** Claude Code planning session (`lookbook_expansion_roadmap`)  
**Baseline when written:** v2.9.5 — updated against **v3.0.8**  
**Canonical plan:** [`PLAN.md`](PLAN.md) · **Scorecard:** [`../COMPLETION.md`](../COMPLETION.md)

Phased roadmap for cloud/local model expansion, quality tooling, UI cleanup, and infrastructure. Written before v3 `HomeScreen` shipped; map legacy `StudioScreen` references to the home pager + `UnifiedStudioPane`.

**Related:** [`../lookbook-v3-followup/`](../lookbook-v3-followup/), [`../stable-release/`](../stable-release/).

## Cycles (from plan frontmatter)

| ID | Focus | Status |
|----|-------|--------|
| cycle1-inference-discovery | HF router discovery + inference image-edit | **DONE** |
| cycle2-quality-packs | BiRefNet, Real-ESRGAN, pro-v2-int8 manifest | **DONE** for BiRefNet/ESRGAN @ v3.0.3–3.0.4; **pro-v2-int8 HF still pending** |
| cycle3-ui-settings | Settings split, model health, generation feedback | **DONE** @ v3.0.5–3.0.6 |
| cycle4-speed-compliance | QNN EP, LCM, safety classifier, UI tests | **PARTIAL** — LCM step clamp tested @ 3.0.7; QNN needs EP package; ONNX NSFW classifier open |

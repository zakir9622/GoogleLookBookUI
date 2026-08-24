# Local-first mode

**Status:** Iteration 1 shipped this PR; iterations 2–3 scoped, not started
**Baseline:** v3.1.0-rc17 (`5903b0a`)
**Canonical plan:** [`PLAN.md`](PLAN.md)

Driven directly by Claude rather than Cursor (Cursor's usage was reported exhausted), from direct
product feedback: a non-functional-feeling News refresh button, a request to temporarily hide
Try-on app-wide without deleting it, a request for a global "local-only by default, cloud behind
one explicit toggle" mode, and a report that the newly-shipped Google AI Edge Gallery / LiteRT-LM
local models are slow on-device.

| # | Theme | Status |
|---|-------|--------|
| 1 | News refresh feedback + Try-on hidden app-wide + LiteRT-LM slowness investigated | **Shipped this PR** — compiles, full unit suite green |
| 2 | Global "Enable cloud models" toggle, anchored at `AppSettings.preflight()` | Scoped, not started |
| 3 | Studio tab cleanup + real "loading model" state for LiteRT-LM cold loads | Scoped, not started |

**Related:** [`../generation-transparency/`](../generation-transparency/) (the per-tab live log
this plan reuses rather than duplicates), [`../litert-lm-integration/`](../litert-lm-integration/)
(the feature whose on-device latency this plan investigates).

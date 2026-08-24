# Generation stability — image / video / code

**Status:** M1–M3 + M6 core **shipped**; M2 live-health UI + blank-frame **done @ v3.0.5**; M4 local image-gen weights **deferred** (scaffold only); M5 screenshot baselines **partial**. See [`../stable-release/`](../stable-release/).  
**Baseline:** v2.9.16 → execution through **v3.0.5**  
**Canonical plan:** [`PLAN.md`](PLAN.md)

| # | Theme | Status |
|---|-------|--------|
| M1 | Typed failures + correct fallback | **DONE** @ v3.0.1 |
| M2 | Live model health + generation budget | **DONE** @ v3.0.5 (UI + blank-frame + 2 KB floor) |
| M3 | Self-healing Gradio schema contracts | **DONE** @ v3.0.2 |
| M4 | Local on-device image generation | **DEFERRED** — `LocalImageGenerator` scaffold @ 3.0.5 |
| M5 | Test + visual verification harness | **PARTIAL** — scripts in CI; device baselines thin |
| M6 | Cleanup, changelog, KMP portability | **PARTIAL** — EpochClock/hooks done; iOS open |

**Related:** [`../lookbook-v3-followup/`](../lookbook-v3-followup/), [`../stable-release/`](../stable-release/).

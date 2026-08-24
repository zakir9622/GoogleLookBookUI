# LiteRT-LM / Google AI Edge Gallery integration

**Status:** Planned — not started  
**Baseline:** v3.1.0-rc16 (`main`)  
**Canonical plan:** [`PLAN.md`](PLAN.md)

Supplements [`../true-local/`](../true-local/) and [`../five-star-quality/`](../five-star-quality/).
Does **not** replace ONNX try-on / tiny-SD image generation — adds a **third on-device runtime**
for Google’s current GenAI stack (LiteRT-LM).

## Why this plan

[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) demonstrates on-device Gemma 4,
vision Q&A, audio scribe, and tool use via **LiteRT-LM** (`.litertlm` packs). Lookbook already
ships local Code via **MediaPipe** `.task` (maintenance mode per Google) and try-on/image via
**ONNX Runtime**. This plan migrates and extends the LLM/VLM path without breaking existing packs.

## Scope at a glance

| Phase | Delivers | User-visible |
|-------|----------|--------------|
| **L0** | Kotlin 2.3 + LiteRT-LM SDK spike | — |
| **L1** | Gemma 4 E2B Code pack + engine | Code Studio offline upgrade |
| **L2** | Gemma 4 vision assist | Garment / look describe, prompt help |
| **L3** | Audio Scribe (optional) | Offline transcript / translate |
| **L4** | FunctionGemma tools (optional) | Local studio assists |

## Out of scope

- Importing Gallery’s app or sharing its downloads (separate sandboxes)
- Replacing `lite-v1` / `pro-v1` / `local-sdturbo-v1` ORT graphs with LiteRT-LM
- Cloud studio changes (AUTO never cloud, free-tier cloud only — unchanged)

## Related

- [`../true-local/`](../true-local/) — original offline engine train
- [`../generation-transparency/`](../generation-transparency/) — benchmark harness for ORT paths
- [LiteRT-LM Android guide](https://developers.google.com/edge/litert-lm/android)
- [litert-community on Hugging Face](https://huggingface.co/litert-community)

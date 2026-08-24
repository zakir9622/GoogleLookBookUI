# Local model research (v3)

Open-source models evaluated for on-device use in The Lookbook.

**Device floor:** app `minSdk = 35` (Android 15+).

## Try-on (shipping)

| Pack | Role | Size | Status |
|------|------|------|--------|
| `lite-v1` | Garment seg + human parse (ONNX) | ~68 MB | **Required** — masks for Lite and Pro |
| `pro-v1` | SD1.5 FP16 + ControlNet | ~4.3 GB | **On HF manifest** (preferred Pro download) |
| `pro-v2-int8` | SD1.5 INT8 | ~2 GB | Export ready; HF upload pending |

**Constraint:** Pro cannot run without `lite-v1` human parsing. Do not remove lite pack until a replacement mask pipeline ships.

## Quality post-steps (optional packs)

| Pack | Model | Status |
|------|-------|--------|
| `birefnet-v1` | BiRefNet matting | **On HF** · runners fixed @ v3.0.4 |
| `realesrgan-v1` | Real-ESRGAN upscale | **On HF** · FP16 runner @ v3.0.4 |
| `gfpgan-v1` | GFPGAN face restore | Planned |

## Create / Code / Video (not local yet)

| Direction | Candidates | Blocker |
|-----------|------------|---------|
| Image gen | SD-Turbo / LCM (`local-sdturbo-v1`) | Weights + ONNX runner — `LocalImageGenerator` / `PackAwareLocalImageGenerator` scaffolded @ v3.0.5 |
| Code LLM | Qwen2.5-Coder 1.5B, Gemma 2B | ExecuTorch / MediaPipe / LiteRT-LM not in build |
| Video | — | Not practical on phones; cloud LTX-Video only |

**Current approach:** Hide non-runnable catalog entries from pickers; use cloud HF / Groq / OpenRouter for Create Studio until local packs are ready.

## Google open-source models on Android (Aug 2026)

**Verdict: Yes — Gemma can run locally on modern phones, but not in this build yet.**

| Framework | Models | Android fit | Lookbook status |
|-----------|--------|---------------|-----------------|
| **LiteRT-LM** (recommended) | Gemma 3 1B, Gemma 3n E2B/E4B, Gemma 4 | CPU/GPU/NPU on Pixel 8+, Samsung S23+; `.litertlm` INT4 ~1–2 GB RAM | **Planned** — catalog entry `local-gemma-planned`, pack id reserved |
| **MediaPipe LLM Inference** | Gemma 3 1B (`.task`) | Maintenance-only; Google directs new apps to LiteRT-LM | Not integrated |
| **ExecuTorch** | Custom ONNX/Torch exports | Possible for vision; LLM path heavier than LiteRT-LM | Not integrated |

**Recommendation:** For offline Code/News chat, ship **Gemma 3 1B INT4 via LiteRT-LM** as an optional ~1.5 GB pack on flagship devices (8 GB+ RAM, minSdk 35). Keep Qwen2.5-Coder 1.5B as an alternate if Hugging Face `.litertlm` exports are preferred for coding. Do **not** block on full multimodal Gemma 3n until RAM/thermal budgets are validated.

**Not feasible soon:** On-device FLUX-class image gen and short video — stay on cloud Spaces + HF Inference fallback.

References:
- [LiteRT-LM README](https://github.com/google-ai-edge/LiteRT-LM)
- [MediaPipe LLM Inference (Android)](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android)
- [LiteRT Hugging Face community](https://huggingface.co/litert-community)

## Session caching

`OrtSessionCache` reuses ONNX sessions per model path to cut cold-start latency on repeat try-on shots. Invalidate when pack root changes (re-download / verify).

## lite-v2 (research)

Potential improvements for a future `lite-v2` pack:

- Smaller garment seg (MobileSAM-class) for faster first layer
- Updated human parser (SCHP / Graphonomy export) for better abaya/hijab regions
- Quantized INT8 graphs where ORT mobile EP supports them

Export pipeline: train/export on desktop → validate with `scripts/benchmark-local.py` → publish to `Iamzakirzr/vestra-packs` manifest.

## References

- [ONNX Runtime Android](https://onnxruntime.ai/docs/get-started/with-java.html)
- [CreativeML OpenRAIL-M](https://huggingface.co/spaces/CompVis/stable-diffusion-license) (SD1.5)
- HF manifest: `https://huggingface.co/datasets/Iamzakirzr/vestra-packs`

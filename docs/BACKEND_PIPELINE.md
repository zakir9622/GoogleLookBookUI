# Backend pipeline — what happens when you generate

This document explains what The Lookbook does under the hood for each feature tier.

## Try-on (Lite — fast local compositor)

**Pack:** `lite-v1` (~68 MB) — `garment_seg.onnx` + `human_parse.onnx`

1. **Read images** — garment photo + person/model photo from disk or picker
2. **Garment segmentation** — U²-Net-style model extracts garment cutout (320×320)
3. **Category** — auto-classify cutout geometry (abaya, kurta, hijab, etc.)
4. **Human parsing** — ATR parser finds body region on person (512×512)
5. **Contour warp** — mesh garment onto target body region
6. **Harmonize** — match lighting between garment and person
7. **Backdrop** (optional) — re-segment person and paint studio scene
8. **Save** — JPEG + AI watermark + EXIF tag

**Typical time:** 3–15 s on mid-range phone (first run slower while ONNX loads).

## Try-on (Pro — on-device diffusion)

**Packs:** `pro-v1` (~4.3 GB) + **required** `lite-v1` for masks

1. **Structure** — human-parse mask from Lite pack
2. **Texture** — VAE encode person + garment latents
3. **Synthesis** — DDIM diffusion (20+ steps) with UNet + ControlNet residuals
4. **Decode** — VAE decode + paste-back outside mask
5. **Backdrop** — optional, uses Lite segmenter

**Typical time:** 30 s – several minutes depending on RAM/NPU and step count.

## Try-on (Cloud)

**Network:** Hugging Face Gradio Spaces (ZeroGPU quota applies)

1. Upload person + garment as base64 FileData payloads
2. Space queue → poll until complete
3. Download result image from Space CDN

**Fallback:** When ZeroGPU quota is spent, switch to **Lite/Pro** locally or wait for daily refill.

## Create Studio (Image / Video / Code)

- **HF Spaces** — same Gradio queue pattern
- **HF Inference** — FLUX Schnell text-to-image (needs HF token + monthly credits)
- **OpenRouter / Groq** — code generation via chat-completions API

Image gen automatically falls back from Spaces → Inference when quota errors occur.

## Pack verification lifecycle

```
Download → staging → sha256 check → ONNX smoke load → .complete marker → VERIFIED
```

Until `VERIFIED`, local engines report `PACK_VERIFY_PENDING` (not failed).

## Diagnostic export

Settings → Diagnostics → **Export run history** produces JSON with:
- Each run's capability, model, tier
- Per-stage timings (ms)
- Success/failure + error message

Send this file when reporting issues.

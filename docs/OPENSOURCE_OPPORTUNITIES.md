# Free / Open-Source Opportunities — quality without cost

Research log + action plan for improving The Lookbook using **free, open-source**
models, libraries, and infrastructure — **without compromising image quality**.
Compiled 2026-07-18. Sources listed at the bottom.

> TL;DR of the biggest wins:
> 1. **Kill the per-image cloud cost** — self-host a try-on model on **Hugging
>    Face ZeroGPU** (free H200) instead of paying Replicate.
> 2. **Make on-device fast + smaller** — INT8 quantize + switch ORT to the
>    **Qualcomm QNN** NPU path (proven by the open-source **Local Dream** app).
> 3. **Raise quality for free** — generate at 512 then **Real-ESRGAN upscale** +
>    **GFPGAN/CodeFormer face restore**; distill to 4–8 steps with **LCM/Hyper-SD**.
> 4. **Watch licenses** — most try-on model *weights* are non-commercial because
>    of the VITON-HD/DressCode datasets. Our SD + ControlNet + IP-Adapter base
>    avoids that trap and stays commercially clean.

---

## 1. Try-on models (free) — and the license reality

| Model | Base | Quality | License | Notes |
|---|---|---|---|---|
| **IDM-VTON** (`yisol/IDM-VTON`) | SDXL | Very high | code OK; **dataset non-commercial** | What our Cloud tier uses now. Heavy. |
| **Leffa** (`franciszzj/Leffa`, Meta) | SD | High, recent | **MIT** (verify dataset) | Best commercial-friendly candidate to trial. |
| **CatVTON** (`zhengchong/CatVTON`) | SD1.5, 899M params, <8 GB VRAM @1024×768 | High, compact | official **CC-BY-NC**; an **MIT fork** exists | Smallest → most mobile-friendly. ICLR 2025. |
| **Kolors Virtual Try-On** (`Kwai-Kolors/...`) | Kolors | Very high (10k❤ Space) | check | Strong; callable as a Space. |
| **OOTDiffusion / FitDiT / MagicTryOn (video) / MuGa-VTON (multi-garment) / DiffFit** | SD/DiT | High, task-specific | mixed | See the awesome-lists for the current frontier. |

**⚠️ The commercial-license trap.** Try-on models are usually trained on
**VITON-HD** and **DressCode**, which are **CC BY-NC (non-commercial)**. That
restriction flows to the *weights* regardless of the code's MIT/Apache license.
For a B2B seller product this matters.

**Our advantage:** the on-device Pro stack (Realistic Vision V5.1 + ControlNet-Depth
+ IP-Adapter-Plus) is **not** trained on those datasets — it's a general
commercially-licensed SD approach. Keep it as the **commercial-safe** path; treat
VTON-dataset models as "personal use / preview / research" or replace with a
model trained on a commercially-clean dataset (see the new **FIT** dataset).

---

## 2. On-device acceleration — the free path to fast, offline, high quality

**Local Dream** (`github.com/xororz/local-dream`) is the reference: an open-source
Android app running **SD1.5 and SDXL on Snapdragon NPUs**, fully offline. It uses:
- **Qualcomm QNN SDK** for NPU execution (SD1.5 on Hexagon V68+, SDXL on 8 Gen 3+),
- **Alibaba MNN** for CPU/GPU fallback,
- built-in **Real-ESRGAN / UltraSharpV2** upscalers, img2img + inpainting.

**What to adopt from it:**
1. **Switch ORT execution provider from NNAPI → [Qualcomm QNN EP](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html).**
   QNN targets the Hexagon NPU directly and is dramatically faster than NNAPI on
   Snapdragon. This is the single biggest on-device speed unlock. Free.
2. **[Qualcomm AI Hub](https://aihub.qualcomm.com/)** — free cloud service that
   compiles models to Snapdragon-optimized QNN/TFLite and benchmarks them on real
   devices. It already hosts optimized Stable Diffusion. Use it to produce our
   NPU-ready UNet/ControlNet.
3. **INT8 quantization** (`onnxruntime.quantization`, static with a small
   calibration set) — ~4.3 GB fp16 → **~1.5–2 GB**, runs on 8 GB phones. Keep
   activations at INT16/FP16 for quality. This is the #1 roadmap item already.
4. **MNN diffusion** as an alternative runtime if ORT/QNN coverage is incomplete
   for some ops.

**Step-count distillation (fewer steps = faster + lets us afford higher res):**
- **LCM-LoRA**, **SD-Turbo**, **Hyper-SD** — free LoRAs/checkpoints on HF that cut
  22 steps → **4–8 steps** (4–6× faster) with minimal quality loss. Apply to the
  SD1.5 base before export.

---

## 3. Raise image quality for free (no per-image cost)

- **Real-ESRGAN** (upscaler) — generate at 512, upscale 4× to 2048 for
  catalog-grade output at a fraction of the cost of high-res diffusion. Small ONNX,
  runs on-device or server. **Biggest quality-per-dollar win.**
- **GFPGAN / CodeFormer** (face restoration) — fixes the classic "AI face" problem
  on generated models. Small, free, on-device-able.
- **BiRefNet** (`ZhengPeng7/BiRefNet`) — SOTA free background/garment segmentation,
  much cleaner cutouts than u2netp (our current Lite segmenter).
- **InstantID / IP-Adapter-FaceID** — keep the **same model's face consistent
  across all shots in a shoot** (identity lock). Free.
- **Depth-Anything-V2** — already used for ControlNet structure (good choice).

A 512-gen → ESRGAN-upscale → face-restore pipeline gives high perceived quality
while keeping diffusion cheap/fast — the best "no compromise, no cost" tradeoff.

---

## 4. Free hosting / compute (a $0 Cloud tier)

| Option | Free allowance | Use for |
|---|---|---|
| **HF ZeroGPU** ([docs](https://huggingface.co/docs/hub/en/spaces-zerogpu)) | RTX Pro 6000 Blackwell (48/96 GB). Using others' Spaces: free 5 min/day. **Hosting your own needs PRO ($9/mo)** → 40 min/day + $1/10 min overflow | **Self-host IDM-VTON/Leffa/clean-base as a low-cost API** → replace per-image Replicate. See `docs/CLOUD_ZEROGPU.md` |
| **Modal** | **$30/mo** free credits, serverless GPU, scale-to-zero | Bursty inference without a standing server |
| **Google Colab** | Free T4 | Offline/batch jobs, the pack conversion notebook |
| **Qualcomm AI Hub** | Free compile + device benchmarking | On-device model optimization |

**Recommended:** stand up a **ZeroGPU Space** running our chosen try-on model and
point the app's Cloud tier at it instead of Replicate. For a B2B tool with modest
volume this is effectively **$0/image**, and it removes the Replicate billing
dependency entirely. Keep Replicate as the paid overflow for scale.

---

## 5. Concrete plan (prioritized, mapped to this repo)

### Track 1 — Free Cloud tier (fastest ROI, no app rearchitecting)
1. Build a **ZeroGPU Space** wrapping IDM-VTON (or Leffa for license safety) exposing
   a simple HTTP endpoint.
2. Point `supabase/functions/tryon` (or the app's `CloudConfig.tryOnUrl`) at the Space
   instead of Replicate. Same request shape.
3. Keep Replicate behind an env flag for overflow. → **per-image cost ≈ $0**.

### Track 2 — On-device: smaller + faster (make Pro run on normal phones)
4. **INT8-quantize** the existing validated pack → ~1.5–2 GB; lower `minSpec` to 8 GB.
5. **QNN execution provider** in `OrtGraph`/`OrtModel` (fall back to NNAPI/CPU) —
   big NPU speedup on Snapdragon.
6. **LCM/Hyper-SD LoRA** merged into the SD1.5 base before export → 4–8 steps.
7. Add **Real-ESRGAN** post-upscale (on-device ONNX) so we ship 512-gen but deliver
   1024–2048 output.

### Track 3 — Quality & correctness
8. Swap Lite's segmenter to **BiRefNet**; add **GFPGAN** face restore to both engines.
9. Add **InstantID/IP-Adapter-FaceID** so a shoot's shots share one model identity.
10. Trial **Leffa** (MIT) as a commercially-cleaner try-on model than IDM-VTON.

### Track 4 — Licensing hygiene (B2B)
11. Document the VITON-HD/DressCode non-commercial constraint in `MODEL_LICENSES.md`;
    keep the SD+ControlNet+IP-Adapter base as the commercial path; evaluate the
    **FIT** dataset for a commercially-clean fine-tune.

---

## Sources & useful links

**Models / demos**
- IDM-VTON — https://huggingface.co/spaces/yisol/IDM-VTON · paper https://hf.co/papers/2403.05139
- Leffa (Meta, MIT) — https://huggingface.co/franciszzj/Leffa
- CatVTON — https://github.com/Zheng-Chong/CatVTON · https://huggingface.co/zhengchong/CatVTON
- Kolors Virtual Try-On — https://huggingface.co/spaces/Kwai-Kolors/Kolors-Virtual-Try-On
- Awesome lists — https://github.com/Zheng-Chong/Awesome-Try-On-Models · https://github.com/minar09/awesome-virtual-try-on
- opentryon (SDKs) — https://github.com/tryonlabs/opentryon
- FASHN comparison — https://fashn.ai/blog/comparing-the-top-4-open-source-virtual-try-on-viton-models

**On-device**
- Local Dream (SD on Android NPU) — https://github.com/xororz/local-dream
- ONNX Runtime QNN EP — https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- ONNX Runtime NNAPI EP — https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html
- Qualcomm AI Hub — https://aihub.qualcomm.com/ · docs https://workbench.aihub.qualcomm.com/docs/hub/compile_examples.html
- Alibaba MNN — https://github.com/alibaba/MNN

**Free compute**
- HF ZeroGPU — https://huggingface.co/docs/hub/en/spaces-zerogpu · AoT compile https://huggingface.co/blog/zerogpu-aoti
- Modal — https://modal.com/

**Quality libs**
- Real-ESRGAN — https://github.com/xinntao/Real-ESRGAN
- GFPGAN — https://github.com/TencentARC/GFPGAN · CodeFormer — https://github.com/sczhou/CodeFormer
- BiRefNet — https://huggingface.co/ZhengPeng7/BiRefNet
- InstantID — https://github.com/InstantID/InstantID

# True local on Pixel (v3.1.0-rc8+)

**Status:** All studios have a true-local path when packs are installed  
**Device floor:** Pixel 8+ / 8 GB RAM · `minSdk 35`

## What genuinely works offline on-device today

| Surface | Status | How |
|---------|--------|-----|
| **Try-on Lite / Pro** | **Works** | `lite-v1` / `pro-v1` packs + ORT (rc6 R8 keep fixes Pixel SIGABRT) |
| **Audio Speak** | **Works** | Android **system TTS** (Google/OEM voices) — no pack |
| **Audio voice change** | **Works** | Mic record + DSP knobs — no pack |
| **Quality upscale/matte** | **Works** | Real-ESRGAN / BiRefNet packs |
| **Image Create** | **Works when pack installed** | `AndroidTxt2ImgEngine` + `local-sdturbo-v1` (~1.06 GB) |
| **Image Edit** | **Works when pack installed** | Same pack + `vae_encoder.onnx` (v3+) img2img |
| **Video** | **Works when pack installed** | Honest **still-clip** MP4 from local keyframe (not diffusion video) |
| **Code** | **Works when pack installed** | MediaPipe + `local-gemma-v1` (~530 MB Gemma 3 1B INT4) |

## Unlock offline studios

1. Install **3.1.0-rc8+**
2. Settings → Model packs → download:
   - **local-sdturbo-v1** (~1.06 GB) — Create / Edit / Video still-clip
   - **local-gemma-v1** (~530 MB) — Code Studio
3. Airplane mode → each studio → Generate

Packs live on `Iamzakirzr/vestra-packs`.

## Honesty rules

- Never claim Image Create is offline-ready without pack graphs ≥ 1 MB each + CLIP vocab  
- Never route Create Studio through Pro try-on UNet (9-ch inpaint ≠ txt2img)  
- Local video is a **still-clip**, not Wan/LTX-class diffusion video  
- System TTS is real offline Speak — not a neural Kokoro substitute in quality, but it works without downloads  

## Stretch

- Optional neural TTS pack (`local-tts-v1`) as upgrade over system voices  
- Larger on-device coders when device RAM allows  

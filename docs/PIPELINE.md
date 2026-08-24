# Multi-conditioning try-on pipeline

The engines are structured around a staged, multi-conditioning architecture
(`shared/.../engine/pipeline/`) instead of naive image-to-image. Decoupling the
stages is what produces structural alignment and removes the "garment
hallucinated onto a body" failure mode.

## Stages (`ConditioningStage`)

1. **STRUCTURE** — "Mapping layout skeleton…". A pose/parse (ControlNet-class)
   condition locks the generated body to the target model's pose, so limbs and
   drape stay geometrically correct. On-device this is derived from the Lite
   pack's human-parsing model; a dedicated pose/depth ControlNet slots in via
   `ProPackConfig.structureModel`.
2. **TEXTURE** — "Extracting garment details…". The garment's visual features
   are injected into the UNet cross-attention (IP-Adapter) so garment identity
   comes from image features, not a hallucination-prone text description.
   `ProPackConfig.ipAdapter` names the image-encoder file.
3. **SYNTHESIS** — "Synthesizing localized NPU diffusion…". The diffusion loop
   runs under structure + texture + `PromptStyle` guidance.

## Prompt engineering (`PromptStyle`)

Shared by the on-device Pro engine and the Cloud engine so both use identical
guidance:

- **CFG 7.0**, **22 steps** (mobile-safe point in the 20–25 band; avoids OOM).
- **Positive:** masterpiece, ultra-high-quality studio portrait, photorealistic,
  cinematic edge-lighting, highly detailed skin texture, pores visible, DSLR,
  35 mm lens, RAW camera photo, high-end editorial fashion photography, natural
  fabric physics and draping.
- **Negative:** CGI, 3D render, anime, painting, digital illustration, deformed
  limbs, plastic skin, smooth cartoon texture, airbrushed, AI artifacts, generic
  generation, low quality, mutated geometry, blurry, watermark.

Try-on models are image-conditioned, so these tokens are *auxiliary*: they steer
realism and suppress the CGI/cartoon failure modes while the IP-Adapter carries
garment identity. A pack tuned for a low-CFG model (e.g. CatVTON prefers ~2.5)
may override `guidanceScale`/`inferenceSteps` in its `config.json`.

## The on-device Pro stack (P7) — exact models

The Pro pack runs a full SD1.5 + ControlNet-Depth + IP-Adapter chain from
commercially-usable weights:

| Role | Model | Repo | License |
|---|---|---|---|
| Base photorealism | `Realistic_Vision_V5.1_fp16-no-ema.safetensors` | `SG161222/Realistic_Vision_V5.1_noVAE` | CreativeML OpenRAIL-M (commercial OK) |
| Structure (ControlNet **Depth**) | `control_v11f1p_sd15_depth_fp16.safetensors` | `lllyasviel/ControlNet-v1-1_fp16_safetensors` | OpenRAIL |
| Texture (IP-Adapter Plus) | `ip-adapter-plus_sd15.safetensors` + image encoder | `h94/IP-Adapter` | Apache-2.0 |
| Depth estimator (Stage A) | Depth-Anything-V2-Small | `depth-anything/Depth-Anything-V2-Small` | Apache-2.0 |

**Depth, not OpenPose**, is deliberate: a depth map preserves the *volume* of
flowing modest-wear (abaya, kaftan, dupatta drape) as well as fitted western
wear, which a pose skeleton flattens.

### Mandatory conversion + build
`.safetensors` are PyTorch — they do **not** run on Android. The bridge:

```bash
cd ml
bash download_pro_models.sh              # fetch source weights (~6 GB)
python convert_pro_pack.py --src pro_src --out exports/pro-v1   # → ONNX FP16
python export_depth.py --out exports/pro-v1/depth.onnx
python manifest_gen.py exports/          # publish to the packs repo
```

Conversion needs a **GPU box with ≥16 GB RAM** (not CI/agent). The Android
runtime (`SdControlNetPipeline`) consumes the produced ONNX at **512×512, 20–25
steps, CFG 7.0**, gated to flagship NPUs by `DeviceProbe`. Pack is ~3.5–4 GB.

## What still requires model weights (honesty note)

The Kotlin pipeline **orchestrates tensors; it does not contain the models.**
Full IP-Adapter + ControlNet photorealism on-device requires these files shipped
in the Pro pack and referenced by `ProPackConfig`:

- a base SD/inpaint UNet (`unet.onnx`),
- an IP-Adapter image encoder (`ipAdapter`),
- a pose/depth ControlNet (`structureModel`),
- VAE encoder/decoder.

`PipelineRequirements.isFullyConditioned` reports whether the active pack has
them; without the IP-Adapter/ControlNet files the Pro engine falls back to the
CatVTON-style concat path. Producing the fully-conditioned pack is the
`ml/train/` + export work (needs GPU budget). **Until then, the Cloud tier is
the way to get IP-Adapter+ControlNet-quality output today** — it maps the same
`ConditioningInputs`/`PromptStyle` onto a hosted model (`docs/CLOUD_SETUP.md`).

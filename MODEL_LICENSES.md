# Model licenses

Every model this app downloads or ships, with the licence it is distributed under and where it
comes from. The app itself is separate — this file covers **model weights only**.

Weights are **not** re-hosted where the upstream repository can be linked directly. Packs served
from `Iamzakirzr/vestra-packs` are either assembled from public weights or mirror an upstream
file; both are listed with their origin so the licence obligation follows the weights.

Verified against the live Hugging Face repositories on 2026-08-24.

## On-device (LiteRT-LM · Google AI Edge)

| Pack | Model | Upstream | Licence |
|---|---|---|---|
| `local-qwen3-06b-v1` | Qwen3 0.6B INT4 | [`litert-community/Qwen3-0.6B-int4`](https://huggingface.co/litert-community/Qwen3-0.6B-int4) | Apache-2.0 |
| `local-gemma-4-e2b-v1` | Gemma 4 E2B IT | [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | [Gemma Terms of Use](https://ai.google.dev/gemma/terms) |
| `local-gemma-v1` | Gemma 3 1B IT (INT4, MediaPipe `.task`) | [`litert-community/Gemma3-1B-IT`](https://huggingface.co/litert-community/Gemma3-1B-IT) | [Gemma Terms of Use](https://ai.google.dev/gemma/terms) |
| `local-functiongemma-v1` | FunctionGemma 270M (mobile actions) | `google/functiongemma-270m-it` derivative | [Gemma Terms of Use](https://ai.google.dev/gemma/terms) |

**Gemma obligations.** The Gemma Terms of Use are not Apache-2.0. Redistribution must carry the
same use restrictions and the [Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy),
and modified weights must be marked as modified. The app downloads Gemma packs to the user's
device rather than bundling them in the APK, and shows the licence on each catalog row.

**Qwen3 (Apache-2.0)** carries no use restrictions. Attribution and the licence text must travel
with any redistribution.

## On-device (LiteRT — plain `Interpreter`/`CompiledModel`, not LiteRT-LM)

| Pack | Model | Upstream | Licence |
|---|---|---|---|
| `local-bonsai-image-v1` | Bonsai Image 4B — ternary-weight FLUX.2-klein-architecture DiT, int4 | [`litert-community/Bonsai-Image-ternary-4B`](https://huggingface.co/litert-community/Bonsai-Image-ternary-4B), following upstream [`prism-ml/bonsai-image-ternary-4B`](https://huggingface.co/prism-ml/bonsai-image-ternary-4B) | Apache-2.0 |

Text-to-image only (no reference-image conditioning in this export) — `local-bonsai-image-v1`
never appears under Image Edit. Files link directly to `litert-community/Bonsai-Image-ternary-4B`;
none of the ~4 GB of weights are re-hosted. The on-device Kotlin pipeline (`BonsaiImageEngine`,
`BonsaiTokenizer`, `BonsaiMath`) is ported from the Apache-2.0 reference app at
[`john-rocky/hf-to-litertlm`](https://github.com/john-rocky/hf-to-litertlm/tree/main/bonsai_image_work/device/BonsaiAppAndroid)
(Daisuke Majima); the ported files carry that attribution in their doc comments.

## On-device (ONNX Runtime)

| Pack | Model | Origin | Licence |
|---|---|---|---|
| `local-sdturbo-v1` | tiny-SD / SD-Turbo class txt2img + img2img, FP16 ONNX | assembled from public SD1.x-lineage weights | [CreativeML Open RAIL-M](https://huggingface.co/spaces/CompVis/stable-diffusion-license) |
| `pro-v1` | SD1.5 + ControlNet-Depth + IP-Adapter | SD1.5 lineage | CreativeML Open RAIL-M |
| `lite-v1` | Garment segmentation + human parsing | task-specific ONNX | see pack `config.json` |
| `birefnet-v1` | BiRefNet matting (Swin-Tiny) | BiRefNet | MIT |
| `realesrgan-v1` | Real-ESRGAN x2 | Real-ESRGAN | BSD-3-Clause |

**RAIL-M obligations.** CreativeML Open RAIL-M is an *open but use-restricted* licence: the
attached use restrictions must be passed on to end users, and derivatives must not be used for
the prohibited purposes listed in the licence. It is not an OSI-approved open-source licence.

## Runtimes

| Component | Licence |
|---|---|
| LiteRT / LiteRT-LM (`com.google.ai.edge.litertlm`) | Apache-2.0 |
| LiteRT `Interpreter`/`CompiledModel` (`com.google.ai.edge.litert:litert`) | Apache-2.0 |
| ONNX Runtime (Android) | MIT |
| MediaPipe LLM Inference (legacy Gemma 3 `.task` path) | Apache-2.0 |

## Cloud models

Cloud generation is off by default and runs only against free-tier hosted endpoints
(Hugging Face Spaces / Inference Providers, Groq, OpenRouter). No weights are redistributed;
each model remains under its own licence and its host's terms of service. The app never ships a
paid provider.

## Adding a model

Before adding any model to `LocalModelCatalog`:

1. Record the upstream repository and its licence in the table above.
2. Prefer referencing upstream weights in `manifest.json` over re-hosting them.
3. Put the licence name in the catalog entry's `license` field — it is shown in the picker.
4. For Gemma-family weights, keep the Terms of Use and Prohibited Use Policy links intact.

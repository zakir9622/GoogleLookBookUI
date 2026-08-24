# Model pack tooling

Python scripts that produce the downloadable model packs the app fetches from Hugging Face Hub. Nothing here ships inside the APK.

## Setup

```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Scripts

| Script | Output |
|---|---|
| `export_lite_pack.py` | `exports/lite-v1/` — INT8 ONNX models for the Lite engine (garment segmentation + human parsing) |
| `convert_pro_pack.py` | `exports/pro-v1/` — **production** fully-conditioned Pro (SD1.5 + ControlNet-Depth + IP-Adapter) |
| `export_catvton_legacy_pack.py` | `exports/catvton-legacy/` — CatVTON 3-file experiment pack (never write as pro-v1) |
| `export_diffusion_pack.py` | **Removed** — stub that refuses to run (old CatVTON→pro-v1 collision) |
| `export_dev_pack.py` | `exports-dev/pro-dev-v1/` — **private, non-commercial** dev pack from CatVTON research weights (devOnly-flagged) |
| `build_models_pack.py` | `exports/studio-models-v1/` — studio-models (casting gallery) pack from the owner's base-model photos |
| `manifest_gen.py` | `exports/manifest.json` — pack manifest (ids, versions, sha256, sizes, device gates, devOnly flags) |
| `colab_convert_pro_pack.ipynb` | **One-click free** Pro-pack conversion on Google Colab's free GPU → uploads `pro-v1/` ONNX pack + manifest to Hugging Face |
| `train/` | Turn-key training program for commercially-shippable Pro weights (dataset prep → fine-tune → distill) |
| `eval/compute_metrics.py` | Per-category SSIM/LPIPS benchmark (modest-wear categories reported separately) |

## Free on-device Pro pack (no local GPU needed)

The Pro engine (SD1.5 + ControlNet-Depth + IP-Adapter) makes generation **photoreal,
offline, and $0 per image** — but the `.safetensors` weights must first be converted to
ONNX FP16, which needs a GPU. If you don't have one, use the **Colab notebook**:

1. Open `ml/colab_convert_pro_pack.ipynb` in [Google Colab](https://colab.research.google.com/)
   (File → Upload notebook).
2. Runtime → Change runtime type → **T4 GPU** (free tier).
3. Runtime → **Run all**. Paste an HF **write** token when the upload cell asks.
4. It downloads the weights, exports every ONNX component, builds the pack + manifest,
   and uploads them to `Iamzakirzr/vestra-packs`.
5. Point `VestraApp.kt`'s `PACKS_MANIFEST_URL` at the printed manifest URL and rebuild.

The fused-UNet export (step 6) is version-sensitive; if it errors, the printed traceback +
residual shapes are enough to patch the runtime input contract in one pass.

## Publishing a pack release

1. Run the export script(s); verify outputs with the checks each script prints.
2. `python manifest_gen.py exports/` — regenerates `manifest.json`, bumping versions for changed packs.
3. Upload the changed files + `manifest.json` to the Hugging Face packs repo (`huggingface-cli upload`).

The app polls `manifest.json`; installed packs with a lower version show "Update available".

## Licensing

Before publishing any exported model, record its upstream license in `MODEL_LICENSES.md` and confirm it permits redistribution + commercial use. Research-only checkpoints must not be published to the production packs repo.

#!/usr/bin/env bash
# Fetch the commercially-usable source weights for the on-device Pro pack.
# These are .safetensors (PyTorch) — NOT runnable on Android as-is. Run
# convert_pro_pack.py afterwards to produce the ONNX FP16 pack the app loads.
#
# Requires: pip install "huggingface_hub[cli]"
# Disk: ~6 GB of source weights. Run on a machine with a GPU + >=16 GB RAM
# (the conversion step, not this download, is the heavy part).
set -euo pipefail

DEST="${1:-pro_src}"
mkdir -p "$DEST"

echo "==> Base photorealism model (Realistic Vision V5.1) — OpenRAIL-M"
huggingface-cli download SG161222/Realistic_Vision_V5.1_noVAE \
  Realistic_Vision_V5.1_fp16-no-ema.safetensors \
  --local-dir "$DEST/realistic_vision"

echo "==> ControlNet v1.1 Depth (fp16) — OpenRAIL"
# fp16 safetensors mirror (lllyasviel's own repo ships .pth, not fp16 safetensors)
huggingface-cli download comfyanonymous/ControlNet-v1-1_fp16_safetensors \
  control_v11f1p_sd15_depth_fp16.safetensors \
  --local-dir "$DEST/controlnet_depth"

echo "==> IP-Adapter Plus (SD1.5) + image encoder — Apache-2.0"
huggingface-cli download h94/IP-Adapter \
  models/ip-adapter-plus_sd15.safetensors \
  models/image_encoder/model.safetensors \
  models/image_encoder/config.json \
  --local-dir "$DEST/ip_adapter"

echo "==> Depth estimator source (Depth-Anything-V2-Small) — Apache-2.0"
huggingface-cli download depth-anything/Depth-Anything-V2-Small \
  depth_anything_v2_vits.pth \
  --local-dir "$DEST/depth" || \
  echo "   (optional; export_depth.py can also pull MiDaS-small)"

echo "Done. Sources in '$DEST/'. Next: python convert_pro_pack.py --src $DEST"

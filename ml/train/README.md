# Training program — commercially-shippable Pro weights

Everything here is ready to run the day GPU budget exists. The recipe follows
CatVTON's published approach (train only the UNet's self-attention parameters,
~49M, over the OpenRAIL-licensed SD-1.5-inpainting base), so the resulting
weights are **ours** and commercially shippable — see `../MODEL_LICENSES.md`.

## Dataset layout (modest-wear-first taxonomy)

```
dataset/
├── train/
│   ├── <category>/<pair_id>/
│   │   ├── person.jpg    # model wearing the garment
│   │   ├── garment.jpg   # the same garment, flat-lay/hanger/catalog
│   │   └── meta.json     # {"category": "...", "source": "...", "license": "..."}
└── eval/                 # same layout, never trained on
```

Categories (priority order): `full_coverage` (abaya, jilbab, kaftan, burqa),
`headscarf` (hijab, shayla, dupatta), `dress` (incl. maxi, kurta-length),
`upper_body` (incl. kurta, tunic, modest long-sleeve), `lower_body`
(incl. wide-leg, palazzo, maxi skirt).

Targets: ≥3k pairs/category to fine-tune usefully; ≥10k for strong results.
Every pair's `meta.json` MUST carry a license we can train on commercially.

## Pipeline

```bash
# 1. Normalize raw pairs into the layout above (masks precomputed with the
#    same u2netp + SCHP models the app ships, so train/inference match).
python prepare_dataset.py raw_images/ dataset/

# 2. Fine-tune self-attention only (CatVTON recipe).
#    A single A100-80GB: ~2 days @ 512x384, batch 32. ~$150-400 on spot.
accelerate launch train_tryon.py --data dataset/train --out runs/tryon-v1

# 3. Distill to 8-12 steps for phones (LCM-style consistency distillation).
accelerate launch distill.py --teacher runs/tryon-v1 --out runs/tryon-v1-lcm

# 4. Export for the app. CatVTON 3-file experiments go to catvton-legacy/;
#    production pro-v1 must use convert_pro_pack.py (fully conditioned).
python ../export_catvton_legacy_pack.py --weights runs/tryon-v1-lcm --out ../exports/catvton-legacy
# For shippable pro-v1 (ControlNet + IP-Adapter + text):
#   python ../convert_pro_pack.py --src pro_src --out ../exports/pro-v1
python ../manifest_gen.py ../exports/
```

## GPU options when budget lands

| Provider | Setup | Rough cost for the full program |
|---|---|---|
| RunPod / Lambda spot A100 | ssh + this repo | $200–600 |
| Replicate trainings | managed | $300–900 |
| Google Colab Pro+ (A100) | slowest, cheapest start | <$100 for pilot runs |

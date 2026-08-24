#!/usr/bin/env python3
"""Fine-tune try-on weights we own — the CatVTON recipe over SD-1.5-inpainting.

Trains ONLY the UNet's self-attention parameters (~49M) so the run is cheap and
the base model's OpenRAIL license carries through to weights we can ship
commercially. Conditioning follows the app's runtime contract exactly
(shared/.../pro/DiffusionEngine.kt): person and garment latents concatenated
along width; 9-channel inpaint input [noisy | mask | masked].

Usage:
  accelerate launch train_tryon.py --data dataset/train --out runs/tryon-v1 \
      [--size 384x512] [--batch 8] [--steps 30000] [--lr 5e-5]

Pairs whose meta.json license is "unknown" are excluded.
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

BASE_MODEL = "runwayml/stable-diffusion-inpainting"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--size", default="384x512")
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--steps", type=int, default=30_000)
    parser.add_argument("--lr", type=float, default=5e-5)
    parser.add_argument("--save-every", type=int, default=2_000)
    args = parser.parse_args()

    import torch
    import torch.nn.functional as F
    from accelerate import Accelerator
    from diffusers import AutoencoderKL, DDPMScheduler, UNet2DConditionModel

    width, height = (int(v) for v in args.size.split("x"))
    accelerator = Accelerator(mixed_precision="fp16")
    device = accelerator.device

    vae = AutoencoderKL.from_pretrained(BASE_MODEL, subfolder="vae").to(device).eval()
    unet = UNet2DConditionModel.from_pretrained(BASE_MODEL, subfolder="unet")
    noise_scheduler = DDPMScheduler.from_pretrained(BASE_MODEL, subfolder="scheduler")

    # CatVTON recipe: freeze everything except self-attention (attn1).
    trainable = []
    for name, param in unet.named_parameters():
        if "attn1" in name:
            param.requires_grad = True
            trainable.append(param)
        else:
            param.requires_grad = False
    print(f"trainable params: {sum(p.numel() for p in trainable) / 1e6:.1f}M")

    dataset = TryOnPairs(args.data, width, height)
    loader = torch.utils.data.DataLoader(
        dataset, batch_size=args.batch, shuffle=True, num_workers=4, drop_last=True
    )
    optimizer = torch.optim.AdamW(trainable, lr=args.lr, weight_decay=1e-2)
    unet, optimizer, loader = accelerator.prepare(unet, optimizer, loader)

    zero_context = torch.zeros(args.batch, 1, unet.module.config.cross_attention_dim
                               if hasattr(unet, "module") else unet.config.cross_attention_dim,
                               device=device, dtype=torch.float16)

    step = 0
    scale = 0.18215
    while step < args.steps:
        for batch in loader:
            with torch.no_grad():
                person = batch["person"].to(device, dtype=torch.float32)
                garment = batch["garment"].to(device, dtype=torch.float32)
                mask = batch["mask"].to(device, dtype=torch.float32)

                person_lat = vae.encode(person).latent_dist.sample() * scale
                garment_lat = vae.encode(garment).latent_dist.sample() * scale
                masked = person * (1 - mask)
                masked_lat = vae.encode(masked).latent_dist.sample() * scale
                lat_mask = F.interpolate(mask, size=person_lat.shape[-2:])

                # Try-on concat along width: [person | garment].
                clean = torch.cat([person_lat, garment_lat], dim=-1)
                cond = torch.cat([masked_lat, garment_lat], dim=-1)
                mask_c = torch.cat([lat_mask, torch.zeros_like(lat_mask)], dim=-1)

                noise = torch.randn_like(clean)
                timesteps = torch.randint(0, 1000, (clean.shape[0],), device=device)
                noisy = noise_scheduler.add_noise(clean, noise, timesteps)
                model_in = torch.cat([noisy, mask_c, cond], dim=1)

            pred = unet(model_in.half(), timesteps, encoder_hidden_states=zero_context).sample
            loss = F.mse_loss(pred.float(), noise, reduction="mean")

            accelerator.backward(loss)
            optimizer.step()
            optimizer.zero_grad()
            step += 1
            if step % 100 == 0 and accelerator.is_main_process:
                print(f"step {step}/{args.steps} loss {loss.item():.4f}")
            if step % args.save_every == 0 and accelerator.is_main_process:
                save(accelerator.unwrap_model(unet), vae, args.out / f"step-{step}")
            if step >= args.steps:
                break

    if accelerator.is_main_process:
        save(accelerator.unwrap_model(unet), vae, args.out)
        print(f"done → {args.out}. Next: distill.py, then export_diffusion_pack.py")


def save(unet, vae, out: Path) -> None:  # noqa: ANN001
    (out / "unet").mkdir(parents=True, exist_ok=True)
    unet.save_pretrained(out / "unet")
    vae.save_pretrained(out / "vae")


class TryOnPairs:
    """Loads (person, garment, agnostic-mask) triples from the dataset layout."""

    def __init__(self, root: Path, width: int, height: int) -> None:
        self.items = [
            pair for pair in sorted(root.glob("*/*"))
            if (pair / "person.jpg").exists()
            and (pair / "garment.jpg").exists()
            and json.loads((pair / "meta.json").read_text()).get("license", "unknown") != "unknown"
        ]
        if not self.items:
            raise SystemExit(f"No licensed pairs under {root}")
        self.width, self.height = width, height
        random.shuffle(self.items)

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, index: int):  # noqa: ANN001
        import numpy as np
        import torch
        from PIL import Image

        pair = self.items[index]
        person = self._image(pair / "person.jpg")
        garment = self._image(pair / "garment.jpg")
        mask_path = pair / "mask.png"
        if mask_path.exists():
            mask = np.array(Image.open(mask_path).convert("L").resize((self.width, self.height)))
            mask = torch.from_numpy(mask[None].astype("float32") / 255.0)
        else:
            # No precomputed mask: train mask-free on the torso band (BooW-style
            # pseudo supervision); precompute_masks with the app's SCHP model is
            # strongly preferred.
            mask = torch.zeros(1, self.height, self.width)
            mask[:, self.height // 6 : self.height * 5 // 6, self.width // 6 : self.width * 5 // 6] = 1.0
        return {"person": person, "garment": garment, "mask": mask}

    def _image(self, path: Path):  # noqa: ANN001
        import numpy as np
        import torch
        from PIL import Image

        img = Image.open(path).convert("RGB").resize((self.width, self.height))
        arr = np.asarray(img).astype("float32") / 127.5 - 1.0
        return torch.from_numpy(arr).permute(2, 0, 1)


if __name__ == "__main__":
    main()

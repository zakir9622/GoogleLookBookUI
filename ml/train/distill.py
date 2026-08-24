#!/usr/bin/env python3
"""Consistency-distill the fine-tuned try-on UNet to 8–12 phone-speed steps.

LCM-style distillation: the student learns to jump along the teacher's DDIM
trajectory, cutting inference from ~30 steps to 8–12 with minor quality loss —
the difference between ~40s and ~12s per shot on a flagship NPU.

Usage:
  accelerate launch distill.py --teacher runs/tryon-v1 --data dataset/train \
      --out runs/tryon-v1-lcm [--steps 15000]

Reads the same dataset layout as train_tryon.py. The output directory is what
export_diffusion_pack.py takes as --weights.
"""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--teacher", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=15_000)
    parser.add_argument("--size", default="384x512")
    parser.add_argument("--batch", type=int, default=4)
    args = parser.parse_args()

    import copy

    import torch
    import torch.nn.functional as F
    from accelerate import Accelerator
    from diffusers import AutoencoderKL, DDIMScheduler, UNet2DConditionModel

    from train_tryon import TryOnPairs

    width, height = (int(v) for v in args.size.split("x"))
    accelerator = Accelerator(mixed_precision="fp16")
    device = accelerator.device

    vae = AutoencoderKL.from_pretrained(args.teacher / "vae").to(device).eval()
    teacher = UNet2DConditionModel.from_pretrained(args.teacher / "unet").to(device).eval()
    student = copy.deepcopy(teacher)
    for p in teacher.parameters():
        p.requires_grad = False
    ddim = DDIMScheduler.from_pretrained("runwayml/stable-diffusion-inpainting", subfolder="scheduler")
    ddim.set_timesteps(50)

    loader = torch.utils.data.DataLoader(
        TryOnPairs(args.data, width, height), batch_size=args.batch, shuffle=True, drop_last=True
    )
    optimizer = torch.optim.AdamW(student.parameters(), lr=1e-5)
    student, optimizer, loader = accelerator.prepare(student, optimizer, loader)
    context_dim = teacher.config.cross_attention_dim
    zero_context = torch.zeros(args.batch, 1, context_dim, device=device, dtype=torch.float16)

    scale = 0.18215
    step = 0
    while step < args.steps:
        for batch in loader:
            with torch.no_grad():
                person = batch["person"].to(device)
                garment = batch["garment"].to(device)
                mask = batch["mask"].to(device)
                person_lat = vae.encode(person).latent_dist.sample() * scale
                garment_lat = vae.encode(garment).latent_dist.sample() * scale
                masked_lat = vae.encode(person * (1 - mask)).latent_dist.sample() * scale
                lat_mask = F.interpolate(mask, size=person_lat.shape[-2:])
                clean = torch.cat([person_lat, garment_lat], dim=-1)
                cond = torch.cat([masked_lat, garment_lat], dim=-1)
                mask_c = torch.cat([lat_mask, torch.zeros_like(lat_mask)], dim=-1)

                noise = torch.randn_like(clean)
                index = torch.randint(0, len(ddim.timesteps) - 1, (1,)).item()
                t = ddim.timesteps[index].to(device)
                t_next = ddim.timesteps[index + 1].to(device)
                noisy = ddim.add_noise(clean, noise, t.expand(clean.shape[0]))

                # Teacher takes ONE fine DDIM step; the student must match the
                # teacher's next-point prediction from the coarser start.
                teacher_in = torch.cat([noisy, mask_c, cond], dim=1).half()
                teacher_eps = teacher(teacher_in, t, encoder_hidden_states=zero_context).sample
                teacher_next = ddim.step(teacher_eps.float(), t, noisy).prev_sample
                target_in = torch.cat([teacher_next, mask_c, cond], dim=1).half()
                target_eps = teacher(target_in, t_next, encoder_hidden_states=zero_context).sample

            student_eps = student(teacher_in, t, encoder_hidden_states=zero_context).sample
            loss = F.mse_loss(student_eps.float(), target_eps.float())
            accelerator.backward(loss)
            optimizer.step()
            optimizer.zero_grad()
            step += 1
            if step % 100 == 0 and accelerator.is_main_process:
                print(f"distill {step}/{args.steps} loss {loss.item():.5f}")
            if step >= args.steps:
                break

    if accelerator.is_main_process:
        (args.out / "unet").mkdir(parents=True, exist_ok=True)
        accelerator.unwrap_model(student).save_pretrained(args.out / "unet")
        vae.save_pretrained(args.out / "vae")
        print(f"distilled student → {args.out}")


if __name__ == "__main__":
    main()

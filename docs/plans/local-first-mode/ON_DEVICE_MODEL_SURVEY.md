# On-device model survey — what is actually shippable

> **Method:** every size below was read from the live Hugging Face file listing on
> 2026-08-23, not from model cards or memory. Totals are the sum of the files a working
> pipeline actually needs, not the whole repo.
> **Scope:** `litert-community` — the org upstream of Google's AI Edge Gallery — plus
> what `Iamzakirzr/vestra-packs` (a **dataset** repo, not a model repo) ships today.

## The headline, stated plainly

**Google's LiteRT community does not currently publish an on-device text-to-image model
that fits in a phone app.** All three are 4–10 GB. The app's existing hand-assembled
tiny-SD ONNX pack (1.06 GB) is *smaller and more practical than anything upstream offers*.

So for the stated goal — improve on-device image generation by adopting Google's Edge
stack — the honest answer is that **swapping the image model is not the win**. The wins
are in the text/audio packs, where upstream ships models that are dramatically smaller
than what this app currently downloads.

## What the app ships today (verified in `Iamzakirzr/vestra-packs`)

| Pack | Size | Format | Used for |
|---|---|---|---|
| `realesrgan-v1` | 4.9 MB | ONNX | upscale |
| `lite-v1` | 68.6 MB | ONNX | Lite try-on |
| `birefnet-v1` | 224 MB | ONNX | matting |
| `local-functiongemma-v1` | 284 MB | `.litertlm` | tool calling |
| `local-gemma-v1` | 555 MB | `.task` | legacy code |
| `local-sdturbo-v1` | **1.06 GB** | ONNX | image gen / edit / still-clip |
| `local-gemma-4-e2b-v1` | **2.47 GB** | `.litertlm` | code · vision · audio STT |
| `pro-v1` | 3.99 GB | ONNX | Pro try-on |

The 2.47 GB Gemma 4 pack is the single biggest cause of "it takes so much time": it
backs Code, vision assist, **and** speech-to-text, and it cold-loads on the CPU backend
(`preferLiteRtLmGpu` defaults to `false`).

## Tier 1 — ship these (high probability)

### `litert-community/Qwen3-0.6B-int4` — 331 MB · `.litertlm` · Apache-2.0
**Shipped in this change.** The decisive property is that it is a `.litertlm` file, so it
is a **drop-in for the engine the app already runs** — same `Engine(EngineConfig(modelPath,
backend, cacheDir))` call, zero new runtime. 7.5× smaller than the Gemma 4 pack.

The repo ships two builds; the app uses the **no-think** one
(`qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`), which pre-closes `<think></think>` so
the model answers directly — the card measures it **~2× quicker on a CPU backend**, which
is exactly this app's default. It also carries prefill signatures `8/64/128/256/512/1024`,
so a short prompt runs a small prefill instead of always paying the 1024-token cost.

Weights are referenced **directly from litert-community** rather than re-hosted, so the
dataset repo stays small and the weights stay canonical.

### `gemma-4-E2B-it-gpu.litertlm` — 2.0 GB (vs the 2.47 GB build in use)
Same model, same repo, **470 MB smaller**, GPU-targeted. A free saving on the existing
pack whenever the GPU backend is enabled. Not taken here — it should land together with
the GPU-default decision, which needs a real device to validate.

## Tier 2 — worth doing, but needs new plumbing

Both are `.tflite`, and the app has **ONNX Runtime and LiteRT-LM but no raw-TFLite path**.
That's real work, not a config change — which is why they are not in this change.

| Model | Size | Replaces / fills |
|---|---|---|
| `litert-community/whisper-tiny` (`i8`) | **41 MB** | STT, currently done by the 2.47 GB Gemma 4 pack — a **60× smaller** model for the job |
| `litert-community/Matcha-TTS` | ~92 MB (4 files) | `local-tts-v1`, which is `runnable = false` today — would make neural TTS real |

## Tier 3 — rejected, with the numbers

| Model | Real size | Why not |
|---|---|---|
| `Z-Image-Turbo-LiteRT` | **~9.8 GB** | text encoder alone is 3.55 GB; six 908 MB DiT chunks |
| `FLUX.2-klein-4B-LiteRT` | **~6.7 GB** generate path (~10.5 GB repo) | three 912 MB text-encoder shards before any DiT |
| `Bonsai-Image-ternary-4B` | **~4.3 GB** | smallest real DiT, still 4× the current tiny-SD pack, and a 4B transformer per step |
| `Qwen2.5-Coder-3B-Instruct` | **3.43 GB** | *larger* than the Gemma 4 pack it would replace |

## Incidental finding

`LiteRtLmPacks.AUDIO_SCRIBE_FILE` is `"whisper-large-v3-turbo.litertlm"`, a file that is
published nowhere and referenced by no resolver — `AndroidLocalAudioTranscriber` resolves
`GEMMA4_FILE` instead. The constant is dead; transcription really does run on the 2.47 GB
Gemma 4 pack. Harmless today, but it misreads as though a Whisper pack exists. Deleting it
belongs with the Tier-2 whisper-tiny work.

## Order of work

1. **Qwen3 0.6B** — done here; biggest latency win per byte, no new runtime.
2. **whisper-tiny** — needs a TFLite path, but 41 MB against 2.47 GB is the largest single
   saving available anywhere in this table.
3. **Matcha-TTS** — same runtime work as (2), reuse it; retires a dead catalog row.
4. **GPU default + the 2.0 GB Gemma build** — together, once a device can validate GPU
   delegate coverage.
5. **On-device image generation** — no upstream option is viable at phone size. Effort is
   better spent on the existing tiny-SD ONNX path than on adopting a 4–10 GB DiT.

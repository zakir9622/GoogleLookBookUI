# Device matrix soak (Q3) — Pixel 8+/9

Run after **rc11** (`latest` APK) is installed. Score each cell 1–5; target average ≥4.5.

## Setup
- Device: Pixel 8 / 9, airplane mode toggle ready
- Packs: `lite-v1`, `pro-v1` (or skip Pro if FP16 still fails → expect Lite AUTO fallback), `local-sdturbo-v1`, `local-gemma-v1`
- HF token optional (credits may be empty — Spaces / local must still work)

## Matrix

| Surface | Offline · pack ready | Offline · pack missing | Online · cloud selected | Online · local selected |
|---------|----------------------|------------------------|-------------------------|-------------------------|
| Image Create | local PNG | soft fail + CTA packs | selected Space/Inference | local PNG (no HTTP) |
| Image Edit | img2img PNG | CTA packs | cloud edit | local edit |
| Video / Clip | still-clip MP4 | cloud or CTA | cloud video | still-clip |
| Code | Gemma text | cloud | cloud LLM | Gemma |
| Audio | system TTS | — | TTS first | TTS first |
| Try-on AUTO | Lite or Pro | CTA | N/A | N/A |
| Try-on Pro (forced) | Pro or friendly ORT copy | CTA | N/A | N/A |

## Pass criteria
- No Java crash / blank fatal dialog with raw ORT dumps for Pro incompat (friendly copy or Lite fallback)
- Pager tabs do not wipe each other’s results
- Wardrobe shows **CLIP** frame for MP4 (not broken Coil still)
- Diagnostics share includes abrupt logcat hints after a forced native kill (optional)

## Record
Paste scores into the PR / issue when done:

```
Stability: _
Honesty: _
Clarity: _
Offline: _
Cloud fallback: _
Avg: _
```

# Changelog — The Lookbook

## 3.1.0-rc23
- **Fixed a real prompt-leak bug, found directly from a user report**: typing a prompt in one
  studio tab (Image/Video/Code/Audio), then visiting News/Chat and tapping a headline, could
  overwrite that prompt with the headline's text. Root cause: `HomeScreen.openNewsChat()` and
  `VestraNavHost`'s `onOpenNewsChat` callback both wrote the headline into
  `GenerativeViewModel.prompt` — the single `StateFlow` every studio tab reads — even though
  `NewsChatScreen` already manages its own separate local chat-input state and never reads that
  flow. Both dead writes deleted; the per-tab isolation mechanism itself
  (`GenerativeViewModel.bindStudio`/`StudioBag`) was already correct.
- **Wired the image-edit/img2img entry point for Appium**: the "Add reference image" button and
  its attached-photo thumbnail on the Image tab (`composer_add_reference`,
  `composer_reference_thumb`) now carry stable `testTag`s — these existed as constants but were
  never actually applied to the composables. Also tagged Home's Settings entry button
  (`home_open_settings`).
- **Added a real Appium test suite** (`appium/`) covering prompt isolation across tabs (a direct
  regression test for the leak above), local image/code/chat generation reaching a genuine
  terminal state, the image-edit flow end to end, and the Processing Mode card. Honestly
  documented as unexecuted: no device, emulator, or Appium server exists in the environment that
  wrote it — see `appium/README.md`.

## 3.1.0-rc22
- **Started porting lookbookweb's design/UX language, local-only, per
  `docs/plans/lovable-parity-local-first/PLAN.md`.** Added four per-modality accent color tokens
  (`VestraColors.ModalityImage/Video/Code/Audio`, brass-family tints — Loom Ink's identity stays)
  and a derived `RadiusTokens` corner-radius scale; wired the Studio header label to its
  modality's accent. Added a subtle press-lift micro-interaction to `GlassCard` (scale to ~97%
  on press, gated by reduced-motion) — lookbookweb's `press-3d` language ported at Compose-native
  cost. Confirmed the Syne/Outfit typography pairing this plan called for was already in place.
- **Fixed misleading "Cloud by default" studio copy.** The Image/Video/Code studio subtitle said
  "Cloud by default" regardless of whether cloud models were actually enabled — since
  `cloudModelsEnabled` defaults to `false` app-wide, that text was simply wrong for most users.
  Now reads "On-device only (cloud is off)" when the master toggle is off, or names the local
  pack to install either way.
- **The News/Chat window is now Appium-testable**: refresh button, headline cards, and chat
  message bubbles carry stable `testTag`s, alongside the generation-flow coverage from rc21.
- Updated `docs/DRAWBACKS.md` and the plan's own README with an honest status: this is a slice
  of the full lookbookweb-parity plan, not the whole thing — see those docs for exactly what's
  landed and what's still open.

## 3.1.0-rc21
- **Local LiteRT-LM models now fall back to CPU automatically if the GPU delegate fails to
  initialize**, found via a user's Pixel 9 screenshot: `Local Qwen3 0.6B (fast) could not load:
  Failed to create engine: INTERNAL: ERROR: [...litert_compiled_model_executor.cc...]`. Before
  this fix, a failed GPU init had no fallback, so tapping "Retry load" repeated the identical
  failing GPU path forever. `LiteRtLmEngine.initialize()` now tries GPU first when requested,
  catches a GPU init failure, logs it, and retries on CPU — the model still loads, just slower.
- **The app is now testable with Appium/UiAutomator and similar external automation tools.**
  Compose's `Modifier.testTag` is invisible outside Compose's own UI-test framework unless the
  app opts in via `testTagsAsResourceId`; that flag is now set once at the composable root
  (`MainActivity.kt`). A new `TestTags` catalog
  (`composeApp/src/main/kotlin/com/zakir/vestra/ui/TestTags.kt`) gives every core interactive
  and result element in the generation flow a stable id: prompt input, model chip, assist
  toggle, send/stop, each home tab, every `GenerativeState` result card (image/video/audio/code
  streaming and ready/transcription/failed), the live generation console, retry/cancel, model
  pack install/handshake buttons, and each row in the model picker sheet (cloud and on-device).
- **Added `docs/DRAWBACKS.md`** — an honest, non-marketing list of this app's current real
  limitations (local model quality tradeoffs, partial NNAPI offload, no committed on-device
  benchmark yet, testability coverage gaps, no iOS target), kept up to date as items close.

## 3.1.0-rc20
- **Fixed a real on-device crash in local Create Studio**, found via a user's Pixel 9 screenshots:
  `ORT_INVALID_ARGUMENT — Invalid rank for input: timestep Got: 0 Expected: 1`. The local
  txt2img engine built the timestep tensor with no shape (defaulting to a scalar); the published
  `local-sdturbo-v1/unet.onnx` requires rank 1. Reproduced the exact error against the real graph
  before and after the fix to confirm.
- **Fixed local generations being mislabeled as cloud**, found via a user's diagnostics export:
  a CHAT run recorded `modelId: "llama33-70b-groq"` while its own note field said
  `local-qwen3-06b-v1` actually ran (cloud was off). The live console showed "Connecting to FLUX.1
  Schnell" / "Connecting to Llama 3.3 70B (Groq)" immediately before local generation actually
  started. Fixed across image/code/video/audio generation, the Chat and Code Studio diagnostics
  records, and the Diagnostics screen's "Tier" field (was hardcoded to CLOUD for every run).
- **Video now hard-stops offline** like image/code/audio already did, instead of a soft "Network
  probe uncertain — trying cloud anyway…" that burned time with no network to reach.
- **Local still-clip video holds its pack in use** through both the still-image generation and
  the MediaCodec encode that follows it, matching the pattern used everywhere else a local pack
  backs a multi-stage operation.
- **Pack handshake toasts no longer leak machine ACK strings** (`HANDSHAKE_OK`) — use the existing
  human-readable summary everywhere a handshake result reaches the user.

## 3.1.0-rc19
- **Live generation output, everywhere:** tapping Generate now streams real model output as
  it's produced — News Chat and Code Studio append tokens live (`GenerativeState.CodeStreaming`,
  `ChatViewModel.streamLocalReply`), local image generation (tiny-SD/LCM and Bonsai) reports
  live per-step progress instead of a single static "please wait". No stage is simulated.
- **Two real, on-device-model-verified bugs fixed in local Create Studio** — found and confirmed
  by running the actual published `local-sdturbo-v1` ONNX weights end-to-end on real hardware
  math (not just code review), per the standing "test the models, don't trust the code" rule:
  - `OrtGraph.timestepTensor` was missing an FP16 branch; the pack's `unet.onnx` declares
    `timestep tensor(float16)`, so every generation threw `ORT_INVALID_ARGUMENT`.
  - `LcmScheduler.step()` combined the UNet's raw noise prediction directly instead of first
    converting it to a predicted denoised sample, and never re-injected noise between steps —
    both required by the model's LCM distillation. Rewritten to match diffusers'
    `scheduling_lcm.py` exactly; verified against a real 4-step generation that produces a
    genuine, if soft, image instead of statistical noise.
  - Local image-to-image edit additionally ignored `strength`: the denoise loop always started
    from the schedule's highest timestep even though the reference image was only noised to a
    partial level. Fixed to slice the timestep schedule to match, mirroring diffusers'
    `get_timesteps()` — img2img now denoises from the correct noise level instead of collapsing
    to a near-black frame.
- **Real-ESRGAN quality upscale now reaches local Create Studio.** `realesrgan-v1`'s own catalog
  description already promised "auto-upscale after try-on or Create" — it only ever ran for
  try-on. Wired the same `QualityPostProcessor` into `AndroidTxt2ImgEngine` and
  `BonsaiImageEngine` so an installed pack now upscales locally-generated images too.

## 3.1.0-rc18
- **Bonsai Image 4B (LiteRT):** second on-device text-to-image engine, `local-bonsai-image-v1` —
  ternary-weight FLUX.2-klein-architecture DiT via LiteRT `Interpreter`/XNNPACK (~4 GB, text-to-image
  only). Selectable alongside tiny-SD in the Create Studio ON-DEVICE picker; Edit always uses tiny-SD.
- Plain `com.google.ai.edge.litert:litert` runtime added alongside the existing LiteRT-LM engine.

## 3.1.0-rc17
- **LiteRT-LM deep integration:** warm engine cache (no per-shot cold load), 90s inference timeout
- **Offline hard-stop:** Code Studio and Chat fail closed when offline without local pack
- **FunctionGemma:** selectable in Code ON-DEVICE picker; tool callbacks wired to studio prompt/tier
- **Audio scribe picker:** Generate transcribes attached clip when scribe model selected
- **Vision assist:** feedback when reference photo cannot be read
- **Per-pack readiness:** Gemma 3 / Gemma 4 / FunctionGemma show independent install state in picker

## 3.1.0-rc14
- **DoD stability pass:** live HF `pro-v1` verified fully-conditioned; CatVTON exporter quarantined off `pro-v1`
- Composer honesty: remove Steps/CFG/Seed UI (never reached cloud); audio fashion assist enriches speech
- Model Health dropdown uses runtime `effectiveSupport` (cooldown/failures), not static catalog
- Video + audio Gradio `predict` honor wall `deadlineMs` + poll timeout (same as image)
- Offline image/audio hard-stop when local unavailable — no cloud loop
- Audio failures map `CloudFailure` → health kinds; CI runs `integration-edge-cases.py`

## 3.1.0-rc10
- **Five-star Q1:** per-studio session bags (pager tabs no longer wipe each other)
- OrtGraph safe session + output size caps; local packs mark in-use during generate
- Still-clip MediaCodec presentation timestamps; human handshake labels
- Prefer local Create/Edit/Code/Video when offline; honor cloud selection when online
- Help + product blurb updated for true-local; Clip studio naming

## CI / releases
- **Release APK only on `main`:** merges/pushes to main publish the rolling `latest` GitHub Release
- Feature-branch pushes no longer create preview releases (PR runs Android CI checks only)
- Publishing `latest` prunes any other leftover release tags

## 3.1.0-rc9
- **Pack device handshake:** Settings → Engines & packs and Model packs gain **Verify link** / **Verify all**
- Re-checks files + graphs on device and returns `HANDSHAKE_OK` / `HANDSHAKE_FAIL` with wired studios listed

## 3.1.0-rc8
- **True local for every studio:** Image Create/Edit, Video still-clip, Code (Gemma), Audio (system TTS) — not try-on only
- **Image Edit offline:** `vae_encoder` img2img via `local-sdturbo-v1` v3+
- **Video offline:** honest H.264 still-clip from on-device keyframe (`local-stillclip-v1`)
- **Code offline:** MediaPipe + published `local-gemma-v1` (~530 MB)
- Catalog / preflight / studio copy updated; airplane-safe generate when local packs ready

## 3.1.0-rc7
- **True local Image Create:** published `local-sdturbo-v1` (~994 MB tiny-SD ONNX FP16) to HF packs; catalog `runnable=true`
- Assemble tooling: `scripts/assemble-local-sdturbo-pack.py` (from public tiny-SD ONNX)
- Studio copy: download pack from Model packs for offline Create
- Continues rc6: Pixel try-on ORT R8 fix + cloud studio reliability

## 3.1.0-rc6
- **Try-on crash fix:** R8 keep `ai.onnxruntime.**` — Pixel SIGABRT was `NodeInfo.<init>` NoSuchMethodError during Lite generate
- **Cloud Image:** Prefer FLUX Schnell Space by default; mark SDXL Lightning unsupported (Space API 404); fix 402 credit copy (was mislabeled as token permissions); capability-aware Inference rejection hints
- **Cloud Audio:** Default Edge-TTS; budget 45s with budget-aware polls so Kokoro falls back instead of hanging ~90s
- Continues true-local work from rc5 (system TTS, SD-Turbo engine)

## 3.1.0-rc5
- **True local Audio:** Android system TTS offline (personas → device voices) + DSP knobs
- **True local Image engine:** `AndroidTxt2ImgEngine` ORT denoise loop wired (`SAMPLER_WIRED=true`); needs `local-sdturbo-v1` pack weights to run
- **Airplane-safe studios:** Image/Audio skip cloud API-key preflight when local engines are ready
- **Honesty:** system TTS reports `local-tts-system`; SD-Turbo picker shows green when pack graphs installed
- **Pack tooling:** `export_image_gen_pack.py` writes `pack.json` + optional tokenizer copy; `verify-local-sdturbo-pack.py`
- Catalog: `local-tts-system` Ready offline; SD-Turbo status “Engine ready · pack weights not on device”
- Plan: `docs/plans/true-local/PLAN.md`

## 3.1.0-rc4
- **Local model picker honesty:** Create Studio ON-DEVICE list uses `forStudioPicker` — Real-ESRGAN / BiRefNet / GFPGAN quality packs no longer appear as Image generators; SD-Turbo / local TTS / local video show scaffold · weights-not-published status
- **Audio mic + voice change:** Record short PCM/WAV on-device, apply local DSP knobs (record → transform → play); `RECORD_AUDIO` permission
- **Cloud audio hosts:** Edge-TTS → `innoai/Edge-TTS-Text-to-Speech` (`tts_interface`); Kokoro → Remsky ZeroGPU (`generate_speech_from_ui`); MMS-TTS demoted (HF Inference often rejects); default audio = Kokoro
- **Cloud video:** Wan2 fails faster (short poll) then falls back to LTX; rate-limit cooldown messaging
- **UX:** Fix double “Space Space” in offline 404 copy
- **Try-on crash hardening:** Soft-wrap ORT session create / UnsatisfiedLinkError; yield before heavy graphs; catch native Throwable on Lite/Pro generate path

## 3.1.0-rc3
- **Image edit timeouts:** Gradio poll GETs capped at ~12s (no more 60–75s stuck on “Space poll 1/N”)
- Honor the image deadline inside Space wake/poll loops; skip wake retries when budget is tight
- After Qwen (or another primary) burns the 120s window, grant a 45s grace pass for InstructPix2Pix fallback

## 3.1.0-rc2
- **Audio Studio:** new home tab — cloud TTS (MMS-TTS Inference, Kokoro Space, Edge/OpenVoice Space)
- **Voice personas:** Amina, Noor, Layla, Yasir, Omar, Sam, Rana, Kai (named varieties)
- **Local voice changer:** on-device DSP knobs — pitch, speed, formant, warmth, clarity (no pack required)
- **Local TTS scaffold:** `local-tts-v1` + `LocalAudioGenerator` (`TTS_RUNNER_WIRED=false` until weights)
- Honest Settings / model picker entries for audio

## 3.1.0-rc1
- **Big release R2 (true limits):** full ATR Auto classification for all garment categories; single-pass human parse on generate
- **Garment chips:** complete taxonomy (Abaya, Jilbab, Kaftan, Hijab, Niqab, Dupatta, Headscarf, Shalwar, Kurta, Lehenga, Dress, Upper, Trousers, Full coverage) + Auto
- **Real-input harness:** `scripts/test_atr_classify.py` + `scripts/fixtures/atr/*.json` (12 worn-photo shapes); Kotlin `AtrTaxonomyTest` mirrors fixtures
- **UI — Loom Ink:** cool mist + brass + teal-ink atelier; stronger brand hero; less card clutter on Packs intro
- **On-device Create Studio:** `Txt2ImgPipeline` scaffold (`SAMPLER_WIRED=false`); honest cloud-only Image/Video/Code until HF weights
- Plan: `docs/plans/big-release-r2/`

## 3.0.16
- Stable sideload keystore + soft network preflight (stop false offline blocks)

## 3.0.15
- Live gen console + ticking countdown; diagnostics share off main thread

## 3.0.14
- Garment pick no longer loads `human_parse.onnx`; connection-abort UX ≠ offline

## 3.0.13
- Offline ≠ Cooling down; Lite soft verify; trim-memory no longer clears ORT on UI_HIDDEN

## 3.0.12
- ORT CPU default; soft startup verify; Prefer NNAPI toggle (off)

## 3.0.11
- Abrupt-exit session watchdog; low-memory + logcat FATAL scrape

## 3.0.10
- **ZeroGPU UX:** account quota no longer shows misleading “Cooling down · 1m” — chip says **ZeroGPU empty · refills daily**
- After account ZeroGPU fail, skip other HF Spaces and try Inference fallbacks; error CTA becomes **Choose model**

## 3.0.9
- **Auto-troubleshooting:** uncaught crashes append to `diagnostics/crash_log.txt` (never auto-cleared) with classified `likelyCause`
- Continuous `app_trace.log` breadcrumbs (screen route) + rotating size cap
- Diagnostics: last-crash card, **Share troubleshooting bundle**, manual clear only for crash/trace

## 3.0.8
- Diagnostics export includes **logcat snippet** (warnings+) + app version in the JSON bundle
- Plan **COMPLETION.md** scorecard for Claude expansion + v3 follow-up (~95% in-repo done)

## 3.0.7
- **M4 LocalImageEngine:** `AndroidLocalImageGenerator` validates installed `local-sdturbo-v1` graphs (rejects scaffold placeholders); Create Studio stays on cloud until real weights + sampler
- **cycle4:** `DiffusionSteps` LCM clamp (4–8) extracted + unit-tested; export scaffold sets `lcmDistilled`
- **M5:** `scripts/catalog-matrix.py` + `verify-all-models.sh` fold local `runnable` flags into the report

## 3.0.6
- **C4 SettingsScreen split:** widgets + general/cloud/engines/appearance section files; orchestrator ~380 lines (was ~1,180)
- Durable-storage **primary CTA** moved off Appearance — pack download (`rememberPackDownloadStarter`) + Packs screen own enable flow; Settings shows status/tip only
- **Honesty polish:** `PackAwareLocalImageGenerator.isReady` false until runner wired; `pro-v2-int8` catalog `runnable=false` until HF; on-device picker “Coming soon” for unpublished packs
- Hostname sanitize in cloud failure hints; QNN comment honesty; `visual-verify.sh --dry-run`; accesslint routes expanded; release notes Android 15+

## 3.0.5
- **Live model health UI:** picker, Usage, Settings, and preflight show cooldown / verified labels from `ModelHealthTracker` (not static Ready)
- Health records success/failure for **code + video** as well as image
- **Blank-frame reject:** Android luminance MAD check after download; image size floor raised to 2 KB
- Scaffold `LocalImageGenerator` + pack-aware wiring in Create Studio (still `runnable = false` until weights)
- Unit tests: `ModelHealthTrackerTest`, validator 2 KB floor

## 3.0.4
- **Quality pack integration:** Real-ESRGAN runner feeds FP16 `input` + `denoise_strength` (was silent no-op via single float32 OrtModel)
- BiRefNet matte applies **sigmoid** on logits before resize (was min–max normalize)
- Integrity verify smoke-runs Real-ESRGAN; catalog sizes corrected (~224 MB / ~5 MB)
- `realesrgan-v1` minRam gate lowered to 2 GB in export metadata; integration script smokes both quality packs
- **Local model crash hardening:** pack in-use refcount; block uninstall/update while generating; invalidate ORT session cache before pack file replace; rethrow cancel; soft-fail quality OOM; harden OrtModel output bounds; BackdropCompositor shares session cache
- **Stable release plan:** `docs/plans/stable-release/` — R0 (this cut) vs R1 perfect (offline Create Studio, pro-v2-int8 HF, live health UI)
- Pro unavailable copy prefers **pro-v1** (matches HF manifest); docs clarify **minSdk 35 / Android 15+**

## 3.0.3
- Published **birefnet-v1** (~224 MB) and **realesrgan-v1** (~5 MB) to `Iamzakirzr/vestra-packs` manifest
- Download from **Settings → Model packs**; matte refine + upscale activate when installed
- `scripts/build-and-publish-quality-packs.py` for future quality-pack releases

## 3.0.2
- **Generation stability M2–M6 (remaining):** global image deadline (120s) with remaining-time stage text; Gradio wakeRetries=1 + budget-derived maxPolls
- **M3:** `GradioSchemaClient` live `/info` payloads; removed guessing 1-arg Space fallbacks; HF discovery only for known Inference routes
- **M5:** `visual-verify.sh --compare`, `compare-screenshots.py`, `verify-all-models.sh`, `e2e-matrix.sh`
- **M6:** `EpochClock` replaces `System.currentTimeMillis` in commonMain; `DiagnosticsHook` per-run handles (no concurrent clobber); stop silent Space→Inference rewrite on token save
- Catalog: `local-sdturbo-v1` reserved; BiRefNet/Real-ESRGAN marked downloadable when packs ship; `ml/export_image_gen_pack.py` scaffold

## 3.0.1
- **Generation stability (Claude plan M1/M2):** `CloudFailure` typed errors; image fallback chain correctly advances models (fixes root-cause `continue` bug); per-candidate preflight inside loop; `ModelHealthTracker` with exponential cooldown; stronger `CloudOutputValidator` (1 KB min + dimension check); video no longer hard-requires HF Space; 402 skips remaining Inference candidates
- Removed duplicate `deepseek-r1-free-or` catalog entry (migration to `openrouter-free`)
- Unit tests: `CloudFailureTest`, updated `GenerativeCloudServiceTest` fixtures

## 3.0.0
- Image edit fallback no longer hits broken InstructPix2Pix HF Inference (nscale HTTP 400)
- Qwen Image Edit → InstructPix2Pix Space chain; migrate stale inference edit selection
- DNS / offline errors map to friendly "No internet" instead of raw host resolution text
- FLUX Space failures suggest HF Inference fallback when token is configured
- Usage ledger failures prefix selected model when fallback chain exhausts
- Google Gemma 3 local LLM documented as feasible via LiteRT-LM (catalog placeholder)

## 2.9.14
- Quality plan: `QualityRating` maps catalog scores to 1–5★ (5★ = READY + score ≥ 90)
- Cloud downloads validated (reject empty/corrupt images and videos; retry fallback chain)
- News chat uses the same LLM fallback chain as Code studio (Groq → OpenRouter → HF)
- Bypass filter assist on by default for Image/Video (fewer false safety blocks)
- Lite try-on applies BiRefNet matte refinement when `birefnet-v1` pack is installed
- Human parse uses declared 512×512 input; model picker shows star rating + sorts by quality
- Saving HF token migrates image gen to FLUX Inference when Space defaults were selected

## 2.9.3
- Model fallback chains for video, cloud try-on, and code (tries the next free model when one is busy or missing a key)
- LTX-Video payload aligned to live Space schema (null image fields, 704×512, 2s / CFG 1)
- InstructPix2Pix uses 8 steps to fit free ZeroGPU seconds
- OpenRouter free models: read `reasoning` when `content` is null
- Model picker lists Ready models first

## 2.9.2
- Fixed the biggest cause of failed cloud generation: once a Hugging Face
  account's daily ZeroGPU allowance is spent, HF rejects every Space call that
  carries the token instantly with an empty `event: error` / `data: null`, even
  though the same request still runs anonymously. Space calls now retry without
  the token, so image generation keeps working after the allowance runs out
- Explain empty Gradio errors as a likely spent ZeroGPU allowance rather than an
  unexplained failure
- Point Qwen Image Edit at a distilled mirror of the Space: the official one
  rejects every REST call outright, and 8 steps instead of 50 fits the free
  allowance
- Show the bundled Lite pack as installed as soon as it finishes seeding,
  instead of only after the next app launch

## 2.9.1
- Fixed image generation and editing against live Hugging Face Spaces: image
  arguments are now sent as Gradio `FileData` objects, so Qwen Image Edit and
  InstructPix2Pix no longer fail validation with an empty `event: error` /
  `data: null` response
- Support Spaces on Gradio 4 (`/call`) as well as Gradio 5 (`/gradio_api/call`)
- Read the result image from anywhere in a Space's output, fixing
  InstructPix2Pix (image is the 4th output) and OOTDiffusion (gallery)
- Retry Spaces that are waking or restarting, then fall back to another free
  Space when the selected one is out of ZeroGPU quota
- Report out-of-quota and rate-limited Spaces in plain language instead of raw
  Gradio errors
- Only Hugging Face Spaces can serve try-on, image and video; a stored HF
  Inference model is migrated to a curated Space and the correction is saved
- Default try-on is now OOTDiffusion (verified end-to-end); IDM-VTON, CatVTON
  and SDXL Lightning are marked degraded after live failures
- Settings names the Lite pack as the reason Pro try-on is unavailable

## 2.9.0
- Home: “What would you like to do” action list first; Core Try-on centered below
- Image / Video / Code studios: searchable in-composer model picker (name search)
- Local Lite/Pro always selectable; selecting a pack sets the matching engine tier
- HF: clearer Gradio empty-error messages; default image edit → Qwen; InstructPix2Pix marked degraded
- Stop listing warm HF Inference image models that cannot run via Spaces

## 2.8.0
- Looks gallery: tap opens look detail; delete confirmation; favorite a11y labels
- Video studio results ingest into Looks gallery
- In-app Privacy Policy screen (offline) + Settings About link
- Export local content reports from Settings → Storage & privacy
- Help: search semantics, email support CTA, privacy/report FAQ topics
- Cloud usage empty state → Open Image studio
- Try-on result: favorite + open gallery
- Atelier home respects reduced motion
- Deep-link visual verification (`lookbook://screen/*`, `scripts/visual-verify.sh`)

## 2.7.7
- Saffron FilterChips (no Material purple selected state)
- About + Privacy moved to top of Settings

## 2.7.3–2.7.6
- Cancel / Back recovery for try-on and cloud studios
- Gallery empty CTAs, Report/Share on cloud results
- Model chip → Settings; preflight Open Settings
- Composer/home a11y; deep-link screencap tooling

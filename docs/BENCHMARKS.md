# Lookbook ORT benchmarks

**Plan:** generation-transparency A0  
**Last updated:** 2026-08-23 (rc17)

## LiteRT-LM (Gemma 4 / vision / audio)

**Pending on-device numbers.** Instrumented harness ships with rc17; run when a `.litertlm` pack is installed:

```bash
./gradlew :composeApp:connectedSideloadDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zakir.vestra.LiteRtLmBenchmarkTest
```

| Artifact | Role |
|----------|------|
| `composeApp/.../LiteRtLmBenchmarkTest.kt` | Cold load + single-turn generate when pack present |
| `shared/.../litert/LiteRtLmEngine.kt` | Production engine wrapper |
| `scripts/assemble-local-gemma4-pack.py` | Download + pack layout from litert-community |

Logcat tag: `LookbookLiteRtLm`.

Default backend: **CPU**. Opt-in GPU via Settings → Engines → LiteRT-LM GPU.

## On-device numbers (Pixel 9 / flagship)

**Pending.** The instrumented harness and harvest script are shipped; run on a connected device when available:

```bash
bash scripts/benchmark-on-device.sh
```

That writes:

- [`docs/benchmarks/on-device-ort-latest.json`](benchmarks/on-device-ort-latest.json) — raw JSON from the test
- This file — human-readable table (device model, SDK, EP probe, per-graph cold/warm ms)

Harness entry points:

| Artifact | Role |
|----------|------|
| `composeApp/.../OnDeviceOrtBenchmarkTest.kt` | Instrumented test via production `OrtModel` / `OrtGraph` / `ProOrtSessions` |
| `shared/.../lite/OrtEpProbe.kt` | Logs available EPs + registration probe (CPU / NNAPI / XNNPACK / QNN) |
| `scripts/benchmark-on-device.sh` | `connectedSideloadDebugAndroidTest` → pull JSON → regenerate this doc |

Logcat tags: `LookbookBench`, `LookbookOrtEp`.

## Desktop CPU reference (NOT on-device)

These numbers come from `scripts/benchmark-local.py` on the bundled `lite-v1` assets using desktop `onnxruntime` with `CPUExecutionProvider`. They are **not** representative of Android NNAPI/QNN or app cold/warm session caching — use only as a sanity check that graphs load and run.

**Raw JSON:** [`docs/benchmarks/desktop-lite-v1-reference.json`](benchmarks/desktop-lite-v1-reference.json)

| Graph | Input | Avg (ms) | Notes |
|-------|-------|----------|-------|
| `garment_seg.onnx` | 320×320 | 296.6 | 3 runs, session recreated each run |
| `human_parse.onnx` | 512×512 | 516.2 | 3 runs, session recreated each run |

Re-run locally:

```bash
python3 scripts/benchmark-local.py
```

## Production EP policy (reminder)

- Pro path: **CPU + `NO_OPT`** via `ProOrtSessions` — QNN never enabled (FP16 rewrite hazard).
- NNAPI: opt-in via `OrtEpPolicy.preferNnapi` (default false after Pixel 9 SIGSEGV).
- Lite path: `OrtModel` with EP probe logging; session cache via `OrtSessionCache`.

## Related changes (rc15)

| Item | Status |
|------|--------|
| A0 harness | Shipped — device numbers TBD |
| A1 `OrtSessionCache` in Pro pipeline | Shipped — `SdControlNetPipeline` reuses cached graphs |
| A3 `DeviceSpec.minSdk` | Wired in `ModelPackManager.deviceMeets()` |
| B2 elapsed timer | Shipped — `ResultPane` + live console header |
| B3 reduce motion | Shipped — `GenerationScreen` + `DevelopShader` |

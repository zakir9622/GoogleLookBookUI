# Appium test suite — The Lookbook

Real, executable Appium (UiAutomator2) tests against `com.zakir.vestra`, written against the
stable `testTag`s catalogued in `composeApp/src/main/kotlin/com/zakir/vestra/ui/TestTags.kt`.

**Honesty note, not a formality:** these tests have never been run. No Android device, emulator,
or Appium server exists in the environment that authored them — that environment has no `adb`,
no `ANDROID_HOME`, and no Appium binary (verified directly, not assumed). Writing tests you
cannot execute is not the same as verifying them; treat every test here as a first draft that
needs a real run on a real device before it's trusted. If a locator or a timing assumption is
wrong, expect to find that out on the first real run, not before.

## What's covered

| File | What it actually asserts |
|---|---|
| `test_prompt_isolation.py` | Prompts stay isolated per studio tab (Image/Video/Code/Audio); direct regression test for a real bug found and fixed this session — tapping a News headline used to overwrite whatever prompt was typed in the currently-active studio tab. |
| `test_generation_flows.py` | Local image generation, local code generation (streaming → ready), local chat reach a genuine terminal state — a real result or a legible failure, never a hang or a raw stack trace. |
| `test_image_edit.py` | The image-to-image/edit entry point (attach a reference photo on the Image tab) actually works: attach → thumbnail appears → clear → thumbnail disappears → generation with a reference reaches a terminal state. |
| `test_processing_mode.py` | The On-device-only / Cloud-allowed card in Settings renders both options and switching between them doesn't crash. |

## What's deliberately NOT covered yet

- Video and Audio generation end-to-end (Image and Code were prioritized as the higher-risk
  paths this session touched most).
- Model Packs install/handshake flow (`pack_install_<id>`, `pack_handshake_<id>` tags exist and
  are ready to test — just not written yet).
- Wardrobe version-history navigation (`wardrobe_history_row_<id>` — tags exist, test not written).
- Any assertion on *generation quality* — these check that a result exists and is non-trivial
  (e.g. generated code contains `def`), not that the image looks good or the code is correct.
  That needs either a human eye or a separate, much harder automated-quality-check effort.

## Prerequisites

1. A real Android device or emulator running the app's `minSdk` (35 / Android 15) or higher,
   with the `sideload` debug (or release) build installed — build via
   `./gradlew :composeApp:assembleSideloadDebug` from the repo root.
2. `testTagsAsResourceId` is already enabled at the app's composable root
   (`MainActivity.kt`) — no extra build flag needed.
3. An Appium server reachable from wherever you run pytest:
   ```bash
   npm install -g appium
   appium driver install uiautomator2
   appium  # starts the server on http://127.0.0.1:4723 by default
   ```
4. Python deps:
   ```bash
   cd appium
   python3 -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   ```

## Running

```bash
# Point at an already-installed app (fastest iteration — app state persists between runs):
export APPIUM_NO_RESET=true
pytest -v

# Fresh install from a built APK, resetting app state each session:
export APPIUM_APP_PATH=/path/to/composeApp-sideload-debug.apk
export APPIUM_NO_RESET=false
pytest -v

# Against a specific device/emulator (adb devices to list):
export APPIUM_DEVICE_NAME=emulator-5554
pytest -v
```

Generation tests use long timeouts (`GENERATION_TIMEOUT_SECONDS = 360` in
`test_generation_flows.py`/`test_image_edit.py`) because local Bonsai Image 4B / on-device LLM
cold loads are genuinely several minutes on CPU per their own catalog notes — a hang past that
timeout is a real finding worth investigating, not something to silently extend away.

## When a test fails

Read the failure message first — every assertion in this suite is written to say *what* is
wrong, not just that something didn't match. For a generation timeout, check the app's own
live console (the `result_live_console` tag) and Settings → Diagnostics before assuming it's a
test bug; several bugs found earlier this session (a GPU-delegate failure with no CPU fallback,
a prompt leaking between tabs) were found exactly this way — by tracing what the UI actually
showed, not by trusting that a screen rendering meant the feature worked.

## Locator note

`by_tag()` in `conftest.py` locates elements by `AppiumBy.ID` using the raw `testTag` string
(Compose's `testTagsAsResourceId` semantics property exposes it that way, not prefixed with the
app's package). If your Appium/UiAutomator2 version resolves resource-ids differently on the
device you're running against, that's the one line to adjust — flagged here because it's the
part of this suite most likely to need a tweak on first real run.

# Plan completion scorecard — Claude expansion + v3 follow-up

**As of:** 2026-08-22 · app **v3.0.8** (branch `cursor/stable-r1-plans-367c`, PR #49)  
**Verdict:** Both plans are **~95% complete for in-repo work**. Remaining items need HF upload, GPU weight export, or a Pixel device — not more app scaffolding.

---

## Claude Code expansion (`claude-code-expansion`)

| Cycle | Status | What’s left |
|-------|--------|-------------|
| **cycle1** HF discovery + inference edit | **DONE** | — |
| **cycle2** BiRefNet / Real-ESRGAN / pro-v2 | **DONE** for quality packs @ 3.0.3–3.0.4 | `pro-v2-int8` **HF publish** only |
| **cycle3** Settings / health / wizard | **DONE** @ 3.0.5–3.0.6 | — |
| **cycle4** QNN / LCM / safety / UI tests | **PARTIAL** | LCM clamp + prompt `InputSafetyGate` done; **QNN EP package** + **ONNX NSFW model** + device UI evidence open |

**Claude plan complete?** Code cycles 1–3 yes. Cycle 4 is intentionally stretch (NPU EP + classifier model).

---

## v3 follow-up (`lookbook-v3-followup` A1–E4)

| Band | Status |
|------|--------|
| **A1–A5** stabilize / CI / diagnostics | **DONE** |
| **A6** pro-v2 story | **DONE in-app** (prefer pro-v1 / `runnable=false`); HF upload open |
| **B2–B6** quality packs + Pro UX | **DONE** (runners @ 3.0.4) |
| **B1** lite-v2 | Export flag done; **manifest publish** open |
| **C1–C6** composer + Settings C4 | **DONE** @ 3.0.6 |
| **D3–D4** unit + instrumented smoke | **DONE** |
| **D1** accesslint | Config routes done; **live sweep** needs device |
| **D2** E2E matrix | Script harness done; **recording** needs device |
| **E1–E2** diagnostics persist + pipeline help | **DONE** (+ logcat in export @ 3.0.8) |
| **E3** Gemma | **OPEN** (research / stretch) |
| **E4** SD-Turbo | **Code DONE** (`AndroidLocalImageGenerator` @ 3.0.7); **weights** open |

**Follow-up complete?** Yes for every checkbox that does not require HF credentials, GPU export, or adb. Checklist in [`PLAN.md`](PLAN.md) matches this.

---

## Still blocked (same three gates)

1. **`HF_TOKEN` (write)** → publish `pro-v2-int8` / `lite-v2` / `local-sdturbo-v1`
2. **GPU host** → real ONNX weights
3. **Pixel / emulator adb** → visual baselines + accesslint live report

Canonical release checklist: [`../stable-release/PLAN.md`](../stable-release/PLAN.md).

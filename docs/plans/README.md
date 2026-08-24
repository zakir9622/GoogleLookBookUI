# Lookbook planning docs

Active roadmaps live in separate directories so parallel workstreams do not collide.

| Directory | Source | Baseline | Scope | Status |
|-----------|--------|----------|-------|--------|
| [`lovable-parity-local-first/`](lovable-parity-local-first/) | User's `lookbookweb` (Lovable) repo + live app research | **v3.1.0-rc19** | Glass/spatial design tokens, generation-lifecycle UX (resumable jobs, lineage, correlation-ID errors, storage rollup, single processing-mode setting) — local generation only, no cloud parity | **Planning** — see Open questions before implementation |
| [`five-star-quality/`](five-star-quality/) | Post true-local rc9 audit | **v3.1.0-rc9 → rc10+** | Studio isolation, OrtGraph soft-fail, honesty UI, iterative 5★ cycles | **Active** |
| [`local-first-mode/`](local-first-mode/) | Claude direct (Cursor usage exhausted) | **v3.1.0-rc17** | News refresh fix, Try-on hidden app-wide, global cloud-models toggle, LiteRT-LM on-device slowness | **Iteration 1 shipped** — 2/3 not started |
| [`generation-transparency/`](generation-transparency/) | Claude Code read-only audit (post rc9) | **v3.1.0-rc15** | On-device latency benchmark harness, Pro-pack export collision, OrtSessionCache in diffusion path, per-tab generation log + timer, reduced-motion coverage | **rc15 complete** — all code items; Pixel 9 benchmark run after install |
| [`litert-lm-integration/`](litert-lm-integration/) | Google AI Edge Gallery / LiteRT-LM research | **v3.1.0-rc16** | Gemma 4 Code via LiteRT-LM, vision assist, optional STT/tools; MediaPipe deprecation | **Planned** — see L0–L4 in plan |
| [`true-local/`](true-local/) | Pixel-true offline engines | **v3.1.0-rc5…rc9** | System TTS + SD-Turbo + Gemma + still-clip + handshake | Merged to main |
| [`big-release-r2/`](big-release-r2/) | True-limits release (ATR + UI + on-device scaffold) | **v3.1.0-rc1…rc4** | Full ATR Auto, fixture harness, Loom Ink UI, Audio Studio | Merged to main |
| [`stable-release/`](stable-release/) | Cursor plan audit (post v3.0.3) | v3.0.4 → v3.0.16 | Honest stable cut + sideload | R0/R1 done; handoff to R2 |
| [`lookbook-v3-followup/`](lookbook-v3-followup/) | Cursor Cloud Agent review (post v3 ship) | v2.9.16 → **v3.0.x** | Finish v3 gaps: diagnostics, CI gates, dead-code cleanup, composer depth | Mostly done; E3/E4 open |
| [`claude-code-expansion/`](claude-code-expansion/) | Claude Code improvement plan | v2.9.5 → **v3.0.x** | Models, tools, UI — HF discovery, quality packs, QNN/LCM, model health | cycle1–3 done; cycle4 partial (QNN/LCM honesty) |
| [`generation-stability/`](generation-stability/) | Claude Code read-only audit | v2.9.16 → **v3.0.2+** | Typed cloud failures, live model health, Gradio schemas, local image gen, harness | M1–M3/M6 done; M2 UI @3.0.5; M4 weights deferred; M5 harness dry-run |

## How to use

- **Big release R2** — start here for v3.1.0 (ATR Auto, UI overhaul, true on-device limits).
- **Stable release** — historical R0/R1 through v3.0.16.
- **v3 follow-up** — A1–E4 checklist (historical + remaining E3/E4).
- **Claude Code expansion** — longer-horizon product/engine roadmap (`cycle1`–`cycle4`).
- **Generation stability** — M1–M6 gated milestones for cloud/local generation.
- **Generation transparency** — measure real on-device latency before trusting any speed claim,
  resolve the Pro-pack export collision for good, and give each studio tab a real log + timer.
- **LiteRT-LM integration** — Gallery-class Gemma 4 / vision / optional STT via `.litertlm` packs;
  migrates Code off MediaPipe; does **not** replace ONNX try-on or tiny-SD.
- **Local-first mode** — hide Try-on temporarily, gate all cloud generation behind one master
  toggle (local-only by default), and investigate/fix on-device LiteRT-LM speed.

When items overlap (e.g. BiRefNet packs, settings split), implement once and mark done in both plans.

**Scorecard:** [`COMPLETION.md`](COMPLETION.md) — Claude cycles + v3 follow-up A1–E4 vs current tree.

## Iterative UX cycles (historical)

Short atelier polish loops on `iterative-*` branches (v2.7.1–2.7.5): cancel, a11y, Share/Report, preflight. All merged into `main` — **complete**.

## Archived / external

- Original v3 overhaul plan: Cursor artifacts (`lookbook_v3_overhaul_32292744.plan.md`) — do not edit.

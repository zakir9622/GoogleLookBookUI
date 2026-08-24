# Google Play compliance — Vestra

Working checklist for Play submission. Items marked ☐ are pre-launch actions
for the owner; ☑ are implemented in the codebase.

## AI-Generated Content policy

- ☑ **In-app reporting**: every result screen has a Report action with a reason
  taxonomy (sexual content / violence / likeness misuse / other). Reports queue
  offline and deliver to the `report` Edge Function (`ReportQueue`).
- ☑ **Provenance marking**: every generated image carries a visible
  "✦ AI generated" watermark plus EXIF `UserComment`/`Software` tags
  (`Watermark`, `LiteEngineIo.saveResult`) across all three engines.
- ☑ **Likeness consent**: first use of a personal photo requires an explicit
  acknowledgement (`PersonSourceScreen` consent dialog, persisted in settings).
- ☑ **Input safety (prompt gate)**: [InputSafetyGate] blocks explicit NSFW / prohibited
  prompts on the generative cloud path before network calls. Consent gate + report loop
  remain. ☐ On-device NSFW/minor-detection **classifier model** (ONNX in Lite pack) still
  open before wide Play launch.
- ☐ Complete the Play Console "AI-Generated Content" declaration form.

## Data safety form (answers as implemented)

| Question | Answer |
|---|---|
| Data collected | None by default. Photos are processed on-device and stored only in app-private storage. |
| Data shared | Cloud tier only, explicit opt-in: garment + person images are uploaded for processing and deleted immediately after the run (no retention, no identifiers). Content reports contain reason + app version only. |
| Encryption in transit | Yes (HTTPS everywhere). |
| Deletion mechanism | Uninstalling removes all local data; cloud inputs are transient by design. |

- ☑ Uploads are re-encoded with EXIF stripped before leaving the device.
- ☐ Host `docs/PRIVACY_POLICY.md` at a public URL and link it in the listing.

## Permissions

- ☑ No `READ_MEDIA_*` / storage permissions — system Photo Picker only.
- ☑ `CAMERA` is runtime-optional (`required=false` feature); all flows work
  without it via the picker.
- ☑ `POST_NOTIFICATIONS` used for pack-download progress (runtime prompt on 13+).
- ☑ Foreground service is declared with the `dataSync` type and only runs
  during pack downloads.

## Technical requirements

- ☑ `targetSdk 36`, single-activity, edge-to-edge.
- ☑ Release build minifies + shrinks resources.
- ☐ Switch release packaging to AAB (`bundleRelease`) and enroll in Play App
  Signing; keep the upload keystore out of the repo (gitignored patterns exist).
- ☐ Pre-launch report on a flagship + a 4 GB-RAM device; capture the
  `VestraProBench` timings from logcat for the Pro gate decision.

## Content rating

Expected IARC outcome: Everyone / PEGI 3 with the AI-content questionnaire
answered honestly (user-directed AI image generation, safeguards above).
No ads, no purchases in v1.

## Licensing gates (must clear before store listing)

- ☐ `ml/MODEL_LICENSES.md`: resolve the human-parsing weights question
  (retrain or confirm) and do NOT publish a Pro pack built from
  CC BY-NC weights — see the resolution paths documented there.
- ☑ App code is original; repo is GPL-3.0.

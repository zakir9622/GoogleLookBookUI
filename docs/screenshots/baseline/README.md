# Visual baselines (M5)

Expected PNG filenames produced by `scripts/visual-verify.sh` on a Pixel / emulator:

| File | Deep link route |
|------|-----------------|
| `01-atelier-tryon.png` | `lookbook://screen/studio/tryon` |
| `02-image-studio.png` | `lookbook://screen/studio/image` |
| `03-video-studio.png` | `lookbook://screen/studio/video` |
| `04-code-studio.png` | `lookbook://screen/studio/code` |
| `05-news-chat.png` | `lookbook://screen/studio/news` |
| `06-looks-gallery.png` | `lookbook://screen/wardrobe` |
| `07-help-faq.png` | `lookbook://screen/help` |
| `08-settings.png` | `lookbook://screen/settings` |
| `09-cloud-usage.png` | `lookbook://screen/usage` |
| `10-garment-capture.png` | `lookbook://screen/garment` |
| `11-settings-about.png` | Settings scrolled (about) |

**Status:** placeholders only — commit real Pixel screenshots here when a device run is available, then `scripts/visual-verify.sh <serial> <outdir> --compare`.

List routes without adb:

```bash
scripts/visual-verify.sh --dry-run
```

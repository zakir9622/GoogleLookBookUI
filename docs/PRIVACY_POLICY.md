# The Lookbook Privacy Policy

_Last updated: 2026-08-21_

The Lookbook (package `com.zakir.vestra`) generates modest-fashion try-on looks
and optional cloud studio outputs (image, video, code). It is designed so your
photos stay on your phone unless you explicitly choose a free-tier cloud model.

## What happens on your device

- Photos you pick or capture (garments, yourself) are processed **on your
  device** when you use Lite or Pro model packs. They are stored in app-private
  storage (or Documents/TheLookbook when durable storage is enabled) and are
  not sold or used for advertising.
- Generated looks are saved on your device and marked as AI-generated (visible
  watermark on store builds and image metadata).
- Deleting the app deletes private app storage. You can also remove individual
  looks from the Looks gallery. Durable Documents copies may remain until you
  delete them.

## Optional free-tier cloud studios

If — and only if — you select a **Cloud** try-on engine or run Image / Video /
Code studio with a free Hugging Face Space, Groq, or OpenRouter model:

- Your prompt text and any attached reference or garment/person images are
  uploaded over HTTPS to the selected free provider for that job, then the
  provider returns a result. Providers’ retention policies apply to their
  infrastructure; The Lookbook does not keep a second copy of uploads on a
  Lookbook server.
- Images are re-encoded before upload so location and device metadata (EXIF)
  are stripped when possible.
- API keys you paste (Hugging Face, Groq, OpenRouter) stay on your device and
  are sent only to the matching provider when you generate.

## Content reports

If you report a generated image or clip, The Lookbook stores the reason and
file path **on this device only** (no paid report backend). Use this for Play
policy review workflows and your own records.

## What we never do

- No ads, no trackers, no analytics SDKs.
- No sale or sharing of personal data for advertising.
- No collection of contacts, precise location, or browsing history.

## Children

The Lookbook is not directed at children under 13, and the likeness-consent
gate prohibits generating images of anyone without their permission.

## Contact

Questions or requests: zakir9622@gmail.com

# Hugging Face packs setup (10 minutes)

The Lookbook downloads its model packs — the **Lite engine** (~69 MB, required
for offline generation) and the **Studio models** gallery (your model photos) —
from a Hugging Face dataset repo. This is a one-time setup the owner does.

## 1. Create the repo

- Sign in / sign up at https://huggingface.co
- New → **Dataset** → name it `vestra-packs` (any name works)
- Visibility: **Public** (the app downloads without auth). Keep any dev/research
  packs in a *separate private* repo — never public.
- Copy your write token from https://huggingface.co/settings/tokens

## 2. Build the packs (on a machine with Python)

```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Lite engine pack (segmentation + parsing models)
python export_lite_pack.py

# Studio models pack — point at YOUR model photos (see build_models_pack.py)
python build_models_pack.py /path/to/your/model_photos --out exports/studio-models-v1

# One manifest describing every pack
python manifest_gen.py exports/ --base-url \
  https://huggingface.co/datasets/<your-username>/vestra-packs/resolve/main
```

## 3. Upload

```bash
pip install huggingface_hub
huggingface-cli login          # paste your write token
huggingface-cli upload <your-username>/vestra-packs exports/ . --repo-type dataset
```

## 4. Point the app at it

In `composeApp/src/main/kotlin/com/zakir/vestra/VestraApp.kt`:

```kotlin
const val PACKS_MANIFEST_URL =
    "https://huggingface.co/datasets/<your-username>/vestra-packs/resolve/main/manifest.json"
```

Rebuild the app. On first run it fetches the manifest; the Lite pack downloads
via the onboarding banner, and the casting gallery switches from the bundled
silhouettes to your studio models automatically.

## Updating later

Re-run the relevant `export_*` / `build_models_pack.py`, then `manifest_gen.py`
(it bumps the version of any pack whose files changed), then re-upload. The app
shows "Update available" for changed packs; installed packs keep working offline
in the meantime.

## What stays private

- **Dev / research-weight packs** (`export_dev_pack.py`) → a *private* repo only.
- **Replicate token** and the **Supabase service-role key** → never in any repo;
  they live only in Supabase Edge Function secrets (see `supabase/README.md`).
- The Supabase **anon key** is publishable by design and goes in `VestraApp.kt`.

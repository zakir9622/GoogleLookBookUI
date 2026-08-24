#!/usr/bin/env python3
"""REMOVED — CatVTON stub that used to default to exports/pro-v1/.

That path collides with the production fully-conditioned Pro pack
(SD1.5 + ControlNet-Depth + IP-Adapter) built by convert_pro_pack.py /
colab_convert_pro_pack.ipynb. Writing the 3-file CatVTON export into
pro-v1/ would silently drop text/ControlNet/IP conditioning.

  Production pro-v1:  python convert_pro_pack.py --src … --out exports/pro-v1
  CatVTON experiments: python export_catvton_legacy_pack.py
                       (default out: exports/catvton-legacy/)
"""
from __future__ import annotations

import sys

def main() -> int:
    print(__doc__, file=sys.stderr)
    print(
        "Refusing to run. Use convert_pro_pack.py for pro-v1, "
        "or export_catvton_legacy_pack.py for CatVTON experiments.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

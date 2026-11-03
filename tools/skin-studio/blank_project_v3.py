"""Create a genuinely blank, unstyled v3 project with production validation."""
from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
STARTER = HERE / "blank-starter"


def blank_project():
    from runtime_package_v3 import build, unpack
    manifest = json.loads((STARTER / "manifest.json").read_text(encoding="utf-8"))
    return unpack(build(manifest, STARTER / "source"))

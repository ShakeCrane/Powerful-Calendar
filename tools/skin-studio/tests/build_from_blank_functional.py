"""Build the S-06 Android fixture from its independently authored Blank v3 project."""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from runtime_package_v3 import build

FIXTURE = HERE / "fixtures" / "from-blank-functional"
SOURCE = FIXTURE / "source"
OUTPUT = FIXTURE / "from-blank.pcskin"


def build_fixture() -> bytes:
    manifest = json.loads((FIXTURE / "manifest.json").read_text(encoding="utf-8"))
    return build(manifest, SOURCE)


if __name__ == "__main__":
    OUTPUT.write_bytes(build_fixture())
    print(f"built {OUTPUT.relative_to(HERE.parent)} ({OUTPUT.stat().st_size} bytes)")

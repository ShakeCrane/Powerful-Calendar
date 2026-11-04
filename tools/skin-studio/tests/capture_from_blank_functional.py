"""Capture a Studio GUI export as the isolated S-06 source and Android fixture."""
from __future__ import annotations

import json
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from runtime_package_v3 import unpack

FIXTURE = HERE / "fixtures" / "from-blank-functional"


def capture(package_path: Path) -> tuple[dict, dict[str, bytes]]:
    manifest, files = unpack(package_path.read_bytes())
    if manifest.get("id") != "powercal-from-blank-functional":
        raise ValueError("refusing to capture a package with an unexpected Skin ID")
    output_manifest = dict(manifest)
    output_manifest.pop("sha256", None)
    (FIXTURE / "manifest.json").write_text(
        json.dumps(output_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    for relative, content in files.items():
        target = FIXTURE / "source" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
    return manifest, files


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: python capture_from_blank_functional.py <Studio .pcskin>")
    manifest, files = capture(Path(sys.argv[1]))
    print(json.dumps({"id": manifest["id"], "files": sorted(files),
                      "capabilities": manifest["capabilities"]}, ensure_ascii=False))

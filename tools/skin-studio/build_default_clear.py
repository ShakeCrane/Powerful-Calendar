"""Build or verify the single canonical bundled Clear skin artifact.

The APK asset is not a second authored template. Studio reads these exact bytes.
The visual input clear-design.css and font were copied from the draft design
into tracked runtime source, so the build never depends on untracked design/.
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
from runtime_package_v3 import build, unpack

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
SOURCE=HERE/"default-clear"
ASSET=ROOT/"app"/"src"/"main"/"assets"/"powercal-clear-default.pcskin"

def expected_bytes():
    import json
    manifest=json.loads((SOURCE/"manifest.json").read_text(encoding="utf-8"))
    return build(manifest,SOURCE/"source")

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--verify",action="store_true",
                    help="fail if checked-in APK bundle differs from canonical source")
    args=ap.parse_args()
    expected=expected_bytes()
    if args.verify:
        if not ASSET.is_file():
            raise SystemExit("Bundled Clear skin missing")
        actual=ASSET.read_bytes()
        if actual!=expected:
            raise SystemExit("Bundled Clear skin is stale: rebuild and commit it")
    else:
        ASSET.parent.mkdir(parents=True,exist_ok=True)
        ASSET.write_bytes(expected)
    manifest, files=unpack(expected)
    digest=hashlib.sha256(expected).hexdigest()
    print(f"Clear skin: {manifest['id']}, {len(files)} files, "
          f"{len(expected)} bytes, sha256={digest}")

if __name__=="__main__":
    main()

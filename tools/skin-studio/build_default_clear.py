"""Build or verify the single canonical bundled Clear skin artifact.

The APK asset is not a second authored template. Studio reads these exact bytes.
The visual input clear-design.css and font were copied from the draft design
into tracked runtime source, so the build never depends on untracked design/.
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
from zipfile import BadZipFile, ZipFile
from runtime_package_v3 import build, unpack

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
SOURCE=HERE/"default-clear"
ASSET=ROOT/"app"/"src"/"main"/"assets"/"powercal-clear-default.pcskin"

def expected_bytes():
    import json
    manifest=json.loads((SOURCE/"manifest.json").read_text(encoding="utf-8"))
    return build(manifest,SOURCE/"source")

def verify_apk_asset(apk_path, expected):
    asset_name="assets/powercal-clear-default.pcskin"
    try:
        with ZipFile(apk_path) as apk:
            matches=[entry for entry in apk.infolist() if entry.filename==asset_name]
            if len(matches)!=1:
                raise ValueError(f"APK must contain exactly one {asset_name}")
            if matches[0].file_size!=len(expected):
                raise ValueError("compiled APK Clear asset size differs from canonical source bundle")
            actual=apk.read(matches[0])
    except BadZipFile as error:
        raise ValueError("--apk is not a readable APK/ZIP archive") from error
    if actual!=expected:
        raise ValueError("compiled APK Clear asset differs from canonical source bundle")
    return hashlib.sha256(actual).hexdigest()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--verify",action="store_true",
                    help="fail if checked-in APK bundle differs from canonical source")
    ap.add_argument("--apk",type=Path,
                    help="also verify the packaged Clear asset inside a built Debug APK (requires --verify)")
    args=ap.parse_args()
    if args.apk and not args.verify:
        ap.error("--apk requires --verify")
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
    apk_digest=verify_apk_asset(args.apk,expected) if args.apk else None
    manifest, files=unpack(expected)
    digest=hashlib.sha256(expected).hexdigest()
    result=(f"Clear skin: {manifest['id']}, {len(files)} files, "
            f"{len(expected)} bytes, sha256={digest}")
    if apk_digest:
        result+=f", apk_asset_sha256={apk_digest}"
    print(result)

if __name__=="__main__":
    main()

"""Self-contained programmable PowerCal .pcskin v3 builder/validator."""
from __future__ import annotations

import hashlib
import io
import json
import re
from pathlib import Path, PurePosixPath
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

import skin_studio

MAX_ARCHIVE = 10_000_000
MAX_TOTAL = 16_000_000
MAX_FILES = 96
TEXT_LIMIT = 600_000
ASSET_LIMIT = 3_000_000
WEB_REQUIRED = ("web/index.html", "web/style.css", "web/app.js", "project.json")
PATH = re.compile(r"(?:web|assets)/[a-zA-Z0-9][a-zA-Z0-9_./-]{0,119}|project\.json\Z")


def check(ok, message):
    if not ok:
        raise skin_studio.SkinError(message)


def safe_path(name):
    p = PurePosixPath(name)
    return bool(PATH.fullmatch(name) and not p.is_absolute() and ".." not in p.parts and "\\" not in name)


def validate_project(project):
    check(type(project) is dict, "project.json must be an object")
    check(project.get("projectVersion") == 3, "projectVersion must be 3")
    check(project.get("mode") in ("simple", "advanced"), "mode must be simple or advanced")
    slots = project.get("slots", [])
    check(type(slots) is list and len(slots) <= 128, "slots must be a bounded array")
    ids = set()
    for slot in slots:
        check(type(slot) is dict and set(slot) <= {"id", "label", "type", "value", "editable"},
              "invalid slot metadata")
        ident = slot.get("id")
        check(type(ident) is str and re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}", ident)
              and ident not in ids, "invalid or repeated slot id")
        ids.add(ident)
        check(slot.get("type") in ("color", "image", "font", "text", "number"),
              "unsupported slot type")
    return project


def validate_manifest(manifest, files, verify_hash=True):
    check(type(manifest) is dict, "manifest must be an object")
    allowed = skin_studio.FIELDS | {"entrypoint", "project", "capabilities", "fullCanvas"}
    check(set(manifest) <= allowed, "unknown v3 manifest key")
    check(manifest.get("format") == "powercal-skin" and manifest.get("schemaVersion") == 3,
          "not a .pcskin schemaVersion 3 package")
    check(type(manifest.get("id")) is str and skin_studio.ID.fullmatch(manifest["id"])
          and manifest["id"] != "powercal-default", "invalid skin id")
    check(type(manifest.get("name")) is str and 1 <= len(manifest["name"].strip()) <= 60,
          "invalid skin name")
    check(manifest.get("entrypoint") == "web/index.html" and manifest.get("project") == "project.json",
          "v3 requires local web/index.html and project.json")
    check(type(manifest.get("fullCanvas", False)) is bool, "fullCanvas must be boolean")
    # Android's parser treats an absent declaration as no capabilities.
    # Importing or exporting a skin must not silently grant Host access.
    caps = manifest.get("capabilities", [])
    check(type(caps) is list and set(caps) <= {"host.read", "host.action", "host.edit"},
          "unsupported bridge capability")
    check(set(WEB_REQUIRED) <= set(files), "v3 package is missing complete web source/project")
    check(all(safe_path(name) for name in files), "unsafe v3 package path")
    check(len(files) <= MAX_FILES, "too many package files")
    validate_project(json.loads(files["project.json"].decode("utf8")))
    html = files["web/index.html"].decode("utf8")
    check("<iframe" not in html.lower() and "<object" not in html.lower(),
          "iframes and embedded objects are forbidden")
    check("<script" not in html.lower() or "src=\"app.js\"" in html or "src='app.js'" in html,
          "index.html may only load packaged app.js")
    check("http://" not in html.lower() and "https://" not in html.lower(),
          "remote resources are forbidden")
    actual = {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}
    hashes = manifest.get("sha256", {})
    check(type(hashes) is dict and (not verify_hash or set(hashes) == set(files)),
          "SHA-256 must cover every package file")
    if verify_hash:
        check(all(hashes.get(name, "").lower() == digest for name, digest in actual.items()),
              "SHA-256 mismatch")
    result = dict(manifest)
    result["name"] = result["name"].strip()
    result["capabilities"] = caps
    result["sha256"] = actual
    return result


def build(manifest, source_dir):
    root = Path(source_dir)
    check(root.is_dir(), "--source-dir must be a directory")
    files = {}
    for path in root.rglob("*"):
        if path.is_file():
            name = path.relative_to(root).as_posix()
            check(safe_path(name), "unsafe source path: " + name)
            data = path.read_bytes()
            limit = TEXT_LIMIT if name.endswith((".html", ".css", ".js", ".json")) else ASSET_LIMIT
            check(len(data) <= limit, "source file too large: " + name)
            files[name] = data
    return build_files(manifest, files)


def build_files(manifest, files):
    checked = validate_manifest(manifest, files, verify_hash=False)
    out = io.BytesIO()
    # Deterministic .pcskin bytes: APK asset, Studio starter and exported default
    # must never disagree solely because a ZIP header contains wall-clock time.
    def fixed_entry(name):
        entry = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        entry.compress_type = ZIP_DEFLATED
        entry.external_attr = 0o100644 << 16
        return entry
    with ZipFile(out, "w", ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr(fixed_entry("manifest.json"),
                         json.dumps(checked, ensure_ascii=False, indent=2))
        for name in sorted(files):
            archive.writestr(fixed_entry(name), files[name])
    blob = out.getvalue()
    check(len(blob) <= MAX_ARCHIVE, "ZIP is larger than 10MB")
    # Apply the same per-file and inflated-size checks as Android and the importer.
    # A tiny compressed ZIP may otherwise conceal an invalid oversized source.
    unpack(blob)
    return blob


def unpack(blob):
    check(len(blob) <= MAX_ARCHIVE, "ZIP exceeds 10MB")
    with ZipFile(io.BytesIO(blob)) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        check(len(entries) <= MAX_FILES + 1 and len(names) == len(set(names)), "repeated/too many entries")
        check("manifest.json" in names, "manifest.json missing")
        files, total = {}, 0
        for entry in entries:
            name = entry.filename
            check(not entry.is_dir() and (name == "manifest.json" or safe_path(name)), "unsafe ZIP entry")
            check(entry.compress_type in (ZIP_DEFLATED, ZIP_STORED), "unsupported compression")
            check((entry.external_attr >> 16) & 0o170000 != 0o120000, "symlinks are forbidden")
            limit = 80_000 if name == "manifest.json" else (
                TEXT_LIMIT if name.endswith((".html", ".css", ".js", ".json")) else ASSET_LIMIT)
            check(entry.file_size <= limit, "entry too large: " + name)
            data = archive.read(entry)
            total += len(data)
            check(total <= MAX_TOTAL, "inflated package too large")
            if name == "manifest.json":
                manifest = json.loads(data.decode("utf8"))
            else:
                files[name] = data
    return validate_manifest(manifest, files), files

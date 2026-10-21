#!/usr/bin/env python3
"""Build/validate non-executable .pcskin v2 runtime UI ZIPs (Python stdlib only)."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

import skin_studio

HERE = Path(__file__).resolve().parent
SCREEN_NAMES = {"timeline", "month", "memo", "ai", "settings", "deck",
                "navigation", "overlay", "chrome", "picker"}
STRUCT = {"column", "row", "box", "card", "scroll"}
LEAVES = {"text", "button", "spacer", "native", "events", "todos", "chats",
          "chat-input", "deck-actions", "skins", "nav-items",
          "overlay-title", "overlay-body", "overlay-confirm", "overlay-dismiss",
          "window-content", "profile", "credentials",
          "picker-title", "picker-date", "picker-time", "picker-confirm", "picker-dismiss"}
TYPES = STRUCT | LEAVES
MOTIONS = {"none", "fade", "slide-up", "slide-left", "scale"}
COLORS = {"", "primary", "primaryContainer", "surface", "surfaceContainer",
          "surfaceContainerHigh", "surfaceContainerLow", "background"}
ACTIONS = {
    "timeline": {"today", "memo", "deck", "month", "settings",
                 "new-alarm", "new-activity", "new-deadline", "native"},
    "month": {"back", "new", "native"},
    "memo": {"back", "new", "native"},
    "ai": {"settings", "deck", "pick-image", "pick-file", "clear-cache", "native"},
    "settings": {"back", "deck", "import-skin", "export-skin",
                 "import-ics", "export-ics", "export-backup", "restore-backup",
                 "notification-permission", "exact-alarm", "native"},
    "deck": {"back", "commit", "discard", "undo", "save", "native"},
    "navigation": {"timeline", "month", "ai", "settings", "native"},
    "overlay": {"native"},
    "chrome": {"native"},
    "picker": {"native"},
}
NODE_KEYS = {"type", "text", "action", "children", "padding", "gap",
             "grow", "color", "motion", "durationMs"}
MAX_NODES = 384


def check(test, error):
    if not test:
        raise skin_studio.SkinError(error)


def validate_ui(ui):
    check(type(ui) is dict and {"format", "version", "screens"} <= set(ui)
          and set(ui) <= {"format", "version", "screens", "transition", "durationMs"},
          "UI metadata contains unsupported fields")
    check(ui["format"] == "powercal-runtime-ui" and type(ui["version"]) is int
          and ui["version"] == 1, "Incompatible UI format")
    check(ui.get("transition", "fade") in MOTIONS and
          type(ui.get("durationMs", 220)) is int and
          0 <= ui.get("durationMs", 220) <= 600, "Invalid page transition")
    screens = ui["screens"]
    check(type(screens) is dict and bool(screens) and set(screens) <= SCREEN_NAMES,
          "Unrecognized screen")
    count = 0

    def node(n, screen, depth=1, scroll_depth=0):
        nonlocal count
        count += 1
        check(type(n) is dict and set(n) <= NODE_KEYS and
              n.get("type") in TYPES and depth <= 10 and count <= MAX_NODES,
              "Invalid, overdeep or oversized component tree")
        typ = n["type"]
        check(type(n.get("text", "")) is str and len(n.get("text", "")) <= 160,
              "Invalid text")
        action = n.get("action", "")
        check(type(action) is str and (not action or
              (typ == "button" and action in ACTIONS[screen])),
              "Disallowed host intent")
        if typ == "button":
            check(bool(n.get("text")) and bool(action), "Buttons require label and intent")
        for field, bound in (("padding", 40), ("gap", 32), ("durationMs", 600)):
            value = n.get(field, 220 if field == "durationMs" else 8 if field == "gap" else 0)
            check(type(value) is int and 0 <= value <= bound, "Invalid node measurement")
        check(type(n.get("grow", False)) is bool and
              n.get("color", "") in COLORS and n.get("motion", "none") in MOTIONS,
              "Invalid component presentation")
        if typ in {"overlay-title", "overlay-body", "overlay-confirm",
                   "overlay-dismiss", "window-content"}:
            check(screen == "overlay", "Dialog slots cannot be used outside dialogs")
        if typ in {"profile", "credentials", "skins"}:
            check(screen == "settings", "Settings widgets out of scope")
        if typ.startswith("picker-"):
            check(screen == "picker", "Picker widget out of scope")
        if typ == "nav-items":
            check(screen == "navigation", "Navigation widget out of scope")
        if typ == "events":
            check(screen in {"timeline", "month"}, "Events widget out of scope")
        if typ == "todos":
            check(screen == "memo", "Memo widget out of scope")
        if typ in {"chats", "chat-input"}:
            check(screen == "ai", "AI widget out of scope")
        if typ == "deck-actions":
            check(screen == "deck", "Draft widget out of scope")
        children = n.get("children", [])
        check(type(children) is list and len(children) <= 30 and
              (typ in STRUCT or not children), "Invalid node children")
        if screen == "deck":
            check(typ != "native", "Use a native-action button in draft dialog")
        if typ == "scroll":
            check(scroll_depth == 0, "Nested vertical scrolling prohibited")
        return {typ} | set().union(*(node(c, screen, depth + 1,
                         scroll_depth + (typ == "scroll")) for c in children))

    for screen, tree in screens.items():
        seen = node(tree, screen)
        if screen == "chrome":
            check("native" in seen, "Chrome must preserve its host body")
        if screen == "navigation":
            check("nav-items" in seen or "native" in seen,
                  "Navigation must preserve navigation actions")
        if screen == "picker":
            check("native" in seen or
                  {"picker-title", "picker-date", "picker-time",
                   "picker-confirm", "picker-dismiss"} <= seen,
                  "Picker must have title, dates, times, confirm and dismiss")
        if screen == "overlay":
            check("native" in seen or
                  {"overlay-body", "overlay-confirm", "overlay-dismiss",
                   "window-content"} <= seen,
                  "Dialogs must include confirmation, dismiss, body and window slots")
    encoded = json.dumps(ui, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    check(len(encoded) <= 120_000, "UI description larger than 120KB")
    return encoded


def validate_manifest(manifest, ui, images):
    check(type(manifest) is dict and set(manifest) <= skin_studio.FIELDS | {"runtimeUi"} and
          manifest.get("schemaVersion") == 2 and manifest.get("runtimeUi") == "ui.json",
          "Invalid v2 manifest")
    base = {k: v for k, v in manifest.items() if k not in ("runtimeUi", "sha256")}
    base["schemaVersion"] = 1
    checked = skin_studio.validated(base, images, verify_hash=False)
    checked["schemaVersion"] = 2
    checked["runtimeUi"] = "ui.json"
    ui_blob = validate_ui(ui)
    checked["sha256"] = checked["sha256"] | {"ui.json": hashlib.sha256(ui_blob).hexdigest()}
    return checked, ui_blob


def build(manifest, ui, images=None):
    images = images or {}
    checked, ui_blob = validate_manifest(manifest, ui, images)
    out = io.BytesIO()
    with ZipFile(out, "w", ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr("manifest.json", json.dumps(checked, ensure_ascii=False, indent=2))
        archive.writestr("ui.json", ui_blob)
        for name in sorted(images):
            archive.writestr(name, images[name])
    data = out.getvalue()
    check(len(data) <= 10_000_000, "ZIP is larger than 10MB")
    return data


def unpack(blob):
    check(len(blob) <= 10_000_000, "ZIP exceeds maximum size")
    with ZipFile(io.BytesIO(blob)) as archive:
        entries = archive.infolist()
        names = [item.filename for item in entries]
        check(len(entries) <= 24 and len(names) == len(set(names)),
              "Too many or repeated entries")
        check("ui.json" in names and "manifest.json" in names, "Required files missing")
        files, total = {}, 0
        for entry in entries:
            path = entry.filename
            check(not entry.is_dir() and (path in ("ui.json", "manifest.json") or
                  skin_studio.IMG.fullmatch(path)), "Unexpected ZIP entry")
            check(entry.compress_type in (ZIP_DEFLATED, ZIP_STORED) and
                  (entry.external_attr >> 16) & 0o170000 != 0o120000,
                  "Unsafe compression or symlink")
            limit = 120_000 if path == "ui.json" else 80_000 if path == "manifest.json" else 3_000_000
            check(entry.file_size <= limit, "ZIP entry too large")
            with archive.open(entry) as stream:
                files[path] = stream.read(limit + 1)
            total += len(files[path])
            check(len(files[path]) <= limit and total <= 12_000_000, "Decompression bounds exceeded")
        manifest = json.loads(files.pop("manifest.json").decode("utf8"))
        ui_blob = files.pop("ui.json")
        ui = json.loads(ui_blob.decode("utf8"))
        images = files
        checked, canonical_ui = validate_manifest(manifest, ui, images)
        check(set(manifest.get("sha256", {})) == set(checked["sha256"]),
              "SHA-256 entry coverage mismatch")
        for path, expected in checked["sha256"].items():
            original = ui_blob if path == "ui.json" else images[path]
            check(manifest["sha256"][path].lower() ==
                  hashlib.sha256(original).hexdigest(), "SHA-256 mismatch: " + path)
        return manifest, ui


def read_assets(manifest, root):
    paths = set(manifest.get("imagesLight", {}).values()) | set(
        manifest.get("imagesDark", {}).values())
    if not paths:
        return {}
    check(root is not None and root.is_dir(), "Image assets require --assets-dir")
    result = {}
    for path in paths:
        check(bool(skin_studio.IMG.fullmatch(path)), "Illegal resource name")
        file = root / path
        check(file.is_file() and file.stat().st_size <= 3_000_000,
              "Missing or oversized image resource: " + path)
        result[path] = file.read_bytes()
    return result


def main():
    cli = argparse.ArgumentParser(description="PowerCal declarative runtime .pcskin v2")
    sub = cli.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build")
    b.add_argument("--manifest", type=Path, required=True)
    b.add_argument("--ui", type=Path, required=True)
    b.add_argument("--out", type=Path, required=True)
    b.add_argument("--assets-dir", type=Path, help="root directory containing images/... resources")
    v = sub.add_parser("validate")
    v.add_argument("file", type=Path)
    args = cli.parse_args()
    if args.command == "build":
        manifest = json.loads(args.manifest.read_text("utf8"))
        pack = build(manifest, json.loads(args.ui.read_text("utf8")),
                     read_assets(manifest, args.assets_dir))
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(pack)
        print("Built .pcskin v2:", args.out, "bytes=", len(pack))
    else:
        manifest, ui = unpack(args.file.read_bytes())
        print("Validated .pcskin v2:", manifest["id"], "screens:", ", ".join(ui["screens"]))


if __name__ == "__main__":
    main()

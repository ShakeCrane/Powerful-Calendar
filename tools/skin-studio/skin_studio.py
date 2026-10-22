#!/usr/bin/env python3
"""Local-only PowerCal Skin Studio. Python stdlib, no pip dependencies."""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import io
import json
import math
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import struct
import zlib
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

HERE = Path(__file__).resolve().parent
COLOR_KEYS = (
    "primary onPrimary primaryContainer onPrimaryContainer secondary onSecondary "
    "tertiary onTertiary background onBackground surface onSurface surfaceContainerLow "
    "surfaceContainer surfaceContainerHigh outline alarm activity deadline nowLine gridLine "
    "glassFill glassBorder glassHighlight"
).split()
IMAGE_SLOTS = (
    "TIMELINE_BACKGROUND DECK_BACKDROP MONTH_BACKGROUND COMPLETION_EMBLEM BRAND_MARK "
    "ALARM_MARKER ACTIVITY_BADGE DEADLINE_MARKER AI_AVATAR SETTINGS_ICON"
).split()
LIMITS = {"cardRadiusDp": (12, 40, 28), "barRadiusDp": (8, 32, 20),
          "pointLineDp": (1, 5, 2), "fontScale": (.85, 1.2, 1)}
FIELDS = {"format", "schemaVersion", "id", "name", "light", "dark", "layout",
          "imagesLight", "imagesDark", "sha256"}
ID = re.compile(r"[a-z0-9][a-z0-9_-]{2,47}\Z")
IMG = re.compile(r"images/[a-z0-9_-]+\.(?:png|webp)\Z")
HEX = re.compile(r"#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\Z")
SHA = re.compile(r"[0-9a-fA-F]{64}\Z")


class SkinError(ValueError):
    pass


def check(ok, error):
    if not ok:
        raise SkinError(error)


def dimensions(blob, path):
    if path.endswith(".png"):
        check(len(blob) >= 33 and blob[:8] == b"\x89PNG\r\n\x1a\n"
              and blob[12:16] == b"IHDR", path + ": 非法 PNG")
        check(zlib.crc32(blob[12:29]) == int.from_bytes(blob[29:33], "big"),
              path + ": PNG header CRC 不正确")
        width, height = struct.unpack(">II", blob[16:24])
    else:
        check(len(blob) >= 30 and blob[:4] == b"RIFF" and blob[8:12] == b"WEBP"
              and int.from_bytes(blob[4:8], "little") + 8 <= len(blob),
              path + ": 非法 WebP")
        kind = blob[12:16]
        if kind == b"VP8X":
            width = 1 + int.from_bytes(blob[24:27], "little")
            height = 1 + int.from_bytes(blob[27:30], "little")
        elif kind == b"VP8L":
            check(len(blob) >= 25 and blob[20] == 0x2f, path + ": VP8L 格式错误")
            bits = int.from_bytes(blob[21:25], "little")
            width, height = (bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1
        elif kind == b"VP8 ":
            check(blob[23:26] == b"\x9d\x01\x2a", path + ": VP8 帧头错误")
            width = int.from_bytes(blob[26:28], "little") & 0x3fff
            height = int.from_bytes(blob[28:30], "little") & 0x3fff
        else:
            raise SkinError(path + ": 不支持的 WebP 类型")
    check(1 <= width <= 4096 and 1 <= height <= 4096
          and width * height <= 8_000_000, path + ": 图片像素超出 Android 导入上限")
    return width, height


def validated(manifest, images, verify_hash=True):
    check(type(manifest) is dict and set(manifest) <= FIELDS, "manifest 存在未知键")
    m = dict(manifest)
    check(m.get("format") == "powercal-skin" and type(m.get("schemaVersion")) is int
          and m["schemaVersion"] == 1, "仅支持 .pcskin schemaVersion 1")
    ident, name = m.get("id"), m.get("name")
    check(type(ident) is str and ID.fullmatch(ident) and ident != "powercal-default",
          "皮肤 ID 无效或为内置保留值")
    check(type(name) is str and 1 <= len(name.strip()) <= 60, "皮肤名称长度应为 1–60")
    m["name"] = name.strip()
    for mode in ("light", "dark"):
        p = m.get(mode, {})
        check(type(p) is dict and set(p) <= set(COLOR_KEYS), mode + ": 未知配色字段")
        for key, val in p.items():
            check(type(val) is str and HEX.fullmatch(val), f"{mode}.{key}: 无效颜色")
        for a, b in (("primary", "onPrimary"), ("background", "onBackground"),
                     ("surface", "onSurface")):
            check((a in p) == (b in p), f"{mode}: {a}/{b} 必须成对")
        m[mode] = p
    layout = m.get("layout", {})
    check(type(layout) is dict and set(layout) <= set(LIMITS), "不支持的布局字段")
    for name, value in layout.items():
        lo, hi, _ = LIMITS[name]
        check(type(value) in (int, float) and math.isfinite(value) and lo <= value <= hi,
              name + ": 超出允许范围")
    m["layout"] = layout
    paths = set()
    for mode in ("imagesLight", "imagesDark"):
        refs = m.get(mode, {})
        check(type(refs) is dict and len(refs) <= 12 and set(refs) <= set(IMAGE_SLOTS),
              mode + ": 素材槽位非法")
        for slot, path in refs.items():
            check(type(path) is str and IMG.fullmatch(path), slot + ": 非法素材路径")
            paths.add(path)
        m[mode] = refs
    check(set(images) == paths, "素材列表与声明不一致（存在遗漏或未声明的图片）")
    check(len(images) + 1 <= 24, "ZIP 条目数量超限")
    digests, total = {}, 0
    for path, data in images.items():
        check(IMG.fullmatch(path) and type(data) is bytes and len(data) <= 3_000_000,
              path + ": 格式或 3MB 大小限制不符")
        total += len(data)
        dimensions(data, path)
        digests[path] = hashlib.sha256(data).hexdigest()
    actual = m.get("sha256", {})
    check(type(actual) is dict, "sha256 必须是对象")
    if verify_hash:
        check(set(actual) == paths, "SHA-256 清单遗漏或包含多余项目")
        for path, digest in actual.items():
            check(type(digest) is str and SHA.fullmatch(digest)
                  and digest.lower() == digests[path], path + ": SHA-256 不匹配")
    m["sha256"] = digests
    raw = json.dumps(m, ensure_ascii=False, indent=2).encode("utf-8")
    check(len(raw) <= 80_000 and len(raw) + total <= 12_000_000,
          "manifest 或解压体积超出上限")
    return m


def pack(manifest, images):
    m = validated(manifest, images, verify_hash=False)
    out = io.BytesIO()
    with ZipFile(out, "w", ZIP_DEFLATED, compresslevel=6) as zipfile:
        zipfile.writestr("manifest.json", json.dumps(m, ensure_ascii=False, indent=2))
        for name in sorted(images):
            zipfile.writestr(name, images[name])
    data = out.getvalue()
    check(len(data) <= 10_000_000, "压缩包超过 10MB")
    return data


def unpack(data):
    check(len(data) <= 10_000_000, "压缩包超过 10MB")
    try:
        with ZipFile(io.BytesIO(data)) as z:
            info = z.infolist()
            names = [x.filename for x in info]
            check(len(info) <= 24 and len(names) == len(set(names)), "ZIP 条目重复或数量超限")
            check("manifest.json" in names, "缺少 manifest.json")
            files, total = {}, 0
            for entry in info:
                name = entry.filename
                check(not entry.is_dir() and
                      (name == "manifest.json" or IMG.fullmatch(name)),
                      "ZIP 非法条目：" + name[:80])
                check(entry.compress_type in (ZIP_DEFLATED, ZIP_STORED), "未知压缩算法")
                check((entry.external_attr >> 16) & 0o170000 != 0o120000,
                      "不允许符号链接")
                limit = 80_000 if name == "manifest.json" else 3_000_000
                check(entry.file_size <= limit, name + ": 单文件超限")
                total += entry.file_size
                check(total <= 12_000_000, "解压总量超限")
                with z.open(entry) as f:
                    files[name] = f.read(limit + 1)
                check(len(files[name]) <= limit, "解压结果超限")
            m = json.loads(files.pop("manifest.json").decode("utf-8"))
            return validated(m, files), files
    except (OSError, UnicodeError, TypeError, KeyError, json.JSONDecodeError,
            RuntimeError) as exc:
        raise SkinError("无法解码皮肤包：" + str(exc)) from exc


def from_json(data):
    check(type(data) is dict and set(data) <= {"manifest", "images"}, "请求体字段错误")
    images = data.get("images", {})
    check(type(images) is dict and len(images) <= 23, "图片映射非法")
    result = {}
    for path, value in images.items():
        check(type(path) is str and IMG.fullmatch(path) and type(value) is str
              and len(value) <= 4_100_000, "图片字段非法")
        try:
            result[path] = base64.b64decode(value, validate=True)
        except binascii.Error as exc:
            raise SkinError(path + ": Base64 非法") from exc
    return data.get("manifest"), result


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, payload, mime="application/json; charset=utf-8", attachment=None, csp=None):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", csp or
                         "default-src 'self'; style-src 'self' 'unsafe-inline'; "
                         "script-src 'self'; img-src 'self' data: blob:; "
                         "connect-src 'self'; object-src 'none'; base-uri 'none'")
        if attachment:
            self.send_header("Content-Disposition", 'attachment; filename="' + attachment + '"')
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def send_json(self, status, data):
        self.reply(status, json.dumps(data, ensure_ascii=False).encode(), "application/json; charset=utf-8")

    def do_GET(self):
        route = self.path.split("?", 1)[0]
        files = {"/": ("index.html", "text/html; charset=utf-8"),
                 "/v3-studio.js": ("v3-studio.js", "text/javascript; charset=utf-8"),
                 "/app.js": ("app.js", "text/javascript; charset=utf-8"),
                 "/style.css": ("style.css", "text/css; charset=utf-8"),
                 "/style-v3.css": ("style-v3.css", "text/css; charset=utf-8")}
        if route == "/api/template-v3":
            from runtime_package_v3 import build, unpack as unpack_v3
            example = HERE / "examples" / "v3"
            manifest = json.loads((example / "manifest.json").read_text("utf8"))
            source = build(manifest, example / "source")
            base, files = unpack_v3(source)
            return self.send_json(200, {"manifest": base, "files": {
                path: base64.b64encode(data).decode("ascii") for path, data in files.items()}})
        if route.startswith("/preview/"):
            from studio_v3_preview import resolve
            response = resolve(route)
            if response is None:
                return self.send_json(404, {"error": "Preview expired or resource missing"})
            mime, raw = response
            return self.reply(200, raw, mime, csp=
                "default-src 'self'; img-src 'self' data:; "
                "style-src 'self' 'unsafe-inline'; script-src 'self'; "
                "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'")
        if route not in files:
            return self.send_json(404, {"error": "Not Found"})
        filename, mime = files[route]
        self.reply(200, (HERE / filename).read_bytes(), mime)

    def do_POST(self):
        if self.path not in ("/api/validate", "/api/export", "/api/import",
                             "/api/export-v3", "/api/import-v3", "/api/preview-v3"):
            return self.send_json(404, {"error": "Not Found"})
        origin = self.headers.get("Origin")
        if origin and origin not in (f"http://127.0.0.1:{self.server.server_port}",
                                     f"http://localhost:{self.server.server_port}"):
            return self.send_json(403, {"error": "跨域请求不允许"})
        try:
            size = int(self.headers.get("Content-Length", "-1"))
            check(0 <= size <= 24_000_000, "请求体过大")
            is_import = self.path in ("/api/import", "/api/import-v3")
            mime = self.headers.get("Content-Type", "").split(";", 1)[0].strip()
            check(mime == ("application/octet-stream" if is_import else "application/json"),
                  "Content-Type 不受支持")
            data = self.rfile.read(size)
            check(len(data) == size, "传输中断")
            if is_import:
                if self.path == "/api/import-v3":
                    from runtime_package_v3 import unpack as unpack_v3
                    manifest, files = unpack_v3(data)
                    return self.send_json(200, {"manifest": manifest, "files": {
                        path: base64.b64encode(blob).decode("ascii") for path, blob in files.items()}})
                manifest, assets = unpack(data)
                self.send_json(200, {"manifest": manifest, "images": {
                    path: base64.b64encode(blob).decode("ascii") for path, blob in assets.items()}})
                return
            obj = json.loads(data.decode("utf-8"))
            if self.path in ("/api/export-v3", "/api/preview-v3"):
                from runtime_package_v3 import build_files
                raw_files = obj.get("files", {})
                check(type(raw_files) is dict and len(raw_files) <= 96, "invalid v3 files")
                files = {path: base64.b64decode(value, validate=True)
                         for path, value in raw_files.items()}
                if self.path == "/api/preview-v3":
                    from studio_v3_preview import publish
                    return self.send_json(200, {"url": publish(obj.get("manifest"), files)})
                compiled = build_files(obj.get("manifest"), files)
                return self.reply(200, compiled, "application/octet-stream",
                                  "PowerCal-" + obj["manifest"]["id"] + ".pcskin")
            manifest, assets = from_json(obj)
            if self.path == "/api/validate":
                m = validated(manifest, assets, verify_hash=False)
                return self.send_json(200, {"ok": True, "manifest": m,
                                            "assets": len(assets), "note": "仍须 Android 真机验收"})
            compiled = pack(manifest, assets)
            self.reply(200, compiled, "application/octet-stream",
                       "PowerCal-" + manifest["id"] + ".pcskin")
        except (SkinError, ValueError, UnicodeError, TypeError) as exc:
            self.send_json(400, {"error": str(exc)})


def main():
    parser = argparse.ArgumentParser(description="PowerCal Skin Studio")
    sub = parser.add_subparsers(dest="command")
    serve = sub.add_parser("serve", help="start local visual editor")
    serve.add_argument("--port", type=int, default=8765)
    chk = sub.add_parser("validate", help="validate an existing .pcskin")
    chk.add_argument("file", type=Path)
    build2 = sub.add_parser("build-v2", help="compile declarative runtime UI into an importable ZIP")
    build2.add_argument("--manifest", type=Path, required=True)
    build2.add_argument("--ui", type=Path, required=True)
    build2.add_argument("--out", type=Path, required=True)
    build2.add_argument("--assets-dir", type=Path)
    build3 = sub.add_parser("build-v3", help="build a self-contained programmable Web skin")
    build3.add_argument("--manifest", type=Path, required=True)
    build3.add_argument("--source-dir", type=Path, required=True)
    build3.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "validate":
        payload = args.file.read_bytes()
        # Do not decompress attacker-provided manifest without a size cap.
        with ZipFile(io.BytesIO(payload)) as archive:
            head = archive.getinfo("manifest.json")
            check(head.file_size <= 80_000, "Manifest too large")
            with archive.open(head) as source:
                manifest = json.loads(source.read(80_001).decode("utf8"))
        if manifest.get("schemaVersion") == 3:
            from runtime_package_v3 import unpack as unpack_v3
            m, files = unpack_v3(payload)
            print(f"OK: {m['id']} / {m['name']} / runtime v3 / {len(files)} source files")
        elif manifest.get("schemaVersion") == 2:
            from runtime_package import unpack as unpack_v2
            m, ui = unpack_v2(payload)
            print(f"OK: {m['id']} / {m['name']} / runtime v2 / {len(ui['screens'])} scenes")
        else:
            m, images = unpack(payload)
            print(f"OK: {m['id']} / {m['name']} / v1 / {len(images)} assets")
    elif args.command == "build-v2":
        from runtime_package import build, read_assets
        manifest = json.loads(args.manifest.read_text("utf8"))
        ui = json.loads(args.ui.read_text("utf8"))
        result = build(manifest, ui, read_assets(manifest, args.assets_dir))
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(result)
        print(f"Built {args.out} ({len(result)} bytes)")
    elif args.command == "build-v3":
        from runtime_package_v3 import build
        manifest = json.loads(args.manifest.read_text("utf8"))
        result = build(manifest, args.source_dir)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(result)
        print(f"Built v3 {args.out} ({len(result)} bytes)")
    else:
        port = getattr(args, "port", 8765)
        with ThreadingHTTPServer(("127.0.0.1", port), Handler) as server:
            print(f"PowerCal Skin Studio http://127.0.0.1:{server.server_port}", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass


if __name__ == "__main__":
    main()

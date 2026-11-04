"""Self-contained programmable PowerCal .pcskin v3 builder/validator."""
from __future__ import annotations

import hashlib
import io
import json
import re
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

import skin_studio

MAX_ARCHIVE = 10_000_000
MAX_TOTAL = 16_000_000
MAX_FILES = 96
TEXT_LIMIT = 600_000
ASSET_LIMIT = 3_000_000
WEB_REQUIRED = ("web/index.html", "web/style.css", "web/app.js", "project.json")
PATH = re.compile(r"(?:web|assets)/[a-zA-Z0-9][a-zA-Z0-9_./-]{0,119}|project\.json\Z")
PAGE_TREE_RENDERER = "powercal-component-tree-v1"
PAGE_TREE_RUNTIME_FILE = "web/project-tree-v1.js"
HTML_RESOURCE = re.compile(r"\b(?:src|href|poster)\s*=\s*['\"]([^'\"]+)['\"]", re.IGNORECASE)
HTML_SRCSET = re.compile(r"\bsrcset\s*=\s*['\"]([^'\"]+)['\"]", re.IGNORECASE)
CSS_RESOURCE = re.compile(r"url\(\s*['\"]?([^\s)\"']+)['\"]?\s*\)", re.IGNORECASE)
CSS_IMPORT = re.compile(r"@import\s+(?:url\(\s*)?['\"]([^'\"]+)['\"]\s*\)?", re.IGNORECASE)


def check(ok, message):
    if not ok:
        raise skin_studio.SkinError(message)


def safe_path(name):
    p = PurePosixPath(name)
    return bool(PATH.fullmatch(name) and not p.is_absolute() and ".." not in p.parts and "\\" not in name)


def _local_resource_target(owner, reference):
    raw = reference.strip()
    if not raw or raw.startswith(("#", "//")):
        return None
    parsed = urlsplit(raw)
    if parsed.scheme or parsed.netloc:
        return None
    resource = unquote(parsed.path)
    if not resource:
        return None
    parts = [] if resource.startswith("/") else list(PurePosixPath(owner).parent.parts)
    for part in PurePosixPath(resource.lstrip("/")).parts:
        if part in ("", "."):
            continue
        if part == "..":
            if not parts:
                return ""
            parts.pop()
        else:
            parts.append(part)
    return "/".join(parts)


def _validate_local_resource_references(files):
    for owner, data in files.items():
        if not owner.startswith("web/") or not owner.lower().endswith((".html", ".htm", ".css")):
            continue
        try:
            text = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            raise skin_studio.SkinError("web source must be valid UTF-8: " + owner) from exc
        is_html = owner.lower().endswith((".html", ".htm"))
        is_css = owner.lower().endswith(".css")
        references = HTML_RESOURCE.findall(text) if is_html else []
        if is_html:
            for srcset in HTML_SRCSET.findall(text):
                expect_url = True
                for token in srcset.split():
                    if expect_url:
                        reference = token.rstrip(",")
                        if reference:
                            references.append(reference)
                    expect_url = token.endswith(",")
        if is_css:
            references.extend(CSS_RESOURCE.findall(text))
            references.extend(CSS_IMPORT.findall(text))
        for reference in references:
            raw = reference.strip()
            parsed = urlsplit(raw)
            check(not raw.startswith("//") and parsed.scheme.lower() not in {"http", "https"},
                  "remote resources are forbidden: " + owner + " -> " + reference)
            if is_css:
                check(not parsed.scheme or parsed.scheme.lower() == "data",
                      "external CSS resources are forbidden: " + owner + " -> " + reference)
            target = _local_resource_target(owner, reference)
            if target is None:
                continue
            check(bool(target) and target in files,
                  "referenced resource is missing or escapes package: " + owner + " -> " + reference)


def _validate_component_nodes(nodes, all_ids, depth=0, total=None):
    check(type(nodes) is list and len(nodes) <= 128, "components must be a bounded array")
    if total is None:
        total = [0]
    check(depth <= 16, "component tree is too deep")
    for node in nodes:
        check(type(node) is dict, "invalid component node")
        ident = node.get("id")
        check(type(ident) is str and re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}", ident)
              and ident not in all_ids, "invalid or repeated project node id")
        all_ids.add(ident)
        component_type = node.get("type")
        check(type(component_type) is str and re.fullmatch(r"[a-z][a-z0-9-]{0,63}", component_type),
              "invalid component type")
        props = node.get("props", {})
        check(type(props) is dict, "component props must be an object")
        total[0] += 1
        check(total[0] <= 512, "project has too many component nodes")
        children = node.get("children", [])
        check(type(children) is list, "component children must be an array")
        _validate_component_nodes(children, all_ids, depth + 1, total)


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
    pages = project.get("pages", [])
    check(type(pages) is list and len(pages) <= 16, "pages must be a bounded array")
    routes = set()
    node_total = [0]
    for page in pages:
        check(type(page) is dict, "invalid page metadata")
        ident, route, title = page.get("id"), page.get("route"), page.get("title")
        check(type(ident) is str and re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}", ident)
              and ident not in ids, "invalid or repeated project node id")
        ids.add(ident)
        check(type(route) is str and re.fullmatch(r"[A-Z][A-Z0-9_]{0,31}", route)
              and route not in routes, "invalid or repeated page route")
        routes.add(route)
        check(type(title) is str and 1 <= len(title.strip()) <= 60, "invalid page title")
        _validate_component_nodes(page.get("components", []), ids, total=node_total)
    from host_binding_catalog import validate_project_bindings
    try:
        validate_project_bindings(project)
    except ValueError as exc:
        raise skin_studio.SkinError(str(exc)) from exc
    return project


def _page_tree_runtime_source(project):
    """Serialize the canonical project tree as inert, packaged runtime data."""
    document = json.dumps(project, ensure_ascii=False, separators=(",", ":"))
    document = document.replace("<", r"\u003c").replace("\u2028", r"\u2028").replace("\u2029", r"\u2029")
    literal = json.dumps(document, ensure_ascii=False)
    literal = literal.replace("\u2028", r"\u2028").replace("\u2029", r"\u2029")
    return ("window.__POWERCAL_PAGE_TREE_V1__=JSON.parse(" + literal + ");\n").encode("utf-8")

def validate_manifest(manifest, files, verify_hash=True):
    check(type(manifest) is dict, "manifest must be an object")
    # Preserve unknown top-level v3 metadata losslessly. Android ignores these
    # fields; only the explicitly parsed capabilities field can affect Host access.
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
    project = validate_project(json.loads(files["project.json"].decode("utf8")))
    if project.get("runtimeRenderer") == PAGE_TREE_RENDERER:
        check(files.get(PAGE_TREE_RUNTIME_FILE) == _page_tree_runtime_source(project),
              "blank page-tree runtime does not match project.json")
    html = files["web/index.html"].decode("utf8")
    loads_page_tree_runtime = re.search(
        r"<script\b[^>]*\bsrc=[\"'](?:\./)?project-tree-v1\.js[\"']",
        html, re.IGNORECASE) is not None
    if project.get("runtimeRenderer") == PAGE_TREE_RENDERER:
        check(loads_page_tree_runtime,
              "blank page-tree runtime script is not loaded by web/index.html")
    else:
        check(not loads_page_tree_runtime,
              "project-tree-v1.js requires the blank page-tree runtime contract")
    check("<iframe" not in html.lower() and "<object" not in html.lower(),
          "iframes and embedded objects are forbidden")
    check("<script" not in html.lower() or "src=\"app.js\"" in html or "src='app.js'" in html,
          "index.html may only load packaged app.js")
    check("http://" not in html.lower() and "https://" not in html.lower(),
          "remote resources are forbidden")
    _validate_local_resource_references(files)
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
    package_files = dict(files)
    project = validate_project(json.loads(package_files["project.json"].decode("utf8")))
    if project.get("runtimeRenderer") == PAGE_TREE_RENDERER:
        # This path is a deterministic runtime projection of project.json. The
        # builder regenerates it for preview, export, import/rebuild and Android.
        package_files[PAGE_TREE_RUNTIME_FILE] = _page_tree_runtime_source(project)
    checked = validate_manifest(manifest, package_files, verify_hash=False)
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
        for name in sorted(package_files):
            archive.writestr(fixed_entry(name), package_files[name])
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

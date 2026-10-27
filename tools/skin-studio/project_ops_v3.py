"""Machine-operable, validated edits for self-contained PowerCal v3 skins."""
from __future__ import annotations

import base64
import binascii
import copy
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import tempfile

import runtime_package_v3 as package_v3
import skin_studio

MAX_OPERATIONS = 128
MAX_PATCH_OPERATIONS = 256
MAX_OPERATION_DOCUMENT = 24_000_000
SHA256 = re.compile(r"[0-9a-fA-F]{64}\Z")
REQUIRED_FILES = frozenset(package_v3.WEB_REQUIRED)
MAX_ANALYZED_COMPONENTS = 20_000
VOID_HTML_TAGS = frozenset({
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
    "meta", "param", "source", "track", "wbr",
})
TEXT_EXTENSIONS = frozenset({
    ".cjs", ".css", ".html", ".htm", ".ini", ".js", ".json", ".jsx", ".kt",
    ".less", ".md", ".mjs", ".properties", ".py", ".scss", ".sh", ".sql",
    ".toml", ".ts", ".tsx", ".txt", ".xml", ".yaml", ".yml", ".svg",
})


def _fail(condition: bool, message: str) -> None:
    package_v3.check(condition, message)


def _file_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _is_text_path(path: str) -> bool:
    return Path(path).suffix.lower() in TEXT_EXTENSIONS


def _manifest_sha256(manifest: dict) -> str:
    canonical = json.dumps(manifest, ensure_ascii=False, sort_keys=True,
                           separators=(",", ":")).encode("utf-8")
    return _file_sha256(canonical)


def _validate_path(path: object) -> str:
    _fail(type(path) is str and package_v3.safe_path(path),
          "file path must be a safe web/ or assets/ path, or project.json")
    _fail(path != "manifest.json", "manifest.json is managed by the package builder")
    return path


def _expect_existing(files: dict[str, bytes], path: str, expected: object) -> bytes:
    _fail(type(expected) is str and SHA256.fullmatch(expected),
          "expectedSha256 is required for edits to an existing file")
    _fail(path in files, "source file does not exist: " + path)
    current = files[path]
    _fail(_file_sha256(current).lower() == expected.lower(),
          "source file changed since inspection: " + path)
    return current


def _json_pointer_tokens(pointer: object) -> list[str]:
    _fail(type(pointer) is str and (pointer == "" or pointer.startswith("/")),
          "JSON Pointer must be empty or start with '/' ")
    if pointer == "":
        return []
    tokens = []
    for token in pointer[1:].split("/"):
        _fail(re.search(r"~(?![01])", token) is None, "invalid JSON Pointer escape")
        tokens.append(token.replace("~1", "/").replace("~0", "~"))
    return tokens


def _array_index(token: str, length: int, *, allow_end: bool) -> int:
    _fail(token != "-" and re.fullmatch(r"0|[1-9][0-9]*", token) is not None,
          "JSON Pointer array index is invalid")
    index = int(token)
    _fail(index <= length if allow_end else index < length,
          "JSON Pointer array index is out of range")
    return index


def _parent_and_key(document: object, tokens: list[str], *, allow_end: bool = False):
    _fail(bool(tokens), "operation cannot address the JSON document root here")
    parent = document
    for token in tokens[:-1]:
        if isinstance(parent, dict):
            _fail(token in parent, "JSON Pointer parent does not exist")
            parent = parent[token]
        elif isinstance(parent, list):
            parent = parent[_array_index(token, len(parent), allow_end=False)]
        else:
            raise skin_studio.SkinError("JSON Pointer parent is not a container")
    key = tokens[-1]
    if isinstance(parent, list):
        if key == "-" and allow_end:
            return parent, len(parent)
        key = _array_index(key, len(parent), allow_end=allow_end)
    elif isinstance(parent, dict):
        pass
    else:
        raise skin_studio.SkinError("JSON Pointer parent is not a container")
    return parent, key


def _pointer_get(document: object, pointer: object) -> object:
    current = document
    for token in _json_pointer_tokens(pointer):
        if isinstance(current, dict):
            _fail(token in current, "JSON Pointer target does not exist")
            current = current[token]
        elif isinstance(current, list):
            current = current[_array_index(token, len(current), allow_end=False)]
        else:
            raise skin_studio.SkinError("JSON Pointer target is not addressable")
    return current


def _pointer_add(document: object, pointer: object, value: object) -> object:
    tokens = _json_pointer_tokens(pointer)
    if not tokens:
        return value
    parent, key = _parent_and_key(document, tokens, allow_end=True)
    if isinstance(parent, list):
        parent.insert(key, value)
    else:
        parent[key] = value
    return document


def _pointer_remove(document: object, pointer: object) -> tuple[object, object]:
    tokens = _json_pointer_tokens(pointer)
    _fail(bool(tokens), "removing the project document root is not supported")
    parent, key = _parent_and_key(document, tokens)
    if isinstance(parent, list):
        return document, parent.pop(key)
    _fail(key in parent, "JSON Pointer target does not exist")
    return document, parent.pop(key)


def _apply_json_patch(document: object, operations: object) -> object:
    _fail(type(operations) is list and 0 < len(operations) <= MAX_PATCH_OPERATIONS,
          f"patch must contain 1–{MAX_PATCH_OPERATIONS} operations")
    result = copy.deepcopy(document)
    for index, operation in enumerate(operations):
        _fail(type(operation) is dict, f"patch operation {index} must be an object")
        op = operation.get("op")
        allowed_fields = {
            "add": {"op", "path", "value"},
            "remove": {"op", "path"},
            "replace": {"op", "path", "value"},
            "move": {"op", "path", "from"},
            "copy": {"op", "path", "from"},
            "test": {"op", "path", "value"},
        }.get(op)
        _fail(allowed_fields is not None and set(operation) == allowed_fields,
              f"patch operation {index} has an unsupported op or fields")
        path = operation["path"]
        tokens = _json_pointer_tokens(path)
        if op == "add":
            result = _pointer_add(result, path, copy.deepcopy(operation["value"]))
        elif op == "remove":
            result, _ = _pointer_remove(result, path)
        elif op == "replace":
            _pointer_get(result, path)
            if not tokens:
                result = copy.deepcopy(operation["value"])
            else:
                result, _ = _pointer_remove(result, path)
                result = _pointer_add(result, path, copy.deepcopy(operation["value"]))
        elif op == "move":
            source_tokens = _json_pointer_tokens(operation["from"])
            _fail(path != operation["from"] and
                  not (len(tokens) > len(source_tokens) and
                       tokens[:len(source_tokens)] == source_tokens),
                  "cannot move a JSON value into itself or a descendant")
            result, value = _pointer_remove(result, operation["from"])
            result = _pointer_add(result, path, value)
        elif op == "copy":
            result = _pointer_add(result, path,
                                  copy.deepcopy(_pointer_get(result, operation["from"])))
        else:
            _fail(_pointer_get(result, path) == operation["value"],
                  f"JSON Patch test failed at {path!r}")
    return result


def _apply_one(files: dict[str, bytes], operation: object) -> dict[str, bytes]:
    _fail(type(operation) is dict, "each operation must be a JSON object")
    op = operation.get("op")
    if op == "write":
        _fail(set(operation) in (
            {"op", "path", "encoding", "content", "expectedSha256"},
            {"op", "path", "encoding", "content", "expectedAbsent"}),
            "write requires exactly one concurrency guard: expectedSha256 or expectedAbsent=true")
        path = _validate_path(operation["path"])
        if "expectedSha256" in operation:
            _expect_existing(files, path, operation["expectedSha256"])
        else:
            _fail(operation["expectedAbsent"] is True and path not in files,
                  "new file requires expectedAbsent=true and an unused path")
        encoding, content = operation["encoding"], operation["content"]
        _fail(type(content) is str, "write content must be a string")
        if encoding == "utf-8":
            data = content.encode("utf-8")
        elif encoding == "base64":
            _fail(len(content) <= 4_100_000, "base64 content exceeds the per-operation limit")
            try:
                data = base64.b64decode(content, validate=True)
            except (binascii.Error, ValueError) as exc:
                raise skin_studio.SkinError("write content is not valid base64") from exc
        else:
            raise skin_studio.SkinError("encoding must be 'utf-8' or 'base64'")
        limit = package_v3.TEXT_LIMIT if path.endswith((".html", ".css", ".js", ".json")) else package_v3.ASSET_LIMIT
        _fail(len(data) <= limit, "write content exceeds the package per-file limit")
        files[path] = data
        return files

    if op == "delete":
        _fail(set(operation) == {"op", "path", "expectedSha256"},
              "delete requires op, path, and expectedSha256")
        path = _validate_path(operation["path"])
        _fail(path not in REQUIRED_FILES, "required v3 source files cannot be deleted")
        _expect_existing(files, path, operation["expectedSha256"])
        del files[path]
        return files

    if op == "move":
        _fail(set(operation) == {"op", "from", "to", "expectedSha256"},
              "move requires op, from, to, and expectedSha256")
        source, target = _validate_path(operation["from"]), _validate_path(operation["to"])
        _fail(source != target and target not in files, "move destination must be unused")
        _expect_existing(files, source, operation["expectedSha256"])
        files[target] = files.pop(source)
        return files

    if op == "json-patch":
        _fail(set(operation) == {"op", "path", "expectedSha256", "patch"},
              "json-patch requires op, path, expectedSha256, and patch")
        path = _validate_path(operation["path"])
        _fail(path == "project.json", "json-patch is limited to project.json")
        current = _expect_existing(files, path, operation["expectedSha256"])
        try:
            project = json.loads(current.decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise skin_studio.SkinError("project.json is not valid UTF-8 JSON") from exc
        updated = _apply_json_patch(project, operation["patch"])
        files[path] = (json.dumps(updated, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        return files

    raise skin_studio.SkinError("unsupported project operation: " + str(op))


def _patch_manifest(manifest: dict, operation: dict) -> dict:
    allowed = {"op", "expectedSha256", "patch"}
    if "allowCapabilityChange" in operation:
        allowed.add("allowCapabilityChange")
        _fail(type(operation["allowCapabilityChange"]) is bool,
              "allowCapabilityChange must be a boolean")
    _fail(set(operation) == allowed, "manifest-patch has unsupported or missing fields")
    _fail(type(operation["expectedSha256"]) is str and
          SHA256.fullmatch(operation["expectedSha256"]),
          "manifest-patch requires expectedSha256")
    _fail(_manifest_sha256(manifest).lower() == operation["expectedSha256"].lower(),
          "manifest changed since inspection")
    updated = _apply_json_patch(manifest, operation["patch"])
    _fail(type(updated) is dict, "manifest root must remain an object")
    protected = {"format", "schemaVersion", "entrypoint", "project", "sha256"}
    _fail(all(updated.get(key) == manifest.get(key) for key in protected),
          "format, schemaVersion, entrypoint, project, and sha256 are managed fields")
    capabilities_changed = updated.get("capabilities", []) != manifest.get("capabilities", [])
    _fail(not capabilities_changed or operation.get("allowCapabilityChange") is True,
          "capability changes require allowCapabilityChange=true")
    return updated


def edit_package(source: bytes, operations: object) -> tuple[dict, dict[str, bytes], bytes, list[dict]]:
    """Apply a bounded operation batch and validate/build it before returning bytes."""
    manifest, original_files = package_v3.unpack(source)
    _fail(type(operations) is list and 0 < len(operations) <= MAX_OPERATIONS,
          f"operations must contain 1–{MAX_OPERATIONS} items")
    files = dict(original_files)
    changes = []
    for index, operation in enumerate(operations):
        _fail(type(operation) is dict, "each operation must be a JSON object")
        if operation.get("op") == "manifest-patch":
            before_manifest = manifest
            manifest = _patch_manifest(manifest, operation)
            changes.append({
                "operationIndex": index,
                "op": "manifest-patch",
                "path": "manifest.json",
                "beforeSha256": _manifest_sha256(before_manifest),
                "afterSha256": _manifest_sha256(manifest),
            })
            continue
        before = dict(files)
        files = _apply_one(files, operation)
        changed_paths = sorted(set(before) | set(files))
        for path in changed_paths:
            if before.get(path) != files.get(path):
                changes.append({
                    "operationIndex": index,
                    "op": operation["op"],
                    "path": path,
                    "beforeSha256": _file_sha256(before[path]) if path in before else None,
                    "afterSha256": _file_sha256(files[path]) if path in files else None,
                })
    # This validates the final package and produces deterministic export bytes.
    compiled = package_v3.build_files(manifest, files)
    final_manifest, final_files = package_v3.unpack(compiled)
    return final_manifest, final_files, compiled, changes


def inspect_package(source: bytes) -> dict:
    manifest, files = package_v3.unpack(source)
    try:
        project = json.loads(files["project.json"].decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise skin_studio.SkinError("project.json is not valid UTF-8 JSON") from exc
    file_index = []
    for path, data in sorted(files.items()):
        encoding = "binary"
        if _is_text_path(path):
            try:
                data.decode("utf-8", errors="strict")
                encoding = "utf-8"
            except UnicodeDecodeError:
                pass
        file_index.append({"path": path, "bytes": len(data),
                           "sha256": _file_sha256(data), "encoding": encoding})
    return {
        "schemaVersion": 1,
        "command": "inspect-v3",
        "packageSha256": _file_sha256(source),
        "manifestSha256": _manifest_sha256(manifest),
        "manifest": manifest,
        "fileCount": len(files),
        "inflatedBytes": sum(len(data) for data in files.values()),
        "files": file_index,
        "project": project,
    }


class _StaticHtmlInventory(HTMLParser):
    """Build a bounded source-nesting index without rewriting HTML bytes."""

    def __init__(self, path: str):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.nodes: list[dict] = []
        self.stack: list[str | None] = [None]
        self.by_id: dict[str, dict] = {}
        self.title_parts: list[str] = []
        self.script_sources: list[str] = []
        self.stylesheet_sources: list[str] = []
        self.inline_script_count = 0
        self.warnings: list[str] = []
        self._script_depth = 0
        self._style_depth = 0

    def parse(self, source: str) -> dict:
        self.feed(source)
        self.close()
        for node_id in self.stack[1:]:
            if node_id is not None:
                node = self.by_id[node_id]
                self.warnings.append(
                    f"unclosed <{node['tag']}> beginning at line {node['line']}"
                )
        self.warnings.append(
            "JavaScript is not executed; runtime-created components are absent from this inventory"
        )
        return {
            "path": self.path,
            "sourceSha256": _file_sha256(source.encode("utf-8")),
            "title": " ".join(" ".join(self.title_parts).split())[:240],
            "treeKind": "explicit-source-nesting",
            "componentCount": len(self.nodes),
            "interactiveCount": sum(1 for node in self.nodes if self._is_interactive(node)),
            "scriptSources": self.script_sources,
            "stylesheetSources": self.stylesheet_sources,
            "inlineScriptCount": self.inline_script_count,
            "duplicateIds": self._duplicate_ids(),
            "components": self.nodes,
            "warnings": list(dict.fromkeys(self.warnings)),
        }

    @staticmethod
    def _is_interactive(node: dict) -> bool:
        values = {item["name"]: item["value"] for item in node["attributes"]}
        return (node["tag"] in {"a", "button", "input", "select", "textarea"} or
                values.get("role") in {"button", "link", "checkbox", "radio", "tab"} or
                "data-action" in values or "data-local" in values)

    def _duplicate_ids(self) -> list[str]:
        counts: dict[str, int] = {}
        for node in self.nodes:
            ident = node.get("id")
            if ident:
                counts[ident] = counts.get(ident, 0) + 1
        return sorted(ident for ident, count in counts.items() if count > 1)

    def _add_element(self, tag: str, attrs: list[tuple[str, str | None]], self_closing: bool) -> dict:
        if len(self.nodes) >= MAX_ANALYZED_COMPONENTS:
            raise skin_studio.SkinError(
                f"HTML inventory exceeds {MAX_ANALYZED_COMPONENTS} components: {self.path}"
            )
        line, column = self.getpos()
        node_id = f"{self.path}#n{len(self.nodes) + 1:05d}"
        parent_id = self.stack[-1]
        normalized = [{"name": name.lower(), "value": value} for name, value in attrs]
        values = {item["name"]: item["value"] for item in normalized}
        node = {
            "nodeId": node_id,
            "parentId": parent_id,
            "tag": tag.lower(),
            "id": values.get("id"),
            "component": values.get("data-component"),
            "line": line,
            "column": column + 1,
            "attributes": normalized,
            "directText": "",
        }
        self.nodes.append(node)
        self.by_id[node_id] = node
        if parent_id is not None:
            self.by_id[parent_id].setdefault("childIds", []).append(node_id)
        if tag.lower() == "script":
            src = values.get("src")
            if src:
                self.script_sources.append(src)
            else:
                self.inline_script_count += 1
        if tag.lower() == "link" and "stylesheet" in (values.get("rel") or "").lower().split():
            href = values.get("href")
            if href:
                self.stylesheet_sources.append(href)
        if not self_closing and tag.lower() not in VOID_HTML_TAGS:
            self.stack.append(node_id)
            if tag.lower() == "script":
                self._script_depth += 1
            elif tag.lower() == "style":
                self._style_depth += 1
        return node

    def handle_starttag(self, tag, attrs):
        self._add_element(tag, attrs, self_closing=False)

    def handle_startendtag(self, tag, attrs):
        self._add_element(tag, attrs, self_closing=True)

    def handle_endtag(self, tag):
        lowered = tag.lower()
        match = next((index for index in range(len(self.stack) - 1, 0, -1)
                      if self.by_id[self.stack[index]]["tag"] == lowered), None)
        if match is None:
            self.warnings.append(f"unmatched closing tag </{lowered}> at line {self.getpos()[0]}")
            return
        for node_id in self.stack[match:]:
            closing = self.by_id[node_id]["tag"]
            if closing == "script":
                self._script_depth = max(0, self._script_depth - 1)
            elif closing == "style":
                self._style_depth = max(0, self._style_depth - 1)
        del self.stack[match:]

    def handle_data(self, data):
        if self.stack[-1] is None:
            return
        current = self.by_id[self.stack[-1]]
        if current["tag"] == "title":
            self.title_parts.append(data)
        if self._script_depth == 0 and self._style_depth == 0 and data.strip():
            pieces = [current["directText"], " ".join(data.split())]
            current["directText"] = " ".join(piece for piece in pieces if piece)[:240]


def analyze_package(source: bytes) -> dict:
    """Return a machine-readable static source inventory for HTML pages in a v3 skin."""
    manifest, files = package_v3.unpack(source)
    pages = []
    total_components = 0
    for path, data in sorted(files.items()):
        if not path.lower().endswith((".html", ".htm")) or not path.startswith("web/"):
            continue
        try:
            html = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            raise skin_studio.SkinError(f"HTML page is not UTF-8: {path}") from exc
        page = _StaticHtmlInventory(path).parse(html)
        total_components += page["componentCount"]
        _fail(total_components <= MAX_ANALYZED_COMPONENTS,
              f"HTML inventory exceeds {MAX_ANALYZED_COMPONENTS} components")
        pages.append(page)
    return {
        "schemaVersion": 1,
        "command": "analyze-v3",
        "ok": True,
        "skin": {"id": manifest["id"], "name": manifest["name"],
                 "packageSha256": _file_sha256(source)},
        "pageCount": len(pages),
        "componentCount": total_components,
        "analysisScope": "static HTML source; no JavaScript execution or computed browser DOM",
        "pages": pages,
    }


def read_file(source: bytes, path: str) -> dict:
    _validate_path(path)
    manifest, files = package_v3.unpack(source)
    _fail(path in files, "source file does not exist: " + path)
    data = files[path]
    result = {"schemaVersion": 1, "command": "read-v3", "path": path,
              "bytes": len(data), "sha256": _file_sha256(data)}
    content = None
    if _is_text_path(path):
        try:
            content = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            pass
    if content is None:
        result["encoding"] = "base64"
        result["content"] = base64.b64encode(data).decode("ascii")
    else:
        result["encoding"] = "utf-8"
        result["content"] = content
    return result


def read_operation_document(path: Path) -> list[dict]:
    raw = path.read_bytes()
    _fail(len(raw) <= MAX_OPERATION_DOCUMENT, "operation document exceeds 24MB")
    try:
        document = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise skin_studio.SkinError("operation document must be UTF-8 JSON") from exc
    _fail(type(document) is dict and set(document) == {"operations"},
          "operation document must be an object containing only 'operations'")
    return document["operations"]


def write_atomic(path: Path, data: bytes) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise skin_studio.SkinError("output already exists; choose a new output path")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(prefix=".powercal-skin-", suffix=".tmp",
                                         dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        if os.name == "nt":
            # Windows rename refuses an existing destination, so publication is
            # atomic without a check-then-overwrite race.
            os.rename(temporary, path)
        else:
            # POSIX rename may replace an existing target; hard-link creation is
            # atomic and fails if another writer created it after our check.
            os.link(temporary, path)
            temporary.unlink()
    except OSError as exc:
        raise skin_studio.SkinError("could not write output atomically: " + str(exc)) from exc
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()

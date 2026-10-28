"""Machine-operable, validated edits for self-contained PowerCal v3 skins."""
from __future__ import annotations

import base64
import binascii
import copy
from collections import Counter
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
MAX_COMPONENT_ATTRIBUTES = 256
BRIDGE_V1_ACTION_REGISTRY = Path(__file__).with_name("bridge_v1_action_registry.json")
BRIDGE_V1_CONTRACT_SCHEMA = Path(__file__).resolve().parents[2] / (
    "docs/contracts/web-skin-bridge-v1.schema.json")
VOID_HTML_TAGS = frozenset({
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
    "meta", "param", "source", "track", "wbr",
})
PROTECTED_COMPONENT_TAGS = frozenset({"html", "head", "body"})
RAW_TEXT_COMPONENT_TAGS = frozenset({"script", "style", "textarea", "title"})
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


def _component_page(files: dict[str, bytes], path: object, expected: object):
    path = _validate_path(path)
    _fail(path.startswith("web/") and path.lower().endswith((".html", ".htm")),
          "component operations require a web/ .html or .htm page")
    data = _expect_existing(files, path, expected)
    try:
        source = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise skin_studio.SkinError("HTML page is not UTF-8: " + path) from exc
    parser = _StaticHtmlInventory(path)
    parser.parse(source)
    _fail(not parser.structural_issues,
          "component operations require well-formed explicit HTML: " +
          "; ".join(parser.structural_issues[:4]))
    return path, source, parser


def _component_node(parser: _StaticHtmlInventory, node_id: object) -> dict:
    _fail(type(node_id) is str and node_id in parser.by_id,
          "component nodeId is missing or stale; run analyze-v3 again")
    node = parser.by_id[node_id]
    _fail(node["_sourceMutable"] and node["_sourceEnd"] is not None,
          "component has no safe explicit source range")
    return node


def _component_fragment(value: object) -> tuple[str, _StaticHtmlInventory]:
    _fail(type(value) is str and bool(value.strip()),
          "component html must be a non-empty string")
    _fail(len(value.encode("utf-8")) <= package_v3.TEXT_LIMIT,
          "component html exceeds the package text-file limit")
    parser = _StaticHtmlInventory("web/__component_fragment__.html")
    parser.parse(value)
    _fail(not parser.structural_issues,
          "component html must be a well-formed HTML fragment")
    _fail(bool(parser.nodes), "component html must contain at least one element")
    _fail(not any(node["tag"] in PROTECTED_COMPONENT_TAGS for node in parser.nodes),
          "component html cannot add document shell elements")
    return value, parser


def _validate_component_result(path: str, before: _StaticHtmlInventory,
                               result: str) -> bytes:
    parser = _StaticHtmlInventory(path)
    parser.parse(result)
    _fail(not parser.structural_issues,
          "component edit would make the HTML source structurally invalid: " +
          "; ".join(parser.structural_issues[:4]))
    old_ids = Counter(node["id"] for node in before.nodes if node.get("id"))
    new_ids = Counter(node["id"] for node in parser.nodes if node.get("id"))
    duplicates = sorted(ident for ident, count in new_ids.items()
                        if count > max(1, old_ids.get(ident, 0)))
    _fail(not duplicates,
          "component edit would create duplicate HTML id: " + ", ".join(duplicates[:8]))
    encoded = result.encode("utf-8")
    _fail(len(encoded) <= package_v3.TEXT_LIMIT,
          "component edit exceeds the package text-file limit")
    return encoded


def _component_is_descendant(parser: _StaticHtmlInventory,
                             ancestor_id: str, node_id: str) -> bool:
    current = parser.by_id[node_id].get("parentId")
    while current is not None:
        if current == ancestor_id:
            return True
        current = parser.by_id[current].get("parentId")
    return False


def _component_insert_offset(node: dict, position: object) -> int:
    if position == "before":
        return node["_sourceStart"]
    if position == "after":
        return node["_sourceEnd"]
    if position == "prepend":
        _fail(node["tag"] not in VOID_HTML_TAGS and node["_sourceCloseStart"] is not None,
              "prepend requires a paired container element")
        _fail(node["tag"] not in RAW_TEXT_COMPONENT_TAGS,
              "cannot insert an HTML component inside a raw-text element")
        return node["_sourceOpenEnd"]
    if position == "append":
        _fail(node["tag"] not in VOID_HTML_TAGS and node["_sourceCloseStart"] is not None,
              "append requires a paired container element")
        _fail(node["tag"] not in RAW_TEXT_COMPONENT_TAGS,
              "cannot insert an HTML component inside a raw-text element")
        return node["_sourceCloseStart"]
    raise skin_studio.SkinError("position must be before, after, prepend, or append")


def _normalize_component_attribute_name(value: object) -> str:
    _fail(type(value) is str and len(value) <= 256 and
          re.fullmatch(r"[^\s\"'<>/=]+", value) is not None and
          not any(ord(character) < 0x20 or ord(character) == 0x7f
                  for character in value),
          "attribute name must be a non-empty HTML attribute token")
    return value.lower()


def _apply_component_attributes(files: dict[str, bytes], operation: dict) -> dict[str, bytes]:
    fields = {"op", "path", "expectedSha256", "targetNodeId",
              "setAttributes", "removeAttributes"}
    _fail(set(operation) == fields, "component-attributes has unsupported or missing fields")
    path, source, parser = _component_page(
        files, operation["path"], operation["expectedSha256"])
    node = _component_node(parser, operation["targetNodeId"])

    raw_set = operation["setAttributes"]
    raw_remove = operation["removeAttributes"]
    _fail(type(raw_set) is dict and len(raw_set) <= MAX_COMPONENT_ATTRIBUTES,
          f"setAttributes must be an object with at most {MAX_COMPONENT_ATTRIBUTES} entries")
    _fail(type(raw_remove) is list and len(raw_remove) <= MAX_COMPONENT_ATTRIBUTES,
          f"removeAttributes must be an array with at most {MAX_COMPONENT_ATTRIBUTES} entries")

    set_attributes: dict[str, str | None] = {}
    for raw_name, value in raw_set.items():
        name = _normalize_component_attribute_name(raw_name)
        _fail(name not in set_attributes,
              "setAttributes contains duplicate names after case normalization")
        _fail(value is None or type(value) is str,
              f"attribute value for {name!r} must be a string or null for a boolean attribute")
        try:
            value_bytes = value.encode("utf-8") if value is not None else b""
        except UnicodeEncodeError as exc:
            raise skin_studio.SkinError(f"attribute value for {name!r} is not valid Unicode") from exc
        _fail(value is None or ("\x00" not in value and
                                len(value_bytes) <= package_v3.TEXT_LIMIT),
              f"attribute value for {name!r} is invalid or too large")
        set_attributes[name] = value

    remove_attributes: set[str] = set()
    for raw_name in raw_remove:
        name = _normalize_component_attribute_name(raw_name)
        _fail(name not in remove_attributes,
              "removeAttributes contains duplicate names after case normalization")
        remove_attributes.add(name)
    _fail(bool(set_attributes or remove_attributes),
          "component-attributes must set or remove at least one attribute")
    _fail(not (set(set_attributes) & remove_attributes),
          "an attribute cannot be both set and removed")

    current_values: dict[str, list[str | None]] = {}
    for attribute in node["attributes"]:
        current_values.setdefault(attribute["name"].lower(), []).append(attribute["value"])
    set_attributes = {name: value for name, value in set_attributes.items()
                      if current_values.get(name) != [value]}
    remove_attributes = {name for name in remove_attributes if name in current_values}
    if not set_attributes and not remove_attributes:
        return files

    attributes: list[dict] = []
    applied: set[str] = set()
    for attribute in node["attributes"]:
        name = attribute["name"].lower()
        if name in remove_attributes:
            continue
        if name in set_attributes:
            if name not in applied:
                attributes.append({"name": name, "value": set_attributes[name]})
                applied.add(name)
            continue
        attributes.append(attribute)
    for name, value in set_attributes.items():
        if name not in applied:
            attributes.append({"name": name, "value": value})

    opening = source[node["_sourceStart"]:node["_sourceOpenEnd"]]
    _fail(bool(opening) and opening.startswith("<"),
          "selected component has no safe opening tag")
    closing = "/>" if re.search(r"/\s*>$", opening) else ">"
    rendered = "<" + node["tag"]
    for attribute in attributes:
        rendered += " " + attribute["name"]
        value = attribute["value"]
        if value is not None:
            escaped = (value.replace("&", "&amp;").replace("\"", "&quot;")
                       .replace("<", "&lt;").replace(">", "&gt;"))
            rendered += '="' + escaped + '"'
    rendered += closing
    start, end = node["_sourceStart"], node["_sourceOpenEnd"]
    result = source[:start] + rendered + source[end:]
    files[path] = _validate_component_result(path, parser, result)
    return files


def _apply_component_operation(files: dict[str, bytes], operation: dict) -> dict[str, bytes]:
    op = operation.get("op")
    if op == "component-attributes":
        return _apply_component_attributes(files, operation)
    common = {"op", "path", "expectedSha256"}
    fields = {
        "component-insert": common | {"targetNodeId", "position", "html"},
        "component-remove": common | {"targetNodeId"},
        "component-replace": common | {"targetNodeId", "html"},
        "component-move": common | {"sourceNodeId", "targetNodeId", "position"},
    }.get(op)
    _fail(fields is not None and set(operation) == fields,
          str(op) + " has unsupported or missing fields")
    path, source, parser = _component_page(
        files, operation["path"], operation["expectedSha256"])

    if op == "component-insert":
        target = _component_node(parser, operation["targetNodeId"])
        fragment, _ = _component_fragment(operation["html"])
        offset = _component_insert_offset(target, operation["position"])
        result = source[:offset] + fragment + source[offset:]
        # Validate against the original page so existing duplicate IDs are retained,
        # while a new duplicate introduced by this fragment is rejected.
        files[path] = _validate_component_result(path, parser, result)
        return files

    if op in {"component-remove", "component-replace"}:
        target = _component_node(parser, operation["targetNodeId"])
        _fail(target["tag"] not in PROTECTED_COMPONENT_TAGS,
              "document shell elements cannot be removed or replaced")
        start, end = target["_sourceStart"], target["_sourceEnd"]
        replacement = ""
        if op == "component-replace":
            replacement, _ = _component_fragment(operation["html"])
        result = source[:start] + replacement + source[end:]
        files[path] = _validate_component_result(path, parser, result)
        return files

    source_node = _component_node(parser, operation["sourceNodeId"])
    target = _component_node(parser, operation["targetNodeId"])
    _fail(source_node["nodeId"] != target["nodeId"],
          "a component cannot be moved relative to itself")
    _fail(source_node["tag"] not in PROTECTED_COMPONENT_TAGS,
          "document shell elements cannot be moved")
    _fail(not _component_is_descendant(parser, source_node["nodeId"], target["nodeId"]),
          "a component cannot be moved into its own descendant")
    start, end = source_node["_sourceStart"], source_node["_sourceEnd"]
    offset = _component_insert_offset(target, operation["position"])
    _fail(not start < offset < end,
          "a component cannot be moved into its own source range")
    fragment = source[start:end]
    without_source = source[:start] + source[end:]
    if start < offset:
        offset -= end - start
    result = without_source[:offset] + fragment + without_source[offset:]
    files[path] = _validate_component_result(path, parser, result)
    return files


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

    if op in {"component-insert", "component-remove", "component-attributes",
              "component-replace", "component-move"}:
        return _apply_component_operation(files, operation)

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
        self.source = ""
        self._line_offsets: list[int] = [0]
        self.nodes: list[dict] = []
        self.stack: list[str | None] = [None]
        self.by_id: dict[str, dict] = {}
        self.title_parts: list[str] = []
        self.script_sources: list[str] = []
        self.stylesheet_sources: list[str] = []
        self.inline_script_count = 0
        self.warnings: list[str] = []
        self.structural_issues: list[str] = []
        self._script_depth = 0
        self._style_depth = 0

    def parse(self, source: str) -> dict:
        self.source = source
        self._line_offsets = [0]
        self._line_offsets.extend(match.end() for match in re.finditer("\n", source))
        self.feed(source)
        self.close()
        for node_id in self.stack[1:]:
            if node_id is not None:
                node = self.by_id[node_id]
                message = f"unclosed <{node['tag']}> beginning at line {node['line']}"
                self.warnings.append(message)
                self.structural_issues.append(message)
                node["_sourceMutable"] = False
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
            "components": [self._public_node(node) for node in self.nodes],
            "warnings": list(dict.fromkeys(self.warnings)),
        }

    @staticmethod
    def _public_node(node: dict) -> dict:
        return {key: value for key, value in node.items() if not key.startswith("_")}

    def _source_offset(self, position: tuple[int, int]) -> int:
        line, column = position
        _fail(1 <= line <= len(self._line_offsets), "HTML parser returned an invalid source line")
        return self._line_offsets[line - 1] + column

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
        start_offset = self._source_offset((line, column))
        start_tag = self.get_starttag_text() or ""
        normalized = [{"name": name.lower(), "value": value} for name, value in attrs]
        values = {item["name"]: item["value"] for item in normalized}
        is_void = tag.lower() in VOID_HTML_TAGS
        has_explicit_end = not self_closing and not is_void
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
            "_sourceStart": start_offset,
            "_sourceOpenEnd": start_offset + len(start_tag),
            "_sourceCloseStart": None,
            "_sourceEnd": start_offset + len(start_tag) if not has_explicit_end else None,
            "_sourceMutable": bool(start_tag),
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
        if has_explicit_end:
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
        close_start = self._source_offset(self.getpos())
        close_match = re.match(r"</\s*" + re.escape(lowered) + r"\s*>",
                               self.source[close_start:], re.IGNORECASE)
        close_end = close_start + len(close_match.group(0)) if close_match else None
        match = next((index for index in range(len(self.stack) - 1, 0, -1)
                      if self.by_id[self.stack[index]]["tag"] == lowered), None)
        if match is None:
            message = f"unmatched closing tag </{lowered}> at line {self.getpos()[0]}"
            self.warnings.append(message)
            self.structural_issues.append(message)
            return
        for node_id in self.stack[match + 1:]:
            node = self.by_id[node_id]
            message = (f"<{node['tag']}> at line {node['line']} is implicitly closed by "
                       f"</{lowered}>")
            self.warnings.append(message)
            self.structural_issues.append(message)
            node["_sourceMutable"] = False
        closed_node = self.by_id[self.stack[match]]
        if close_end is None or close_end < closed_node["_sourceOpenEnd"]:
            message = f"closing tag </{lowered}> has no exact source range at line {self.getpos()[0]}"
            self.warnings.append(message)
            self.structural_issues.append(message)
            closed_node["_sourceMutable"] = False
        else:
            closed_node["_sourceCloseStart"] = close_start
            closed_node["_sourceEnd"] = close_end
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


def audit_bridge_package(source: bytes) -> dict:
    """Check static HTML data-action names against the Host v1 registry."""
    manifest, files = package_v3.unpack(source)
    try:
        registry = json.loads(BRIDGE_V1_ACTION_REGISTRY.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise skin_studio.SkinError("Bridge v1 action registry is unavailable or invalid") from exc
    _fail(type(registry) is dict and registry.get("schemaVersion") == 1 and
          registry.get("bridgeVersion") == 1 and
          type(registry.get("requiredCapability")) is str and
          registry["requiredCapability"] == "host.action" and
          type(registry.get("actions")) is list and
          all(type(name) is str and name for name in registry["actions"]) and
          len(registry["actions"]) == len(set(registry["actions"])),
          "Bridge v1 action registry has an invalid schema")
    known_actions = set(registry["actions"])
    try:
        contract_bytes = BRIDGE_V1_CONTRACT_SCHEMA.read_bytes()
        contract = json.loads(contract_bytes.decode("utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise skin_studio.SkinError("Bridge v1 JSON Schema is unavailable or invalid") from exc
    contract_defs = contract.get("$defs") if type(contract) is dict else None
    action_payloads = (contract_defs.get("actionPayloads")
                       if type(contract_defs) is dict else None)
    command_payloads = (contract_defs.get("commandPayloads")
                        if type(contract_defs) is dict else None)
    _fail(type(contract) is dict and
          contract.get("$schema") == "https://json-schema.org/draft/2020-12/schema" and
          type(action_payloads) is dict and type(command_payloads) is dict and
          set(action_payloads) == known_actions and
          registry.get("contractSchema") == "docs/contracts/web-skin-bridge-v1.schema.json",
          "Bridge v1 JSON Schema action registry does not match the action names")

    def referenced_payload_names(message_name: str, expected_prefix: str) -> set[str]:
        message = contract_defs.get(message_name)
        payload = message.get("properties", {}).get("payload", {}) \
            if type(message) is dict else {}
        branches = payload.get("oneOf") if type(payload) is dict else None
        _fail(type(branches) is list, f"Bridge v1 JSON Schema has no {message_name} payload union")
        refs = set()
        for branch in branches:
            ref = branch.get("$ref") if type(branch) is dict else None
            prefix = f"#/$defs/{expected_prefix}/"
            _fail(type(ref) is str and ref.startswith(prefix),
                  f"Bridge v1 JSON Schema has an invalid {message_name} payload reference")
            refs.add(ref[len(prefix):])
        return refs

    _fail(referenced_payload_names("actionMessage", "actionPayloads") == known_actions,
          "Bridge v1 JSON Schema action message union is incomplete")
    command_names = {name for name in command_payloads if "." in name}
    _fail(referenced_payload_names("commandMessage", "commandPayloads") == command_names and
          len(command_names) > 0,
          "Bridge v1 JSON Schema edit-command union is incomplete")
    bindings = []
    unknown_bindings = []
    inline_script_count = 0
    linked_scripts = set()
    total_components = 0
    for path, data in sorted(files.items()):
        if not path.startswith("web/") or not path.lower().endswith((".html", ".htm")):
            continue
        try:
            html = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            raise skin_studio.SkinError(f"HTML page is not UTF-8: {path}") from exc
        page = _StaticHtmlInventory(path).parse(html)
        total_components += page["componentCount"]
        _fail(total_components <= MAX_ANALYZED_COMPONENTS,
              f"HTML inventory exceeds {MAX_ANALYZED_COMPONENTS} components")
        inline_script_count += page["inlineScriptCount"]
        linked_scripts.update(page["scriptSources"])
        for node in page["components"]:
            attribute = next((item for item in node["attributes"]
                              if item["name"] == "data-action"), None)
            if attribute is None:
                continue
            action = attribute["value"]
            binding = {"path": path, "nodeId": node["nodeId"], "action": action,
                       "payloadSchemaRef": f"#/$defs/actionPayloads/{action}"}
            bindings.append(binding)
            if type(action) is not str or action not in known_actions:
                unknown_bindings.append(binding)

    required_capabilities = ([registry["requiredCapability"]] if bindings else [])
    declared_capabilities = manifest.get("capabilities", [])
    missing_capabilities = [capability for capability in required_capabilities
                            if capability not in declared_capabilities]
    requires_source_review = bool(linked_scripts or inline_script_count)
    result = {
        "schemaVersion": 1,
        "command": "audit-bridge-v3",
        "ok": not unknown_bindings and not missing_capabilities,
        "skin": {"id": manifest["id"], "name": manifest["name"],
                 "packageSha256": _file_sha256(source)},
        "registry": {"bridgeVersion": registry["bridgeVersion"],
                     "requiredCapability": registry["requiredCapability"],
                     "actionCount": len(known_actions)},
        "contractSchema": {"path": registry["contractSchema"],
                           "sha256": _file_sha256(contract_bytes)},
        "declaredCapabilities": declared_capabilities,
        "requiredCapabilities": required_capabilities,
        "missingCapabilities": missing_capabilities,
        "bindings": bindings,
        "unknownBindings": unknown_bindings,
        "scope": "literal data-action attributes in static web/*.html and web/*.htm",
        "requiresSourceReview": requires_source_review,
        "limitations": [
            "Does not execute JavaScript or enumerate controls created at runtime.",
            "Does not validate JavaScript-generated Bridge payloads or their field schemas.",
            "Review linked JavaScript source and V3_HOST_BRIDGE_EXTENSION.md before export."
        ],
    }
    return result


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

"""Machine-operable, validated edits for self-contained PowerCal v3 skins."""
from __future__ import annotations

import base64
import binascii
import copy
from bisect import bisect_right
from collections import Counter, deque
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import posixpath
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
MAX_COMPONENT_STYLE_PROPERTIES = 128
MAX_COMPONENT_STYLE_VALUE_BYTES = 4_096
MAX_BRIDGE_SCRIPT_FINDINGS = 4_096
COMPONENT_STYLE_ATTRIBUTE = "data-pcskin-studio-style"
COMPONENT_STYLE_TOKEN = re.compile(r"pcss-[0-9a-f]{24}\Z")
CSS_PROPERTY = re.compile(r"(?:--[A-Za-z_][A-Za-z0-9_-]*|-?[A-Za-z][A-Za-z0-9-]*)\Z")
BRIDGE_SCRIPT_EXTENSIONS = frozenset({".js", ".mjs", ".cjs"})
BRIDGE_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_.-]{0,63}\Z")
JS_SCRIPT_MIME_TYPE = re.compile(
    r"(?:text|application)/(?:x-)?(?:java|ecma)script(?:[0-9]+(?:\.[0-9]+)?)?\Z")
JS_CONTROL_PAREN_KEYWORDS = frozenset({"catch", "for", "if", "switch", "while", "with"})
JS_REGEX_PREFIX_KEYWORDS = frozenset({
    "await", "case", "delete", "do", "else", "in", "instanceof", "new",
    "of", "return", "throw", "typeof", "void", "yield",
})
JS_REGEX_PREFIX_PUNCTUATION = frozenset({
    "(", "[", "{", ",", ":", ";", "=", "!", "?", "&&", "||", "??",
    "+", "-", "*", "%", "^", "~", "<", ">", "=>", "==", "===", "!=",
    "!==", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "**", "&", "|",
})
JS_MULTI_OPERATORS = tuple(sorted({
    "===", "!==", "**=", "=>", "?.", "&&", "||", "??", "==", "!=", "<=",
    ">=", "+=", "-=", "*=", "/=", "%=", "**", "++", "--", "<<", ">>",
}, key=len, reverse=True))
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


def _normalize_css_property(value: object) -> str:
    _fail(type(value) is str and len(value) <= 256 and CSS_PROPERTY.fullmatch(value) is not None,
          "CSS property must be a valid property name")
    return value if value.startswith("--") else value.lower()


def _component_style_markers(token: str) -> tuple[str, str]:
    return (f"/* pcskin-studio-style-begin:{token} */",
            f"/* pcskin-studio-style-end:{token} */")


def _read_component_style_block(css: str, token: str) -> tuple[re.Match | None, dict[str, str]]:
    begin, end = _component_style_markers(token)
    _fail(css.count(begin) <= 1 and css.count(end) <= 1,
          "component style markers are duplicated; edit the CSS source directly")
    if begin not in css and end not in css:
        return None, {}
    _fail(css.count(begin) == 1 and css.count(end) == 1,
          "component style block is incomplete; edit the CSS source directly")
    pattern = re.compile(re.escape(begin) + r"(.*?)" + re.escape(end), re.DOTALL)
    matches = list(pattern.finditer(css))
    _fail(len(matches) == 1, "component style block is malformed; edit the CSS source directly")
    match = matches[0]
    rule = re.fullmatch(
        r"\s*\[data-pcskin-studio-style=\"" + re.escape(token) +
        r"\"\]\s*\{\s*(.*?)\s*\}\s*", match.group(1), re.DOTALL)
    _fail(rule is not None,
          "component style block selector is malformed; edit the CSS source directly")
    properties: dict[str, str] = {}
    declaration = re.compile(r"\s*((?:--[A-Za-z_][A-Za-z0-9_-]*|-?[A-Za-z][A-Za-z0-9-]*))\s*:\s*(.*?)\s*;\s*\Z")
    for line in rule.group(1).splitlines():
        if not line.strip():
            continue
        parsed = declaration.fullmatch(line)
        _fail(parsed is not None,
              "component style block contains unsupported syntax; edit the CSS source directly")
        name = _normalize_css_property(parsed.group(1))
        _fail(name not in properties, "component style block contains duplicate properties")
        properties[name] = parsed.group(2)
    return match, properties


def _render_component_style_block(token: str, properties: dict[str, str]) -> str:
    begin, end = _component_style_markers(token)
    selector = f'[data-pcskin-studio-style="{token}"]'
    declarations = "\n".join(f"  {name}: {value};" for name, value in sorted(properties.items()))
    return f"{begin}\n{selector} {{\n{declarations}\n}}\n{end}"


def _write_component_style_attribute(files: dict[str, bytes], path: str, source: str,
                                     parser: _StaticHtmlInventory, node: dict,
                                     token: str | None) -> None:
    start, end = node["_sourceStart"], node["_sourceOpenEnd"]
    opening = source[start:end]
    _fail(bool(opening) and opening.startswith("<") and opening.endswith(">"),
          "selected component has no safe opening tag")
    if token is None:
        pattern = re.compile(
            r"\s+" + re.escape(COMPONENT_STYLE_ATTRIBUTE) + r"\s*=\s*([\"'])" +
            re.escape(str(next(attribute["value"] for attribute in node["attributes"]
                               if attribute["name"].lower() == COMPONENT_STYLE_ATTRIBUTE))) +
            r"\1", re.IGNORECASE)
        rendered, count = pattern.subn("", opening, count=1)
        _fail(count == 1, "Studio style marker source could not be removed safely")
    else:
        insert_at = len(opening) - (2 if re.search(r"/\s*>$", opening) else 1)
        rendered = opening[:insert_at] + f' {COMPONENT_STYLE_ATTRIBUTE}="{token}"' + opening[insert_at:]
    updated = source[:start] + rendered + source[end:]
    files[path] = _validate_component_result(path, parser, updated)


def _apply_component_style(files: dict[str, bytes], operation: dict) -> dict[str, bytes]:
    fields = {"op", "path", "expectedSha256", "cssPath", "expectedCssSha256",
              "targetNodeId", "setProperties", "removeProperties"}
    _fail(set(operation) == fields, "component-style has unsupported or missing fields")
    html_path, html_source, parser = _component_page(
        files, operation["path"], operation["expectedSha256"])
    node = _component_node(parser, operation["targetNodeId"])
    css_path = _validate_path(operation["cssPath"])
    _fail(css_path.startswith("web/") and css_path.lower().endswith(".css"),
          "component styles require a package CSS source under web/")
    css_bytes = _expect_existing(files, css_path, operation["expectedCssSha256"])
    try:
        css = css_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise skin_studio.SkinError("component stylesheet is not valid UTF-8") from exc

    raw_set = operation["setProperties"]
    raw_remove = operation["removeProperties"]
    _fail(type(raw_set) is dict and len(raw_set) <= MAX_COMPONENT_STYLE_PROPERTIES,
          f"setProperties must be an object with at most {MAX_COMPONENT_STYLE_PROPERTIES} items")
    _fail(type(raw_remove) is list and len(raw_remove) <= MAX_COMPONENT_STYLE_PROPERTIES,
          f"removeProperties must be an array with at most {MAX_COMPONENT_STYLE_PROPERTIES} items")
    set_properties: dict[str, str] = {}
    for raw_name, raw_value in raw_set.items():
        name = _normalize_css_property(raw_name)
        _fail(name not in set_properties, "setProperties contains duplicate CSS properties")
        _fail(type(raw_value) is str, f"CSS value for {name!r} must be a string")
        try:
            value_bytes = raw_value.encode("utf-8")
        except UnicodeEncodeError as exc:
            raise skin_studio.SkinError(f"CSS value for {name!r} is not valid Unicode") from exc
        value = raw_value.strip()
        _fail(bool(value) and len(value_bytes) <= MAX_COMPONENT_STYLE_VALUE_BYTES and
              not any(character in value for character in "\x00\r\n;{}") and
              "/*" not in value and "*/" not in value,
              f"CSS value for {name!r} is empty, too large, or contains unsupported delimiters")
        set_properties[name] = value

    remove_properties: set[str] = set()
    for raw_name in raw_remove:
        name = _normalize_css_property(raw_name)
        _fail(name not in remove_properties, "removeProperties contains duplicate CSS properties")
        remove_properties.add(name)
    _fail(not (set(set_properties) & remove_properties),
          "a CSS property cannot be both set and removed")

    marker_values = [attribute["value"] for attribute in node["attributes"]
                     if attribute["name"].lower() == COMPONENT_STYLE_ATTRIBUTE]
    _fail(len(marker_values) <= 1, "selected HTML node has duplicate Studio style markers")
    token = marker_values[0] if marker_values else None
    if token is not None:
        _fail(type(token) is str and COMPONENT_STYLE_TOKEN.fullmatch(token) is not None,
              "selected HTML node has an invalid Studio style marker; edit the HTML source directly")
        _fail(sum(1 for candidate in parser.nodes
                  for attribute in candidate["attributes"]
                  if attribute["name"].lower() == COMPONENT_STYLE_ATTRIBUTE and
                  attribute["value"] == token) == 1,
              "Studio style marker is shared by multiple HTML nodes")

    style_match, old_properties = _read_component_style_block(css, token) if token else (None, {})
    for name in list(set_properties):
        current = old_properties.get(name)
        if current == set_properties[name]:
            del set_properties[name]
    remove_properties = {name for name in remove_properties if name in old_properties}
    if not set_properties and not remove_properties:
        return files

    next_properties = dict(old_properties)
    for name in remove_properties:
        next_properties.pop(name, None)
    next_properties.update(set_properties)

    if next_properties and token is None:
        token = "pcss-" + hashlib.sha256(
            (html_path + "\0" + node["nodeId"] + "\0" +
             _file_sha256(html_source.encode("utf-8"))).encode("utf-8")
        ).hexdigest()[:24]
        _fail(not any(attribute["name"].lower() == COMPONENT_STYLE_ATTRIBUTE and
                      attribute["value"] == token
                      for candidate in parser.nodes for attribute in candidate["attributes"]),
              "generated Studio style marker collides with an existing node")

    if token is not None:
        if style_match:
            replacement = _render_component_style_block(token, next_properties) if next_properties else ""
            updated_css = css[:style_match.start()] + replacement + css[style_match.end():]
        elif next_properties:
            separator = "" if not css or css.endswith("\n") else "\n"
            updated_css = css + separator + _render_component_style_block(token, next_properties) + "\n"
        else:
            updated_css = css
        if updated_css != css:
            encoded_css = updated_css.encode("utf-8")
            _fail(len(encoded_css) <= package_v3.TEXT_LIMIT,
                  "component stylesheet exceeds the package text-file limit")
            files[css_path] = encoded_css

        if next_properties and not marker_values:
            _write_component_style_attribute(files, html_path, html_source, parser, node, token)
        elif not next_properties and marker_values:
            _write_component_style_attribute(files, html_path, html_source, parser, node, None)
    return files


def _apply_component_operation(files: dict[str, bytes], operation: dict) -> dict[str, bytes]:
    op = operation.get("op")
    if op == "component-style":
        return _apply_component_style(files, operation)
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

    if op in {"component-insert", "component-remove", "component-attributes", "component-style",
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
        self.inline_scripts: list[dict] = []
        self.inline_handlers: list[dict] = []
        self._active_inline_script: dict | None = None
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

    def _inline_handler_source(self, start_tag: str, start_offset: int,
                               attribute_name: str, source: str,
                               node_id: str) -> dict:
        pattern = re.compile(
            r"(?is)(?:^|\s)" + re.escape(attribute_name) +
            r"\s*=\s*(?:\"(?P<double>[^\"]*)\"|'(?P<single>[^']*)'|(?P<unquoted>[^\s>]+))")
        match = pattern.search(start_tag)
        group = (next((name for name in ("double", "single", "unquoted")
                       if match is not None and match.group(name) is not None), None))
        value_start = (start_offset + match.start(group)
                       if match is not None and group is not None
                       else start_offset + len(start_tag))
        line_index = bisect_right(self._line_offsets, value_start) - 1
        return {
            "path": self.path,
            "sourceKind": "html-event-handler",
            "source": source,
            "lineOffset": line_index,
            "firstLineColumnOffset": value_start - self._line_offsets[line_index],
            "nodeId": node_id,
            "attribute": attribute_name,
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
        for attribute in normalized:
            name = attribute["name"]
            value = attribute["value"]
            if name.startswith("on") and len(name) > 2 and type(value) is str and value.strip():
                self.inline_handlers.append(self._inline_handler_source(
                    start_tag, start_offset, name, value, node_id))
        if tag.lower() == "script":
            if "src" in values:
                self.script_sources.append(values.get("src") or "")
            else:
                self.inline_script_count += 1
                if _is_executable_javascript_type(values.get("type")):
                    self._active_inline_script = {
                        "nodeId": node_id,
                        "sourceStartOffset": start_offset + len(start_tag),
                        "chunks": [],
                    }
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
        if tag.lower() == "script":
            # HTML ignores the self-closing slash on non-void script elements.
            self._add_element(tag, attrs, self_closing=False)
            self.set_cdata_mode(tag)
        else:
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
        if (lowered == "script" and self._active_inline_script is not None and
                self._active_inline_script["nodeId"] == closed_node["nodeId"]):
            active = self._active_inline_script
            inline_source = "".join(active["chunks"])
            if inline_source.strip():
                source_start = active["sourceStartOffset"]
                line_index = bisect_right(self._line_offsets, source_start) - 1
                self.inline_scripts.append({
                    "path": self.path,
                    "sourceKind": "inline",
                    "source": inline_source,
                    "lineOffset": line_index,
                    "firstLineColumnOffset": source_start - self._line_offsets[line_index],
                    "scriptIndex": len(self.inline_scripts) + 1,
                })
            self._active_inline_script = None
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
        if (self._active_inline_script is not None and
                self._active_inline_script["nodeId"] == current["nodeId"]):
            self._active_inline_script["chunks"].append(data)
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


def _js_identifier_start(char: str) -> bool:
    return char in {"$", "_"} or char.isalpha()


def _js_identifier_part(char: str) -> bool:
    return _js_identifier_start(char) or char.isdigit()


def _js_regex_end(source: str, start: int) -> int | None:
    """Return the end of a regex literal, or None when the slash is not one."""
    index = start + 1
    escaped = False
    in_character_class = False
    while index < len(source) and source[index] not in "\r\n":
        char = source[index]
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "[":
            in_character_class = True
        elif char == "]":
            in_character_class = False
        elif char == "/" and not in_character_class:
            index += 1
            while index < len(source) and _js_identifier_part(source[index]):
                index += 1
            return index
        index += 1
    return None


def _is_executable_javascript_type(value: str | None) -> bool:
    script_type = (value or "").split(";", 1)[0].strip().lower()
    return (script_type in {"", "module"} or
            JS_SCRIPT_MIME_TYPE.fullmatch(script_type) is not None)


def _template_may_contain_bridge_call(value: str) -> bool:
    search_from = 0
    while True:
        start = value.find("${", search_from)
        if start < 0:
            return False
        backslashes = 0
        cursor = start - 1
        while cursor >= 0 and value[cursor] == "\\":
            backslashes += 1
            cursor -= 1
        if backslashes % 2:
            search_from = start + 2
            continue

        expression_start = start + 2
        depth = 1
        expression_end = len(value)
        for kind, token, offset in _iter_js_tokens(value[expression_start:]):
            if kind != "punctuation":
                continue
            if token == "{":
                depth += 1
            elif token == "}":
                depth -= 1
                if depth == 0:
                    expression_end = expression_start + offset
                    break
        expression_tokens = iter(_iter_js_tokens(value[expression_start:expression_end]))
        for kind, token, _ in expression_tokens:
            if kind != "identifier" or token not in {
                    "send", "perform", "sendCommand", "postMessage"}:
                continue
            following = next(expression_tokens, None)
            if following is None:
                continue
            if following[1] == "(":
                return True
            if following[1] == "?.":
                call_open = next(expression_tokens, None)
                if call_open is not None and call_open[1] == "(":
                    return True
        if expression_end >= len(value):
            return False
        search_from = expression_end + 1


def _iter_js_tokens(source: str):
    """Yield a bounded-lookahead lexical view; this is not a JavaScript parser."""
    index = 0
    previous = None
    control_parentheses = []
    control_blocks = []
    control_block_expected = False
    regex_after_control_close = False
    while index < len(source):
        char = source[index]
        if char.isspace():
            index += 1
            continue
        if source.startswith("//", index):
            newline = source.find("\n", index + 2)
            index = len(source) if newline < 0 else newline + 1
            continue
        if source.startswith("/*", index):
            end = source.find("*/", index + 2)
            index = len(source) if end < 0 else end + 2
            continue

        start = index
        if char in {"'", '"', "`"}:
            quote = char
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            kind = "template" if quote == "`" else "string"
            value = source[start:index]
        elif _js_identifier_start(char):
            index += 1
            while index < len(source) and _js_identifier_part(source[index]):
                index += 1
            kind, value = "identifier", source[start:index]
        elif char == "/":
            can_start_regex = (
                regex_after_control_close or previous is None or
                (previous[0] == "identifier" and
                 previous[1] in JS_REGEX_PREFIX_KEYWORDS) or
                (previous[0] == "punctuation" and
                 previous[1] in JS_REGEX_PREFIX_PUNCTUATION)
            )
            regex_end = _js_regex_end(source, index) if can_start_regex else None
            if regex_end is not None:
                index = regex_end
                kind, value = "regex", source[start:index]
            else:
                operator = next((item for item in JS_MULTI_OPERATORS
                                 if source.startswith(item, index)), None)
                value = operator or char
                index += len(value)
                kind = "punctuation"
        else:
            operator = next((item for item in JS_MULTI_OPERATORS
                             if source.startswith(item, index)), None)
            value = operator or char
            index += len(value)
            kind = "punctuation"

        control_close = False
        control_block_close = False
        if kind == "punctuation" and value == "(":
            control_parentheses.append(
                previous is not None and previous[0] == "identifier" and
                previous[1] in JS_CONTROL_PAREN_KEYWORDS)
        elif kind == "punctuation" and value == ")" and control_parentheses:
            control_close = control_parentheses.pop()
        elif kind == "punctuation" and value == "{":
            control_blocks.append(control_block_expected)
            control_block_expected = False
        elif kind == "punctuation" and value == "}" and control_blocks:
            control_block_close = control_blocks.pop()

        token = (kind, value, start)
        previous = token[:2]
        if control_close:
            control_block_expected = True
        elif kind == "punctuation" and value not in {"{", "}"}:
            control_block_expected = False
        elif kind != "punctuation":
            control_block_expected = False
        regex_after_control_close = control_close or control_block_close
        yield token


def _literal_js_bridge_name(token) -> str | None:
    if token is None or token[0] != "string":
        return None
    raw = token[1]
    if raw.startswith('"'):
        try:
            value = json.loads(raw)
        except json.JSONDecodeError:
            return None
    elif raw.startswith("'") and len(raw) >= 2 and "\\" not in raw:
        value = raw[1:-1]
    else:
        return None
    return value if type(value) is str and BRIDGE_NAME.fullmatch(value) else None


def _location(source: str, line_starts: list[int], offset: int,
              line_offset: int, first_line_column_offset: int) -> tuple[int, int]:
    line_index = bisect_right(line_starts, offset) - 1
    local_line = line_index + 1
    column = offset - line_starts[line_index] + 1
    if local_line == 1:
        column += first_line_column_offset
    return line_offset + local_line, column


def _scan_bridge_script(source: str, path: str, source_kind: str,
                        action_names: set[str], command_names: set[str],
                        max_findings: int, line_offset: int = 0,
                        first_line_column_offset: int = 0) -> dict:
    line_starts = [0]
    line_starts.extend(index + 1 for index, char in enumerate(source) if char == "\n")
    tokens = iter(_iter_js_tokens(source))
    window = deque()
    bindings, transports, expressions = [], [], []
    truncated = False
    recent_tokens = deque(maxlen=2)

    def fill(count: int = 1) -> bool:
        while len(window) < count:
            try:
                window.append(next(tokens))
            except StopIteration:
                break
        return bool(window)

    def follows_method_body(open_index: int) -> bool:
        depth = 0
        cursor = open_index
        while True:
            if cursor >= len(window) and not fill(cursor + 1):
                return False
            value_at_cursor = window[cursor][1]
            if value_at_cursor == "(":
                depth += 1
            elif value_at_cursor == ")":
                depth -= 1
                if depth == 0:
                    fill(cursor + 2)
                    if len(window) <= cursor + 1:
                        return False
                    return window[cursor + 1][1] == "{"
            cursor += 1

    previous = None
    while fill():
        current = window[0]
        kind, value, offset = current
        lookahead = list(window)
        if kind == "identifier" and value in {"postMessage", "sendCommand", "eval", "Function"}:
            fill(3 if value in {"postMessage", "sendCommand"} else 2)
            lookahead = list(window)
        if kind == "template" and _template_may_contain_bridge_call(value):
            line, column = _location(
                source, line_starts, offset, line_offset, first_line_column_offset)
            expressions.append({
                "path": path, "sourceKind": source_kind, "line": line,
                "column": column, "status": "REVIEW_REQUIRED_TEMPLATE_EXPRESSION",
                "reason": "template interpolation may contain an unparsed Bridge call",
            })
        elif (kind == "identifier" and value in {"send", "perform"} and
              previous != "function" and
              (not recent_tokens or recent_tokens[-1][1] not in {".", "?."})):
            fill(5)
            lookahead = list(window)
            open_index = 1 if len(lookahead) > 1 and lookahead[1][1] == "(" else None
            if (open_index is None and len(lookahead) > 2 and
                    lookahead[1][1] == "?." and lookahead[2][1] == "("):
                open_index = 2
            if (open_index is not None and
                    not (open_index == 1 and follows_method_body(open_index))):
                argument_index = open_index + 1
                argument = (lookahead[argument_index]
                            if argument_index < len(lookahead) else None)
                separator = (lookahead[argument_index + 1][1]
                             if argument_index + 1 < len(lookahead) else None)
                name = (_literal_js_bridge_name(argument)
                        if separator in {",", ")"} else None)
                api = "action" if value == "send" else "command"
                known_names = action_names if api == "action" else command_names
                is_dynamic = name is None
                is_known = not is_dynamic and name in known_names
                if is_dynamic:
                    status = "REVIEW_REQUIRED_DYNAMIC_NAME"
                    payload_ref = None
                    payload_validation = "NOT_POSSIBLE_STATICALLY"
                    reason = "first argument is not a simple Bridge name literal"
                elif is_known:
                    status = "KNOWN_NAME_PAYLOAD_UNVERIFIED"
                    payload_ref = f"#/$defs/{'actionPayloads' if api == 'action' else 'commandPayloads'}/{name}"
                    payload_validation = "NOT_VALIDATED"
                    reason = "payload fields and expressions require schema/runtime review"
                else:
                    status = "UNKNOWN_LITERAL_NAME"
                    payload_ref = None
                    payload_validation = "NOT_RUN"
                    reason = "literal name is absent from the Bridge v1 registry/schema"
                line, column = _location(
                    source, line_starts, offset, line_offset, first_line_column_offset)
                bindings.append({
                    "path": path, "sourceKind": source_kind, "line": line,
                    "column": column, "api": api, "call": value, "name": name,
                    "status": status, "payloadSchemaRef": payload_ref,
                    "payloadValidation": payload_validation, "reason": reason,
                })
        elif (kind == "identifier" and value in {"postMessage", "sendCommand"} and
              len(recent_tokens) == 2 and
              ((value == "postMessage" and
                recent_tokens[0][1] in {"port", "bridgePort"} and
                recent_tokens[1][1] in {".", "?."}) or
               (value == "sendCommand" and
                recent_tokens[0][1] in {"PowerCalSkin", "powerCalSkin"} and
                recent_tokens[1][1] in {".", "?."})) and
              ((len(lookahead) > 1 and lookahead[1][1] == "(") or
               (len(lookahead) > 2 and lookahead[1][1] == "?." and
                lookahead[2][1] == "("))):
            line, column = _location(
                source, line_starts, offset, line_offset, first_line_column_offset)
            transports.append({
                "path": path, "sourceKind": source_kind, "line": line,
                "column": column, "call": value,
                "status": "REVIEW_REQUIRED_RAW_TRANSPORT",
                "reason": "low-level Bridge transport cannot be mapped safely by this lexical scan",
            })
        elif (kind == "identifier" and value in {"eval", "Function"} and
              (not recent_tokens or recent_tokens[-1][1] not in {".", "?."}) and
              len(lookahead) > 1 and lookahead[1][1] == "("):
            line, column = _location(
                source, line_starts, offset, line_offset, first_line_column_offset)
            expressions.append({
                "path": path, "sourceKind": source_kind, "line": line,
                "column": column, "status": "REVIEW_REQUIRED_DYNAMIC_CODE",
                "reason": "dynamic JavaScript evaluation may construct Bridge calls",
            })

        if len(bindings) + len(transports) + len(expressions) >= max_findings:
            truncated = True
            break
        previous = value
        recent_tokens.append(current)
        window.popleft()

    return {"bindings": bindings, "transports": transports,
            "expressions": expressions, "truncated": truncated}


def audit_bridge_package(source: bytes) -> dict:
    """Index static HTML and conservative JavaScript Bridge call sites."""
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
    linked_script_refs = []
    html_sources = []
    inline_event_handlers = []
    total_components = 0
    for path, data in sorted(files.items()):
        if not path.startswith("web/") or not path.lower().endswith((".html", ".htm")):
            continue
        try:
            html = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            raise skin_studio.SkinError(f"HTML page is not UTF-8: {path}") from exc
        html_inventory = _StaticHtmlInventory(path)
        page = html_inventory.parse(html)
        total_components += page["componentCount"]
        _fail(total_components <= MAX_ANALYZED_COMPONENTS,
              f"HTML inventory exceeds {MAX_ANALYZED_COMPONENTS} components")
        inline_script_count += page["inlineScriptCount"]
        for handler in html_inventory.inline_handlers:
            inline_event_handlers.append({
                "path": path,
                "nodeId": handler["nodeId"],
                "attribute": handler["attribute"],
                "line": handler["lineOffset"] + 1,
                "sha256": _file_sha256(handler["source"].encode("utf-8")),
            })
        linked_scripts.update(page["scriptSources"])
        linked_script_refs.extend((path, script) for script in page["scriptSources"])
        html_sources.append((path, html_inventory.inline_scripts,
                             html_inventory.inline_handlers))
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

    script_bindings = []
    script_transports = []
    review_required_expressions = []
    review_required_sources = []
    script_files_scanned = []
    script_findings_truncated = False
    source_review_required = bool(linked_scripts or inline_script_count or
                                  inline_event_handlers)

    def add_scan_result(scan: dict) -> None:
        script_bindings.extend(scan["bindings"])
        script_transports.extend(scan["transports"])
        review_required_expressions.extend(scan["expressions"])

    for path, data in sorted(files.items()):
        suffix = Path(path).suffix.lower()
        if not path.startswith("web/") or suffix not in BRIDGE_SCRIPT_EXTENSIONS:
            continue
        source_review_required = True
        script_files_scanned.append({"path": path, "bytes": len(data),
                                     "sha256": _file_sha256(data)})
        try:
            text = data.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            review_required_sources.append({
                "path": path, "status": "REVIEW_REQUIRED_NON_UTF8_SCRIPT",
                "reason": "JavaScript source is not UTF-8 and could not be scanned",
            })
            continue
        remaining = MAX_BRIDGE_SCRIPT_FINDINGS - (
            len(script_bindings) + len(script_transports) +
            len(review_required_expressions))
        if remaining <= 0:
            script_findings_truncated = True
            break
        scan = _scan_bridge_script(text, path, "file", known_actions,
                                   command_names, remaining)
        add_scan_result(scan)
        if scan["truncated"]:
            script_findings_truncated = True
            break

    if not script_findings_truncated:
        for path, inline_scripts, inline_handlers in html_sources:
            for inline in (*inline_scripts, *inline_handlers):
                remaining = MAX_BRIDGE_SCRIPT_FINDINGS - (
                    len(script_bindings) + len(script_transports) +
                    len(review_required_expressions))
                if remaining <= 0:
                    script_findings_truncated = True
                    break
                scan = _scan_bridge_script(
                    inline["source"], path, inline["sourceKind"], known_actions,
                    command_names, remaining, inline["lineOffset"],
                    inline["firstLineColumnOffset"])
                for item in scan["bindings"]:
                    if "scriptIndex" in inline:
                        item["scriptIndex"] = inline["scriptIndex"]
                    else:
                        item["nodeId"] = inline["nodeId"]
                        item["attribute"] = inline["attribute"]
                for item in scan["transports"]:
                    if "scriptIndex" in inline:
                        item["scriptIndex"] = inline["scriptIndex"]
                    else:
                        item["nodeId"] = inline["nodeId"]
                        item["attribute"] = inline["attribute"]
                for item in scan["expressions"]:
                    if "scriptIndex" in inline:
                        item["scriptIndex"] = inline["scriptIndex"]
                    else:
                        item["nodeId"] = inline["nodeId"]
                        item["attribute"] = inline["attribute"]
                add_scan_result(scan)
                if scan["truncated"]:
                    script_findings_truncated = True
                    break
            if script_findings_truncated:
                break

    resolved_script_sources = []
    for html_path, script_source in sorted(set(linked_script_refs)):
        if re.match(r"^(?:[a-z]+:)?//", script_source, re.IGNORECASE) or \
                script_source.lower().startswith("data:"):
            resolved_script_sources.append({
                "path": html_path, "source": script_source,
                "resolvedPath": None, "status": "REVIEW_REQUIRED_EXTERNAL_SCRIPT",
            })
            review_required_sources.append({
                "path": html_path, "source": script_source,
                "status": "REVIEW_REQUIRED_EXTERNAL_SCRIPT",
                "reason": "external script source is not included in the package scan",
            })
            source_review_required = True
            continue
        resolved = posixpath.normpath(
            posixpath.join(posixpath.dirname(html_path), script_source))
        if resolved not in files:
            resolved_script_sources.append({
                "path": html_path, "source": script_source,
                "resolvedPath": resolved, "status": "REVIEW_REQUIRED_MISSING_SCRIPT",
            })
            review_required_sources.append({
                "path": html_path, "source": script_source,
                "status": "REVIEW_REQUIRED_MISSING_SCRIPT",
                "reason": "linked script source is not present in the package",
            })
            source_review_required = True
        elif Path(resolved).suffix.lower() not in BRIDGE_SCRIPT_EXTENSIONS:
            resolved_script_sources.append({
                "path": html_path, "source": script_source,
                "resolvedPath": resolved, "status": "REVIEW_REQUIRED_UNSUPPORTED_SCRIPT_TYPE",
            })
            review_required_sources.append({
                "path": html_path, "source": script_source,
                "status": "REVIEW_REQUIRED_UNSUPPORTED_SCRIPT_TYPE",
                "reason": "linked executable source is outside the scanner's JS extensions",
            })
            source_review_required = True
        else:
            resolved_script_sources.append({
                "path": html_path, "source": script_source,
                "resolvedPath": resolved, "status": "SCANNED",
            })

    if script_findings_truncated:
        review_required_expressions.append({
            "path": None, "sourceKind": "package", "line": None, "column": None,
            "status": "REVIEW_REQUIRED_ANALYSIS_LIMIT",
            "reason": f"JavaScript Bridge findings exceeded {MAX_BRIDGE_SCRIPT_FINDINGS}",
        })
        review_required_sources.append({
            "status": "REVIEW_REQUIRED_ANALYSIS_LIMIT",
            "reason": "the bounded JavaScript Bridge scan stopped at its findings limit",
        })

    call_order = lambda item: (item.get("path") or "", item.get("line") or 0,
                               item.get("column") or 0, item.get("api") or "",
                               item.get("name") or "")
    script_bindings.sort(key=call_order)
    script_transports.sort(key=call_order)
    review_required_expressions.sort(key=call_order)
    resolved_script_sources.sort(key=lambda item: (
        item.get("path", ""), item.get("resolvedPath") or item.get("source", "")))
    review_required_sources.sort(key=lambda item: (
        item.get("path", ""), item.get("source", ""), item.get("status", "")))
    unknown_script_bindings = [item for item in script_bindings
                               if item["status"] == "UNKNOWN_LITERAL_NAME"]
    has_script_bridge_calls = bool(
        script_bindings or script_transports or review_required_expressions)
    required_capabilities = ([registry["requiredCapability"]]
                             if bindings or has_script_bridge_calls else [])
    declared_capabilities = manifest.get("capabilities", [])
    missing_capabilities = [capability for capability in required_capabilities
                            if capability not in declared_capabilities]
    requires_source_review = source_review_required or bool(review_required_sources)
    result = {
        "schemaVersion": 1,
        "command": "audit-bridge-v3",
        "ok": not unknown_bindings and not unknown_script_bindings and
              not missing_capabilities,
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
        "scriptFilesScanned": script_files_scanned,
        "inlineEventHandlers": inline_event_handlers,
        "linkedScriptSources": resolved_script_sources,
        "scriptBindings": script_bindings,
        "unknownScriptBindings": unknown_script_bindings,
        "scriptTransportCalls": script_transports,
        "reviewRequiredExpressions": review_required_expressions,
        "reviewRequiredSources": review_required_sources,
        "scriptFindingLimit": MAX_BRIDGE_SCRIPT_FINDINGS,
        "scope": "literal data-action attributes in static web/*.html and web/*.htm",
        "scriptScope": "lexical direct bare send()/perform() names and recognized port.postMessage/PowerCalSkin.sendCommand calls in packaged web JavaScript, executable inline scripts, and HTML event-handler attributes",
        "scriptCallMapping": {"send": "action", "perform": "command"},
        "payloadValidation": "names are checked; JavaScript payload fields and values are not validated",
        "requiresSourceReview": requires_source_review,
        "limitations": [
            "The JavaScript scan is lexical, not a parser or runtime; aliases, computed calls, imports, obfuscation and unsupported dynamic forms need source review.",
            "A literal action/command name is checked against the v1 registry/schema, but its payload fields and values are not validated.",
            "Raw postMessage/sendCommand transports and dynamic names are reported for manual review; scripts are never executed by this audit.",
            "Controls created at runtime and Android WebView behavior are not verified."
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

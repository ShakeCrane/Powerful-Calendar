import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

STUDIO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STUDIO))

from build_default_clear import ASSET
from project_ops_v3 import (analyze_package, audit_bridge_package, edit_package,
                            inspect_package, read_file, write_atomic)
from runtime_package_v3 import build_files, unpack
from skin_studio import SkinError


def html_node_id(package, html_id):
    page = analyze_package(package)["pages"][0]
    return next(node["nodeId"] for node in page["components"]
                if node["id"] == html_id)


def isolated_temp_dir():
    evidence_root = STUDIO.parents[1] / ".local" / "android-test"
    evidence_root.mkdir(parents=True, exist_ok=True)
    return tempfile.TemporaryDirectory(prefix="studio-project-ops-", dir=evidence_root)


class ProjectOpsV3Test(unittest.TestCase):
    def setUp(self):
        self.manifest, self.files = unpack(ASSET.read_bytes())
        self.manifest["id"] = "ai-project-ops-test"
        self.manifest["name"] = "AI Project Ops Test"
        self.files["web/move-me.js"] = b"export const value = 1;\n"
        self.files["web/delete-me.txt"] = b"remove this file"
        self.files["assets/raw.bin"] = b"\x00\xffold-binary"
        self.files["assets/utf8-looking.bin"] = b"valid utf8 bytes, still a binary asset"
        self.source = build_files(self.manifest, self.files)

    def test_inspect_returns_manifest_project_and_hashed_utf8_binary_index(self):
        result = inspect_package(self.source)
        self.assertEqual(result["schemaVersion"], 1)
        self.assertEqual(result["command"], "inspect-v3")
        self.assertEqual(result["manifest"]["id"], "ai-project-ops-test")
        self.assertEqual(result["project"]["projectVersion"], 3)
        self.assertIn("slots", result["project"])
        self.assertIn("layers", result["project"])
        entries = {entry["path"]: entry for entry in result["files"]}
        self.assertEqual(entries["web/move-me.js"]["encoding"], "utf-8")
        self.assertEqual(entries["assets/raw.bin"]["encoding"], "binary")
        self.assertEqual(entries["assets/utf8-looking.bin"]["encoding"], "binary")
        self.assertEqual(entries["assets/raw.bin"]["sha256"],
                         hashlib.sha256(self.files["assets/raw.bin"]).hexdigest())
        text_file = read_file(self.source, "web/move-me.js")
        self.assertEqual(text_file["encoding"], "utf-8")
        self.assertEqual(text_file["content"], "export const value = 1;\n")
        binary_file = read_file(self.source, "assets/raw.bin")
        self.assertEqual(binary_file["encoding"], "base64")
        self.assertEqual(base64.b64decode(binary_file["content"]), b"\x00\xffold-binary")
        utf8_binary_file = read_file(self.source, "assets/utf8-looking.bin")
        self.assertEqual(utf8_binary_file["encoding"], "base64")
        self.assertEqual(base64.b64decode(utf8_binary_file["content"]),
                         self.files["assets/utf8-looking.bin"])

    def test_analyze_indexes_static_html_pages_components_and_explicit_limits(self):
        self.files["web/index.html"] = (
            '<!doctype html>\n<html lang="zh-CN"><head><title>日程 &amp; Memo</title>'
            '<link rel="stylesheet" href="style.css"></head><body>'
            '<main id="app"><pc-card data-component="event-card" class="wide">'
            '<button id="open" data-action="openEvent">打开</button>'
            '<img src="assets/icon.png" alt="图标"><span>早上</span>'
            '</pc-card><script src="app.js"></script>'
            '<script>document.createElement("pc-runtime-card")</script>'
            '</main></body></html>\n'
        ).encode("utf-8")
        self.files["web/month.html"] = (
            '<!doctype html><html><head><title>月份</title></head><body>'
            '<section id="month"><p id="same">列表</p><span id="same">其他</span>'
            '</section></body></html></ghost>'
        ).encode("utf-8")
        source = build_files(self.manifest, self.files)

        result = analyze_package(source)

        self.assertEqual(result["command"], "analyze-v3")
        self.assertEqual(result["pageCount"], 2)
        self.assertEqual(result["analysisScope"],
                         "static HTML source; no JavaScript execution or computed browser DOM")
        self.assertEqual(hashlib.sha256(source).hexdigest(),
                         result["skin"]["packageSha256"])
        page = next(item for item in result["pages"] if item["path"] == "web/index.html")
        self.assertEqual(page["title"], "日程 & Memo")
        self.assertEqual(page["stylesheetSources"], ["style.css"])
        self.assertEqual(page["scriptSources"], ["app.js"])
        self.assertEqual(page["inlineScriptCount"], 1)
        self.assertTrue(any("JavaScript is not executed" in warning
                            for warning in page["warnings"]))
        nodes = {node["id"]: node for node in page["components"] if node["id"]}
        self.assertEqual(nodes["open"]["tag"], "button")
        self.assertEqual(nodes["open"]["directText"], "打开")
        self.assertIn("data-action", {item["name"] for item in nodes["open"]["attributes"]})
        card = next(node for node in page["components"]
                    if node["component"] == "event-card")
        image = next(node for node in page["components"] if node["tag"] == "img")
        span = next(node for node in page["components"] if node["tag"] == "span")
        self.assertEqual(image["parentId"], card["nodeId"])
        self.assertEqual(span["parentId"], card["nodeId"])
        self.assertIn(nodes["open"]["nodeId"], card["childIds"])
        self.assertEqual(result["componentCount"],
                         sum(item["componentCount"] for item in result["pages"]))
        month = next(item for item in result["pages"] if item["path"] == "web/month.html")
        self.assertEqual(month["duplicateIds"], ["same"])
        self.assertTrue(any("unmatched closing tag </ghost>" in warning
                            for warning in month["warnings"]))
        self.assertEqual(unpack(source)[1], self.files)

    def test_bridge_audit_cli_indexes_static_bindings_and_capability(self):
        self.files["web/index.html"] = (
            '<!doctype html><html><body><button data-action="month">Month</button>'
            '<button data-action="browseMonth" data-delta-months="1">Next</button>'
            '</body></html>'
        ).encode("utf-8")
        source = build_files(self.manifest, self.files)
        with isolated_temp_dir() as temp:
            package_path = Path(temp) / "audit.pcskin"
            package_path.write_bytes(source)
            result = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"),
                 "audit-bridge-v3", str(package_path)],
                cwd=STUDIO.parents[1], text=True, encoding="utf-8",
                capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        audit = json.loads(result.stdout)
        self.assertEqual(audit["command"], "audit-bridge-v3")
        self.assertTrue(audit["ok"])
        self.assertEqual(audit["requiredCapabilities"], ["host.action"])
        self.assertEqual({binding["action"] for binding in audit["bindings"]},
                         {"month", "browseMonth"})
        self.assertEqual(audit["contractSchema"]["path"],
                         "docs/contracts/web-skin-bridge-v1.schema.json")
        self.assertEqual({binding["payloadSchemaRef"] for binding in audit["bindings"]},
                         {"#/$defs/actionPayloads/month",
                          "#/$defs/actionPayloads/browseMonth"})
        self.assertEqual(audit["unknownBindings"], [])
        self.assertIn("static web/*.html", audit["scope"])

    def test_bridge_audit_rejects_unknown_actions_and_missing_capability(self):
        self.files["web/index.html"] = (
            '<!doctype html><html><body><button data-action="inventedAction">Run</button>'
            '</body></html>'
        ).encode("utf-8")
        self.manifest["capabilities"].remove("host.action")
        source = build_files(self.manifest, self.files)

        audit = audit_bridge_package(source)

        self.assertFalse(audit["ok"])
        self.assertEqual(audit["missingCapabilities"], ["host.action"])
        self.assertEqual(audit["unknownBindings"][0]["action"], "inventedAction")

    def test_bridge_audit_cli_emits_json_error_and_exit_code_two(self):
        self.files["web/index.html"] = (
            '<!doctype html><html><body><button data-action="inventedAction">Run</button>'
            '</body></html>'
        ).encode("utf-8")
        self.manifest["capabilities"].remove("host.action")
        source = build_files(self.manifest, self.files)
        with isolated_temp_dir() as temp:
            package_path = Path(temp) / "invalid-audit.pcskin"
            package_path.write_bytes(source)
            result = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"),
                 "audit-bridge-v3", str(package_path)],
                cwd=STUDIO.parents[1], text=True, encoding="utf-8",
                capture_output=True, check=False)

        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        error = json.loads(result.stderr)
        self.assertEqual(error["command"], "audit-bridge-v3")
        self.assertFalse(error["ok"])
        self.assertEqual(error["missingCapabilities"], ["host.action"])
        self.assertEqual(error["unknownBindings"][0]["action"], "inventedAction")

    def test_bridge_action_registry_matches_runtime_closed_decoder(self):
        registry_path = STUDIO / "bridge_v1_action_registry.json"
        registry = json.loads(registry_path.read_text(encoding="utf-8"))
        host_source = (STUDIO.parents[1] /
                       "app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt")
        source = host_source.read_text(encoding="utf-8")
        decoder = source.split("return when (name) {", 1)[1].split("\n    }\n}", 1)[0]
        rows = re.findall(r'^        ((?:"[A-Za-z0-9_.]+"\s*,?\s*)+)->',
                          decoder, re.MULTILINE)
        runtime_actions = {name for row in rows
                           for name in re.findall(r'"([A-Za-z0-9_.]+)"', row)}

        self.assertEqual(set(registry["actions"]), runtime_actions)

    def test_bridge_v1_schema_covers_registry_examples_and_local_references(self):
        schema_path = STUDIO.parents[1] / "docs/contracts/web-skin-bridge-v1.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        registry = json.loads((STUDIO / "bridge_v1_action_registry.json").read_text(
            encoding="utf-8"))
        definitions = schema["$defs"]
        action_schemas = definitions["actionPayloads"]
        command_schemas = definitions["commandPayloads"]

        self.assertEqual(set(action_schemas), set(registry["actions"]))
        action_refs = {item["$ref"].rsplit("/", 1)[-1] for item in
                       definitions["actionMessage"]["properties"]["payload"]["oneOf"]}
        command_names = {name for name in command_schemas if "." in name}
        command_refs = {item["$ref"].rsplit("/", 1)[-1] for item in
                        definitions["commandMessage"]["properties"]["payload"]["oneOf"]}
        self.assertEqual(action_refs, set(action_schemas))
        self.assertEqual(command_refs, command_names)

        for group in (action_schemas, command_schemas):
            for name, payload_schema in group.items():
                for example in payload_schema.get("examples", []):
                    keys = set(example)
                    self.assertTrue(set(payload_schema["required"]).issubset(keys), name)
                    self.assertTrue(keys.issubset(set(payload_schema["properties"])), name)
                    self.assertEqual(example["name"], name, name)

        def validate_refs(value):
            if isinstance(value, dict):
                if "$ref" in value:
                    ref = value["$ref"]
                    self.assertTrue(ref.startswith("#/"), ref)
                    target = schema
                    for token in ref[2:].split("/"):
                        token = token.replace("~1", "/").replace("~0", "~")
                        target = target[token]
                    self.assertIsNotNone(target, ref)
                for child in value.values():
                    validate_refs(child)
            elif isinstance(value, list):
                for child in value:
                    validate_refs(child)

        validate_refs(schema)

    def test_bridge_v1_command_schema_matches_runtime_decoder_names(self):
        schema_path = STUDIO.parents[1] / "docs/contracts/web-skin-bridge-v1.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        command_schemas = schema["$defs"]["commandPayloads"]
        schema_commands = {name for name in command_schemas if "." in name}
        host_source = (STUDIO.parents[1] /
                       "app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt")
        source = host_source.read_text(encoding="utf-8")
        decoder = source.split("return when(name) {", 1)[1].split(
            "\n    }\n\n    /** A semantic patch", 1)[0]
        rows = re.findall(r'^\s*((?:"[^"\n]+"\s*,?\s*)+)->', decoder, re.MULTILINE)
        runtime_commands = {name for row in rows
                            for name in re.findall(r'"([^"\n]+)"', row)}

        self.assertEqual(schema_commands, runtime_commands)

    def test_analyze_enforces_total_component_bound(self):
        self.files["web/index.html"] = ("<i></i>" * 20_001).encode("utf-8")
        source = build_files(self.manifest, self.files)
        with self.assertRaisesRegex(SkinError, "exceeds 20000 components"):
            analyze_package(source)

    def test_static_component_insert_replace_remove_and_move_operations(self):
        path = "web/index.html"
        html = (
            "<!doctype html><html><head><title>组件</title></head><body>"
            '<main id="root"><section id="first"><b>甲</b></section>'
            '<section id="second"><em>乙</em></section></main></body></html>'
        ).encode("utf-8")
        source = build_files(self.manifest, {**self.files, path: html})

        _, files, inserted, changes = edit_package(source, [{
            "op": "component-insert", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(source, "root"), "position": "append",
            "html": '<article id="third"><span>新</span></article>',
        }])
        self.assertIn('<article id="third"><span>新</span></article>'.encode(), files[path])
        self.assertEqual(changes[0]["op"], "component-insert")
        ordered = [node["id"] for node in analyze_package(inserted)["pages"][0]["components"]
                   if node["id"] in {"first", "second", "third"}]
        self.assertEqual(ordered, ["first", "second", "third"])

        html = files[path]
        _, files, moved, _ = edit_package(inserted, [{
            "op": "component-move", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "sourceNodeId": html_node_id(inserted, "first"),
            "targetNodeId": html_node_id(inserted, "second"), "position": "after",
        }])
        ordered = [node["id"] for node in analyze_package(moved)["pages"][0]["components"]
                   if node["id"] in {"first", "second"}]
        self.assertEqual(ordered, ["second", "first"])
        self.assertIn('<section id="first"><b>甲</b></section>'.encode(), files[path])

        html = files[path]
        _, files, replaced, _ = edit_package(moved, [{
            "op": "component-replace", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(moved, "first"),
            "html": '<aside id="replacement">更新</aside>',
        }])
        self.assertNotIn(b'id="first"', files[path])
        self.assertIn('<aside id="replacement">更新</aside>'.encode(), files[path])

        html = files[path]
        _, files, removed, _ = edit_package(replaced, [{
            "op": "component-remove", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(replaced, "second"),
        }])
        self.assertNotIn(b'id="second"', files[path])
        ids = {node["id"] for node in analyze_package(removed)["pages"][0]["components"]
               if node["id"]}
        self.assertTrue({"root", "third", "replacement"}.issubset(ids))

    def test_static_component_attributes_edit_any_attribute_and_preserve_other_source(self):
        path = "web/index.html"
        html = (
            "<!doctype html><html><body>\r\n"
            '<button id=\'save\' class="old" disabled data-mode=\'compact\' data-mode="extra" '
            'title="keep? remove" >Save</button>\r\n'
            '<aside id="sibling" data-opaque=" untouched "></aside>'
            "</body></html>"
        ).encode("utf-8")
        source = build_files(self.manifest, {**self.files, path: html})
        original_files = unpack(source)[1]
        _, files, edited, changes = edit_package(source, [{
            "op": "component-attributes", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(source, "save"),
            "setAttributes": {
                "class": "primary&selected",
                "aria-label": 'Save "now" <draft>',
                "disabled": None,
                "data-mode": "expanded",
            },
            "removeAttributes": ["title"],
        }])

        page = analyze_package(edited)["pages"][0]
        button = next(node for node in page["components"] if node.get("id") == "save")
        sibling = next(node for node in page["components"] if node.get("id") == "sibling")
        attributes = {item["name"]: item["value"] for item in button["attributes"]}
        self.assertEqual(attributes["class"], "primary&selected")
        self.assertEqual(attributes["aria-label"], 'Save "now" <draft>')
        self.assertIsNone(attributes["disabled"])
        self.assertEqual(attributes["data-mode"], "expanded")
        self.assertEqual(sum(item["name"] == "data-mode" for item in button["attributes"]), 1)
        self.assertNotIn("title", attributes)
        self.assertEqual(sibling["attributes"], [
            {"name": "id", "value": "sibling"},
            {"name": "data-opaque", "value": " untouched "},
        ])
        self.assertIn(b"\r\n", files[path])
        self.assertEqual(files[path].count(b"\r\n"), html.count(b"\r\n"))
        self.assertEqual(changes[0]["op"], "component-attributes")
        self.assertEqual(original_files[path], html)

    def test_component_style_edits_scoped_properties_and_roundtrips(self):
        html_path, css_path = "web/index.html", "web/style.css"
        html = b'<!doctype html><html><head><link rel="stylesheet" href="style.css"></head><body><button id="save">Save</button></body></html>'
        css = b"/* authored */\nbutton { color: black; }\n"
        source = build_files(self.manifest, {
            **self.files, html_path: html, css_path: css,
        })
        first = {
            "op": "component-style", "path": html_path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "cssPath": css_path, "expectedCssSha256": hashlib.sha256(css).hexdigest(),
            "targetNodeId": html_node_id(source, "save"),
            "setProperties": {"color": "#123456", "--card-gap": "calc(2px + 1vw)"},
            "removeProperties": [],
        }

        _, files, styled, changes = edit_package(source, [first])

        page = analyze_package(styled)["pages"][0]
        button = next(node for node in page["components"] if node.get("id") == "save")
        marker = next(attribute["value"] for attribute in button["attributes"]
                      if attribute["name"] == "data-pcskin-studio-style")
        self.assertRegex(marker, r"^pcss-[0-9a-f]{24}$")
        self.assertEqual(files[html_path], html.replace(
            b">Save", f' data-pcskin-studio-style="{marker}">Save'.encode()))
        self.assertIn(f'[data-pcskin-studio-style="{marker}"] {{'.encode(), files[css_path])
        self.assertIn(b"--card-gap: calc(2px + 1vw);", files[css_path])
        self.assertIn(b"button { color: black; }", files[css_path])
        self.assertEqual({entry["path"] for entry in changes}, {html_path, css_path})
        self.assertEqual(unpack(styled)[1], files)

        next_html, next_css = files[html_path], files[css_path]
        update = {
            **first,
            "expectedSha256": hashlib.sha256(next_html).hexdigest(),
            "expectedCssSha256": hashlib.sha256(next_css).hexdigest(),
            "targetNodeId": html_node_id(styled, "save"),
            "setProperties": {"COLOR": "#abcdef", "padding": "4px"},
            "removeProperties": ["--card-gap"],
        }
        _, files, updated, update_changes = edit_package(styled, [update])
        self.assertIn(b"color: #abcdef;", files[css_path])
        self.assertIn(b"padding: 4px;", files[css_path])
        self.assertNotIn(b"--card-gap:", files[css_path])
        self.assertIn(b"/* authored */", files[css_path])
        self.assertEqual({entry["path"] for entry in update_changes}, {css_path})

        current_html, current_css = files[html_path], files[css_path]
        noop = {
            **update,
            "expectedSha256": hashlib.sha256(current_html).hexdigest(),
            "expectedCssSha256": hashlib.sha256(current_css).hexdigest(),
            "targetNodeId": html_node_id(updated, "save"),
            "setProperties": {"color": "#abcdef", "padding": "4px"},
            "removeProperties": [],
        }
        _, noop_files, _, noop_changes = edit_package(updated, [noop])
        self.assertEqual(noop_files, files)
        self.assertEqual(noop_changes, [])

        clear = {
            **noop,
            "setProperties": {},
            "removeProperties": ["color", "padding"],
        }
        _, cleared_files, cleared, clear_changes = edit_package(updated, [clear])
        self.assertNotIn(b"pcskin-studio-style-begin:", cleared_files[css_path])
        self.assertNotIn(b"data-pcskin-studio-style", cleared_files[html_path])
        self.assertIn(b"button { color: black; }", cleared_files[css_path])
        self.assertEqual(cleared_files[html_path], html)
        self.assertEqual({entry["path"] for entry in clear_changes}, {html_path, css_path})
        self.assertEqual(unpack(cleared)[1], cleared_files)

    def test_component_style_rejects_stale_sources_and_css_injection(self):
        html_path, css_path = "web/index.html", "web/style.css"
        html = b'<!doctype html><html><body><button id="save">Save</button></body></html>'
        css = b"button { color: black; }\n"
        source = build_files(self.manifest, {**self.files, html_path: html, css_path: css})
        base = {
            "op": "component-style", "path": html_path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "cssPath": css_path, "expectedCssSha256": hashlib.sha256(css).hexdigest(),
            "targetNodeId": html_node_id(source, "save"),
            "setProperties": {"color": "red"}, "removeProperties": [],
        }
        with self.assertRaisesRegex(SkinError, "changed since inspection"):
            edit_package(source, [{**base, "expectedCssSha256": "0" * 64}])
        with self.assertRaisesRegex(SkinError, "unsupported delimiters"):
            edit_package(source, [{**base, "setProperties": {"color": "red;} body {display:none"}}])
        with self.assertRaisesRegex(SkinError, "valid property name"):
            edit_package(source, [{**base, "setProperties": {"bad property": "red"}}])
        self.assertEqual(unpack(source)[1][html_path], html)
        self.assertEqual(unpack(source)[1][css_path], css)

    def test_component_attribute_noop_preserves_original_opening_tag_bytes(self):
        path = "web/index.html"
        html = b"<!doctype html><html><body><button id='save' class = old>Save</button></body></html>"
        source = build_files(self.manifest, {**self.files, path: html})
        _, files, _, changes = edit_package(source, [{
            "op": "component-attributes", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(source, "save"),
            "setAttributes": {"id": "save", "class": "old"},
            "removeAttributes": ["missing"],
        }])
        self.assertEqual(files[path], html)
        self.assertEqual(changes, [])

    def test_component_attribute_edit_rejects_bad_fields_and_stale_source(self):
        path = "web/index.html"
        html = b'<!doctype html><html><body><button id="save" title="old">Save</button></body></html>'
        source = build_files(self.manifest, {**self.files, path: html})
        base = {
            "op": "component-attributes", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": html_node_id(source, "save"),
            "setAttributes": {"title": "new"}, "removeAttributes": [],
        }

        with self.assertRaisesRegex(SkinError, "attribute name"):
            edit_package(source, [{**base, "setAttributes": {"bad name": "x"}}])
        with self.assertRaisesRegex(SkinError, "attribute name"):
            edit_package(source, [{**base, "setAttributes": {"bad\x00name": "x"}}])
        with self.assertRaisesRegex(SkinError, "attribute value"):
            edit_package(source, [{**base, "setAttributes": {"title": "bad\x00value"}}])
        with self.assertRaisesRegex(SkinError, "valid Unicode"):
            edit_package(source, [{**base, "setAttributes": {"title": "\ud800"}}])
        with self.assertRaisesRegex(SkinError, "both set and removed"):
            edit_package(source, [{**base, "removeAttributes": ["TITLE"]}])
        with self.assertRaisesRegex(SkinError, "changed since inspection"):
            edit_package(source, [{**base, "expectedSha256": "0" * 64}])
        self.assertEqual(unpack(source)[1][path], html)

    def test_component_operations_reject_stale_or_unsafe_edits_without_mutating_input(self):
        path = "web/index.html"
        html = (b'<!doctype html><html><body><main id="root"></main>'
                b'<img id="icon"><script id="script" src="app.js"></script>'
                b'</body></html>')
        source = build_files(self.manifest, {**self.files, path: html})
        target = html_node_id(source, "root")
        base = {"op": "component-insert", "path": path,
                "expectedSha256": hashlib.sha256(html).hexdigest(),
                "targetNodeId": target, "position": "append"}

        with self.assertRaisesRegex(SkinError, "changed since inspection"):
            edit_package(source, [{**base, "expectedSha256": "0" * 64,
                                   "html": '<p id="new">new</p>'}])
        with self.assertRaisesRegex(SkinError, "well-formed HTML fragment"):
            edit_package(source, [{**base, "html": '<p id="broken">missing close'}])
        with self.assertRaisesRegex(SkinError, "duplicate HTML id"):
            edit_package(source, [{**base, "html": '<p id="root">duplicate</p>'}])

        with self.assertRaisesRegex(SkinError, "raw-text element"):
            edit_package(source, [{
                **base, "targetNodeId": html_node_id(source, "script"),
                "html": "<span>not script text</span>",
            }])
        with self.assertRaisesRegex(SkinError, "paired container element"):
            edit_package(source, [{
                **base, "targetNodeId": html_node_id(source, "icon"),
                "html": "<span>cannot be a child</span>",
            }])

        malformed_page = (b'<!doctype html><html><body><main id="broken">'
                          b'</body></html>')
        malformed_source = build_files(self.manifest,
                                       {**self.files, path: malformed_page})
        with self.assertRaisesRegex(SkinError, "well-formed explicit HTML"):
            edit_package(malformed_source, [{
                "op": "component-insert", "path": path,
                "expectedSha256": hashlib.sha256(malformed_page).hexdigest(),
                "targetNodeId": html_node_id(malformed_source, "broken"),
                "position": "append", "html": "<span>cannot safely edit</span>",
            }])

        _, _, package, _ = edit_package(source, [{
            "op": "component-insert", "path": path,
            "expectedSha256": hashlib.sha256(html).hexdigest(),
            "targetNodeId": target, "position": "append",
            "html": '<section id="child"></section>',
        }])
        current_html = unpack(package)[1][path]
        with self.assertRaisesRegex(SkinError, "into its own descendant"):
            edit_package(package, [{
                "op": "component-move", "path": path,
                "expectedSha256": hashlib.sha256(current_html).hexdigest(),
                "sourceNodeId": html_node_id(package, "root"),
                "targetNodeId": html_node_id(package, "child"), "position": "append",
            }])
        self.assertEqual(unpack(source)[1][path], html)

    def test_component_positions_preserve_crlf_and_protect_document_shell(self):
        path = "web/index.html"
        html = ("<!doctype html>\r\n<html><body id=\"body\">\r\n"
                '<main id="root"><img id="icon"><aside id="tail"></aside></main>\r\n'
                "</body></html>").encode("utf-8")
        source = build_files(self.manifest, {**self.files, path: html})

        operations = [
            ("root", "prepend", '<header id="header"></header>'),
            ("tail", "before", '<section id="before-tail"></section>'),
            ("icon", "after", '<span id="after-icon"></span>'),
        ]
        package = source
        for target_id, position, fragment in operations:
            current = unpack(package)[1][path]
            _, _, package, _ = edit_package(package, [{
                "op": "component-insert", "path": path,
                "expectedSha256": hashlib.sha256(current).hexdigest(),
                "targetNodeId": html_node_id(package, target_id),
                "position": position, "html": fragment,
            }])

        updated = unpack(package)[1][path]
        self.assertEqual(updated.count(b"\r\n"), html.count(b"\r\n"))
        nodes = analyze_package(package)["pages"][0]["components"]
        ordered = [node["id"] for node in nodes if node.get("id") in {
            "header", "icon", "after-icon", "before-tail", "tail"}]
        self.assertEqual(ordered, ["header", "icon", "after-icon", "before-tail", "tail"])

        with self.assertRaisesRegex(SkinError, "document shell elements"):
            edit_package(package, [{
                "op": "component-remove", "path": path,
                "expectedSha256": hashlib.sha256(updated).hexdigest(),
                "targetNodeId": html_node_id(package, "body"),
            }])

    def test_component_operation_cli_emits_receipt_and_reimports_package(self):
        path = "web/index.html"
        html = b'<!doctype html><html><body><main id="root"></main></body></html>'
        source = build_files(self.manifest, {**self.files, path: html})
        with isolated_temp_dir() as temp:
            root = Path(temp)
            source_path = root / "input.pcskin"
            operations_path = root / "operations.json"
            output_path = root / "edited.pcskin"
            source_path.write_bytes(source)
            operations_path.write_text(json.dumps({"operations": [{
                "op": "component-insert", "path": path,
                "expectedSha256": hashlib.sha256(html).hexdigest(),
                "targetNodeId": html_node_id(source, "root"), "position": "append",
                "html": '<button id="new-action" data-action="openEvent">Open</button>',
            }]}), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(source_path), "--operations", str(operations_path),
                 "--out", str(output_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            receipt = json.loads(result.stdout)
            self.assertEqual(receipt["changes"][0]["op"], "component-insert")
            self.assertEqual(receipt["changes"][0]["path"], path)
            self.assertIn(b'data-action="openEvent"', unpack(output_path.read_bytes())[1][path])

            attribute_source = output_path.read_bytes()
            attribute_html = unpack(attribute_source)[1][path]
            attribute_output = root / "attributes-edited.pcskin"
            operations_path.write_text(json.dumps({"operations": [{
                "op": "component-attributes", "path": path,
                "expectedSha256": hashlib.sha256(attribute_html).hexdigest(),
                "targetNodeId": html_node_id(attribute_source, "new-action"),
                "setAttributes": {"aria-label": "Open calendar", "disabled": None},
                "removeAttributes": [],
            }]}), encoding="utf-8")
            attribute_result = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(output_path), "--operations", str(operations_path),
                 "--out", str(attribute_output)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(attribute_result.returncode, 0, attribute_result.stderr)
            attribute_receipt = json.loads(attribute_result.stdout)
            self.assertEqual(attribute_receipt["changes"][0]["op"], "component-attributes")
            edited_html = unpack(attribute_output.read_bytes())[1][path]
            self.assertIn(b'aria-label="Open calendar" disabled', edited_html)

            bad_output = root / "must-not-exist.pcskin"
            operations_path.write_text(json.dumps({"operations": [{
                "op": "component-insert", "path": path,
                "expectedSha256": hashlib.sha256(html).hexdigest(),
                "targetNodeId": html_node_id(source, "root"), "position": "append",
                "html": '<span id="root">duplicate</span>',
            }]}), encoding="utf-8")
            failed = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(source_path), "--operations", str(operations_path),
                 "--out", str(bad_output)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(failed.returncode, 2)
            self.assertFalse(bad_output.exists())
            self.assertIn("duplicate HTML id", json.loads(failed.stderr)["error"])

    def test_component_operations_roundtrip_against_real_clear_source_without_asset_write(self):
        original = ASSET.read_bytes()
        original_manifest, original_files = unpack(original)
        path = "web/index.html"
        page = original_files[path]
        target_node = html_node_id(original, "app")
        _, _, attributed, _ = edit_package(original, [{
            "op": "component-attributes", "path": path,
            "expectedSha256": hashlib.sha256(page).hexdigest(),
            "targetNodeId": target_node,
            "setAttributes": {"data-studio-roundtrip": "temporary & value"},
            "removeAttributes": [],
        }])
        attributed_page = unpack(attributed)[1][path]
        self.assertIn(b'data-studio-roundtrip="temporary &amp; value"', attributed_page)
        inserted = '<aside id="studio-component-roundtrip" hidden></aside>'
        _, _, with_component, _ = edit_package(attributed, [{
            "op": "component-insert", "path": path,
            "expectedSha256": hashlib.sha256(attributed_page).hexdigest(),
            "targetNodeId": html_node_id(attributed, "app"),
            "position": "append", "html": inserted,
        }])
        self.assertIn(b'id="studio-component-roundtrip"',
                      unpack(with_component)[1][path])

        changed_page = unpack(with_component)[1][path]
        _, _, restored, _ = edit_package(with_component, [{
            "op": "component-remove", "path": path,
            "expectedSha256": hashlib.sha256(changed_page).hexdigest(),
            "targetNodeId": html_node_id(with_component, "studio-component-roundtrip"),
        }])
        restored_page = unpack(restored)[1][path]
        _, _, restored, _ = edit_package(restored, [{
            "op": "component-attributes", "path": path,
            "expectedSha256": hashlib.sha256(restored_page).hexdigest(),
            "targetNodeId": html_node_id(restored, "app"),
            "setAttributes": {}, "removeAttributes": ["data-studio-roundtrip"],
        }])
        restored_manifest, restored_files = unpack(restored)
        self.assertEqual(restored_manifest, original_manifest)
        self.assertEqual(restored_files, original_files)
        self.assertEqual(ASSET.read_bytes(), original)

    def test_file_crud_and_json_structure_patch_export_and_reimport(self):
        project_sha = hashlib.sha256(self.files["project.json"]).hexdigest()
        manifest_sha = inspect_package(self.source)["manifestSha256"]
        binary_sha = hashlib.sha256(self.files["assets/raw.bin"]).hexdigest()
        move_sha = hashlib.sha256(self.files["web/move-me.js"]).hexdigest()
        delete_sha = hashlib.sha256(self.files["web/delete-me.txt"]).hexdigest()
        operations = [
            {"op": "write", "path": "web/components/custom.css", "encoding": "utf-8",
             "content": ".custom { color: rebeccapurple; }\n", "expectedAbsent": True},
            {"op": "write", "path": "assets/raw.bin", "encoding": "base64",
             "content": base64.b64encode(b"\x89\x00new-binary\xff").decode("ascii"),
             "expectedSha256": binary_sha},
            {"op": "move", "from": "web/move-me.js", "to": "web/components/move-me.js",
             "expectedSha256": move_sha},
            {"op": "delete", "path": "web/delete-me.txt", "expectedSha256": delete_sha},
            {"op": "json-patch", "path": "project.json", "expectedSha256": project_sha,
             "patch": [
                 {"op": "add", "path": "/components", "value": [
                     {"id": "panel", "type": "container", "children": [
                         {"id": "heading", "type": "text"},
                         {"id": "body", "type": "text"}]}]},
                 {"op": "test", "path": "/components/0/children/0/id", "value": "heading"},
                 {"op": "move", "from": "/components/0/children/0",
                  "path": "/components/0/children/1"},
                 {"op": "copy", "from": "/components/0/children/0",
                  "path": "/components/0/children/-"},
                 {"op": "replace", "path": "/components/0/children/2/id",
                  "value": "body-copy"},
             ]},
            {"op": "manifest-patch", "expectedSha256": manifest_sha,
             "patch": [{"op": "replace", "path": "/name", "value": "Agent Edited Skin"}]},
        ]

        manifest, files, exported, changes = edit_package(self.source, operations)
        imported_manifest, imported_files = unpack(exported)
        self.assertEqual(imported_manifest, manifest)
        self.assertEqual(manifest["name"], "Agent Edited Skin")
        self.assertEqual(imported_files, files)
        self.assertEqual(files["web/components/custom.css"],
                         b".custom { color: rebeccapurple; }\n")
        self.assertEqual(files["assets/raw.bin"], b"\x89\x00new-binary\xff")
        self.assertEqual(files["web/components/move-me.js"], self.files["web/move-me.js"])
        self.assertNotIn("web/move-me.js", files)
        self.assertNotIn("web/delete-me.txt", files)
        project = json.loads(files["project.json"])
        self.assertEqual([item["id"] for item in project["components"][0]["children"]],
                         ["body", "heading", "body-copy"])
        self.assertEqual({change["path"] for change in changes}, {
            "web/components/custom.css", "assets/raw.bin", "web/move-me.js",
            "web/components/move-me.js", "web/delete-me.txt", "project.json", "manifest.json"})
        self.assertEqual(inspect_package(exported)["fileCount"], len(files))

    def test_manifest_capability_change_requires_explicit_flag(self):
        manifest_sha = inspect_package(self.source)["manifestSha256"]
        patch = [{"op": "replace", "path": "/capabilities", "value": []}]
        with self.assertRaisesRegex(SkinError, "allowCapabilityChange=true"):
            edit_package(self.source, [{"op": "manifest-patch", "expectedSha256": manifest_sha,
                                       "patch": patch}])
        edited_manifest, _, _, _ = edit_package(self.source, [{
            "op": "manifest-patch", "expectedSha256": manifest_sha,
            "allowCapabilityChange": True, "patch": patch,
        }])
        self.assertEqual(edited_manifest["capabilities"], [])

    def test_stale_hash_unsafe_path_and_invalid_final_project_reject_whole_batch(self):
        with self.assertRaisesRegex(SkinError, "changed since inspection"):
            edit_package(self.source, [
                {"op": "write", "path": "web/new.css", "encoding": "utf-8",
                 "content": "x", "expectedAbsent": True},
                {"op": "delete", "path": "web/move-me.js", "expectedSha256": "0" * 64},
            ])
        with self.assertRaises(SkinError):
            edit_package(self.source, [{
                "op": "write", "path": "web/../escape.js", "encoding": "utf-8",
                "content": "x", "expectedAbsent": True,
            }])
        project_sha = hashlib.sha256(self.files["project.json"]).hexdigest()
        with self.assertRaisesRegex(SkinError, "mode must be"):
            edit_package(self.source, [{
                "op": "json-patch", "path": "project.json", "expectedSha256": project_sha,
                "patch": [{"op": "replace", "path": "/mode", "value": "invalid"}],
            }])
        self.assertEqual(hashlib.sha256(self.source).hexdigest(),
                         hashlib.sha256(build_files(self.manifest, self.files)).hexdigest())

    def test_json_patch_failed_test_does_not_mutate_input_project(self):
        project_sha = hashlib.sha256(self.files["project.json"]).hexdigest()
        with self.assertRaisesRegex(SkinError, "test failed"):
            edit_package(self.source, [{
                "op": "json-patch", "path": "project.json", "expectedSha256": project_sha,
                "patch": [
                    {"op": "add", "path": "/newField", "value": True},
                    {"op": "test", "path": "/mode", "value": "does-not-match"},
                ],
            }])
        self.assertEqual(unpack(self.source)[1]["project.json"], self.files["project.json"])

    def test_cli_is_machine_readable_and_does_not_write_on_invalid_batch(self):
        with isolated_temp_dir() as temp:
            root = Path(temp)
            source_path = root / "input.pcskin"
            source_path.write_bytes(self.source)
            inspected = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "inspect-v3", str(source_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(inspected.returncode, 0, inspected.stderr)
            self.assertEqual(json.loads(inspected.stdout)["manifest"]["id"], "ai-project-ops-test")
            analyzed = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "analyze-v3", str(source_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(analyzed.returncode, 0, analyzed.stderr)
            analysis = json.loads(analyzed.stdout)
            self.assertEqual(analysis["command"], "analyze-v3")
            self.assertTrue(analysis["ok"])
            self.assertEqual(analysis["pageCount"], 1)
            self.assertTrue(any(node["id"] == "timelinePage"
                                for node in analysis["pages"][0]["components"]))
            read = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "read-v3",
                 "--file", str(source_path), "--path", "web/move-me.js"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(read.returncode, 0, read.stderr)
            self.assertEqual(json.loads(read.stdout)["content"], "export const value = 1;\n")

            project_sha = hashlib.sha256(self.files["project.json"]).hexdigest()
            operations_path = root / "operations.json"
            output_path = root / "edited.pcskin"
            operations_path.write_text(json.dumps({"operations": [{
                "op": "json-patch", "path": "project.json", "expectedSha256": project_sha,
                "patch": [{"op": "add", "path": "/agentEdited", "value": True}],
            }]}), encoding="utf-8")
            edited = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(source_path), "--operations", str(operations_path),
                 "--out", str(output_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(edited.returncode, 0, edited.stderr)
            result = json.loads(edited.stdout)
            self.assertTrue(result["ok"])
            self.assertTrue(json.loads(unpack(output_path.read_bytes())[1]["project.json"])["agentEdited"])
            validation = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "validate", str(output_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(validation.returncode, 0, validation.stderr)
            self.assertIn("runtime v3", validation.stdout)

            bad_output = root / "must-not-exist.pcskin"
            operations_path.write_text(json.dumps({"operations": [{
                "op": "delete", "path": "web/move-me.js", "expectedSha256": "0" * 64,
            }]}), encoding="utf-8")
            failed = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(source_path), "--operations", str(operations_path),
                 "--out", str(bad_output)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(failed.returncode, 2)
            self.assertFalse(bad_output.exists())
            self.assertFalse(json.loads(failed.stderr)["ok"])

    def test_atomic_output_never_overwrites_existing_package(self):
        with isolated_temp_dir() as temp:
            target = Path(temp) / "skin.pcskin"
            target.write_bytes(b"old")
            with self.assertRaisesRegex(SkinError, "already exists"):
                write_atomic(target, b"new")
            self.assertEqual(target.read_bytes(), b"old")

    def test_v3_build_and_validate_cli_json_are_reimportable_and_non_overwriting(self):
        with isolated_temp_dir() as temp:
            root = Path(temp)
            manifest_path = root / "manifest.json"
            source_dir = root / "source"
            source_dir.mkdir()
            manifest_path.write_text(json.dumps(self.manifest), encoding="utf-8")
            for name, data in self.files.items():
                target = source_dir / Path(name)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            output_path = root / "built.pcskin"

            built = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "build-v3",
                 "--manifest", str(manifest_path), "--source-dir", str(source_dir),
                 "--out", str(output_path), "--json"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(built.returncode, 0, built.stderr)
            build_receipt = json.loads(built.stdout)
            self.assertEqual(build_receipt["schemaVersion"], 1)
            self.assertEqual(build_receipt["command"], "build-v3")
            self.assertTrue(build_receipt["ok"])
            self.assertEqual(build_receipt["fileCount"], len(self.files))
            self.assertEqual(build_receipt["bytes"], output_path.stat().st_size)
            self.assertEqual(build_receipt["packageSha256"],
                             hashlib.sha256(output_path.read_bytes()).hexdigest())

            imported_manifest, imported_files = unpack(output_path.read_bytes())
            self.assertEqual(imported_manifest["id"], "ai-project-ops-test")
            self.assertEqual(imported_files, self.files)

            validated = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "validate",
                 str(output_path), "--json"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(validated.returncode, 0, validated.stderr)
            validation_receipt = json.loads(validated.stdout)
            self.assertEqual(validation_receipt["command"], "validate")
            self.assertTrue(validation_receipt["ok"])
            self.assertEqual(validation_receipt["runtimeVersion"], 3)
            self.assertEqual(validation_receipt["skinId"], "ai-project-ops-test")
            self.assertEqual(validation_receipt["fileCount"], len(self.files))
            self.assertEqual(validation_receipt["packageSha256"],
                             build_receipt["packageSha256"])

            broken_path = root / "broken.pcskin"
            broken_path.write_bytes(b"not a zip archive")
            invalid = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "validate",
                 str(broken_path), "--json"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(invalid.returncode, 2)
            invalid_receipt = json.loads(invalid.stderr)
            self.assertEqual(invalid_receipt["command"], "validate")
            self.assertFalse(invalid_receipt["ok"])
            invalid_analysis = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "analyze-v3",
                 str(broken_path)], text=True, encoding="utf-8", capture_output=True,
                check=False)
            self.assertEqual(invalid_analysis.returncode, 2)
            self.assertEqual(json.loads(invalid_analysis.stderr)["command"], "analyze-v3")
            self.assertFalse(json.loads(invalid_analysis.stderr)["ok"])

            before = output_path.read_bytes()
            overwrite = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "build-v3",
                 "--manifest", str(manifest_path), "--source-dir", str(source_dir),
                 "--out", str(output_path), "--json"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(overwrite.returncode, 2)
            error_receipt = json.loads(overwrite.stderr)
            self.assertEqual(error_receipt["command"], "build-v3")
            self.assertFalse(error_receipt["ok"])
            self.assertEqual(output_path.read_bytes(), before)

            human_output_path = root / "built-human.pcskin"
            human_build = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "build-v3",
                 "--manifest", str(manifest_path), "--source-dir", str(source_dir),
                 "--out", str(human_output_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(human_build.returncode, 0, human_build.stderr)
            self.assertTrue(human_build.stdout.startswith("Built v3 "))
            human_package = human_output_path.read_bytes()
            self.assertEqual(unpack(human_package)[1], self.files)
            self.assertEqual(human_package, before)


if __name__ == "__main__":
    unittest.main()

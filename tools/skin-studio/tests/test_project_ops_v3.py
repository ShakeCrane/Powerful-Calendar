import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

STUDIO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STUDIO))

from build_default_clear import ASSET
from project_ops_v3 import analyze_package, edit_package, inspect_package, read_file, write_atomic
from runtime_package_v3 import build_files, unpack
from skin_studio import SkinError


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

    def test_analyze_enforces_total_component_bound(self):
        self.files["web/index.html"] = ("<i></i>" * 20_001).encode("utf-8")
        source = build_files(self.manifest, self.files)
        with self.assertRaisesRegex(SkinError, "exceeds 20000 components"):
            analyze_package(source)

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

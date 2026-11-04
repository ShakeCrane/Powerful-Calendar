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
from blank_project_v3 import blank_project
from project_ops_v3 import edit_package, inspect_package, read_file, write_atomic
from runtime_package_v3 import build_files, unpack, validate_manifest, _page_tree_runtime_source
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
        self.files["web/components/vendor-note.pcal-source"] = "第三方文本源码：不依赖文件扩展名白名单。\n".encode("utf-8")
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
        self.assertEqual(entries["web/components/vendor-note.pcal-source"]["encoding"], "utf-8")
        self.assertEqual(entries["assets/raw.bin"]["encoding"], "binary")
        self.assertEqual(entries["assets/utf8-looking.bin"]["encoding"], "binary")
        self.assertEqual(entries["assets/raw.bin"]["sha256"],
                         hashlib.sha256(self.files["assets/raw.bin"]).hexdigest())
        text_file = read_file(self.source, "web/move-me.js")
        self.assertEqual(text_file["encoding"], "utf-8")
        self.assertEqual(text_file["content"], "export const value = 1;\n")
        arbitrary_text = read_file(self.source, "web/components/vendor-note.pcal-source")
        self.assertEqual(arbitrary_text["encoding"], "utf-8")
        self.assertEqual(arbitrary_text["content"], "第三方文本源码：不依赖文件扩展名白名单。\n")
        binary_file = read_file(self.source, "assets/raw.bin")
        self.assertEqual(binary_file["encoding"], "base64")
        self.assertEqual(base64.b64decode(binary_file["content"]), b"\x00\xffold-binary")
        utf8_binary_file = read_file(self.source, "assets/utf8-looking.bin")
        self.assertEqual(utf8_binary_file["encoding"], "base64")
        self.assertEqual(base64.b64decode(utf8_binary_file["content"]),
                         self.files["assets/utf8-looking.bin"])

    def test_blank_page_tree_has_stable_unique_ids_and_rejects_duplicates(self):
        manifest, files = blank_project()
        project = json.loads(files["project.json"].decode("utf-8"))
        self.assertEqual(["TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS"],
                         [page["route"] for page in project["pages"]])
        identifiers = [page["id"] for page in project["pages"]]
        for page in project["pages"]:
            self.assertEqual(1, len(page["components"]))
            identifiers.append(page["components"][0]["id"])
        self.assertEqual(len(identifiers), len(set(identifiers)))
        rebuilt = build_files(manifest, files)
        _, reopened_files = unpack(rebuilt)
        self.assertEqual(files["project.json"], reopened_files["project.json"])

        invalid_files = dict(files)
        invalid = json.loads(invalid_files["project.json"].decode("utf-8"))
        invalid["pages"][1]["components"][0]["id"] = identifiers[0]
        invalid_files["project.json"] = json.dumps(invalid).encode("utf-8")
        with self.assertRaisesRegex(SkinError, "repeated project node id"):
            build_files(manifest, invalid_files)

    def test_blank_runtime_projection_is_derived_from_the_canonical_page_tree(self):
        manifest, files = blank_project()
        runtime_path = "web/project-tree-v1.js"
        project = json.loads(files["project.json"].decode("utf-8"))
        self.assertEqual("powercal-component-tree-v1", project["runtimeRenderer"])
        runtime = files[runtime_path].decode("utf-8")
        self.assertIn("window.__POWERCAL_PAGE_TREE_V1__=JSON.parse(", runtime)
        runtime_literal = runtime.removeprefix("window.__POWERCAL_PAGE_TREE_V1__=JSON.parse(").removesuffix(");\n")
        self.assertEqual(project, json.loads(json.loads(runtime_literal)))

        project["pages"][1]["components"][0]["children"].append({
            "id": "studio-header", "type": "text",
            "props": {"label": "November", "futureProp": {"keep": True},
                      "__proto__": {"inert": True}}, "children": []})
        edited_files = dict(files)
        edited_files["project.json"] = json.dumps(project, ensure_ascii=False).encode("utf-8")
        rebuilt = build_files(manifest, edited_files)
        _, reopened = unpack(rebuilt)
        expected = _page_tree_runtime_source(project)
        self.assertEqual(expected, reopened[runtime_path])
        self.assertIn(b"futureProp", reopened[runtime_path])
        self.assertIn(b"__proto__", reopened[runtime_path])
        self.assertEqual("November", json.loads(reopened["project.json"])["pages"][1]
                         ["components"][0]["children"][0]["props"]["label"])

        stale = dict(reopened)
        stale[runtime_path] = b"window.__POWERCAL_PAGE_TREE_V1__=JSON.parse(\"{}\");\n"
        with self.assertRaisesRegex(SkinError, "does not match project.json"):
            validate_manifest(manifest, stale, verify_hash=False)
        without_renderer = dict(reopened)
        unmarked_project = json.loads(without_renderer["project.json"])
        del unmarked_project["runtimeRenderer"]
        without_renderer["project.json"] = json.dumps(unmarked_project).encode("utf-8")
        with self.assertRaisesRegex(SkinError, "requires the blank page-tree runtime contract"):
            build_files(manifest, without_renderer)

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

    def test_cli_import_edit_export_reimport_preserves_opaque_manifest_metadata(self):
        metadata = {"publisher": "fixture", "custom": {"labels": ["preserve", "only"]},
                    "claims": ["host.action"]}
        source_manifest = dict(self.manifest)
        source_manifest["x-vendor-metadata"] = metadata
        source = build_files(source_manifest, self.files)

        with isolated_temp_dir() as temp:
            root = Path(temp)
            source_path = root / "input.pcskin"
            source_path.write_bytes(source)
            inspected = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "inspect-v3", str(source_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(inspected.returncode, 0, inspected.stderr)
            self.assertEqual(json.loads(inspected.stdout)["manifest"]["x-vendor-metadata"], metadata)

            manifest_sha = json.loads(inspected.stdout)["manifestSha256"]
            operations_path = root / "operations.json"
            output_path = root / "edited.pcskin"
            operations_path.write_text(json.dumps({"operations": [{
                "op": "manifest-patch", "expectedSha256": manifest_sha,
                "patch": [{"op": "replace", "path": "/name", "value": "Edited Skin"}],
            }]}), encoding="utf-8")
            edited = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "edit-v3",
                 "--file", str(source_path), "--operations", str(operations_path),
                 "--out", str(output_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(edited.returncode, 0, edited.stderr)
            validated = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "validate", str(output_path),
                 "--json"],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(validated.returncode, 0, validated.stderr)

            imported_manifest, imported_files = unpack(output_path.read_bytes())
            self.assertEqual("Edited Skin", imported_manifest["name"])
            self.assertEqual(metadata, imported_manifest["x-vendor-metadata"])
            self.assertEqual(self.files, imported_files)

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

    def test_cli_v3_import_export_round_trip_preserves_blank_project_and_unknown_metadata(self):
        manifest, files = blank_project()
        manifest = dict(manifest)
        manifest["x-vendor-metadata"] = {"issuer": "test", "tags": ["blank", "lossless"]}
        project = json.loads(files["project.json"].decode("utf-8"))
        project["thirdPartyMetadata"] = {"备注": "未知字段应保留"}
        files["project.json"] = json.dumps(
            project, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        files["web/components/custom.syntax"] = "空白源码\n".encode("utf-8")
        source_package = build_files(manifest, files)
        source_manifest, source_files = unpack(source_package)

        with isolated_temp_dir() as temp:
            root = Path(temp)
            package_path = root / "blank.pcskin"
            project_path = root / "blank.pcstudio-v3.json"
            reexport_path = root / "blank-reexport.pcskin"
            package_path.write_bytes(source_package)
            imported = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "import-v3",
                 str(package_path), "--out", str(project_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(imported.returncode, 0, imported.stderr)
            import_receipt = json.loads(imported.stdout)
            self.assertEqual(import_receipt["command"], "import-v3")
            self.assertEqual(import_receipt["packageSchemaVersion"], 3)
            self.assertEqual(import_receipt["requiredCapabilities"], ["host.read"])
            self.assertEqual(import_receipt["fileHashes"], source_manifest["sha256"])
            document = json.loads(project_path.read_text(encoding="utf-8"))
            imported_files = {name: base64.b64decode(value)
                              for name, value in document["files"].items()}
            self.assertEqual(imported_files, source_files)
            self.assertEqual(document["manifest"]["x-vendor-metadata"], manifest["x-vendor-metadata"])

            exported = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "export-v3",
                 str(project_path), "--out", str(reexport_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(exported.returncode, 0, exported.stderr)
            export_receipt = json.loads(exported.stdout)
            self.assertTrue(export_receipt["ok"])
            self.assertEqual(export_receipt["fileHashes"],
                             json.loads(imported.stdout)["fileHashes"])
            self.assertEqual(unpack(source_package), unpack(reexport_path.read_bytes()))

            validated = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "validate-v3",
                 str(reexport_path)], text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(validated.returncode, 0, validated.stderr)
            self.assertEqual(json.loads(validated.stdout)["command"], "validate-v3")
            self.assertEqual(json.loads(validated.stdout)["fileHashes"], export_receipt["fileHashes"])

            original = reexport_path.read_bytes()
            overwrite = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "export-v3",
                 str(project_path), "--out", str(reexport_path)],
                text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(overwrite.returncode, 2)
            self.assertEqual(reexport_path.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()

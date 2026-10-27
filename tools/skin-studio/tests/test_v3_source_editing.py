"""Studio source handlers and the production v3 builder share one file set."""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

STUDIO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STUDIO))
from build_default_clear import ASSET
from runtime_package_v3 import build_files, unpack


class StudioSourceEditingTest(unittest.TestCase):
    def run_studio(self, capabilities):
        manifest, files = unpack(ASSET.read_bytes())
        manifest["id"] = "source-edit-test"
        manifest["capabilities"] = capabilities
        project = json.loads(files["project.json"])
        project.pop("layers", None)
        project["thirdPartyMetadata"] = {"keep": "原样保留"}
        files["project.json"] = (json.dumps(project, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf8")
        files["web/binary.js"] = b"\xff\xfe\x00"
        files["web/agent-unused-90f4.txt"] = b"unused source"
        files["assets/replace.bin"] = b"old resource"
        fixture = {"manifest": manifest, "files": {
            name: base64.b64encode(data).decode("ascii") for name, data in files.items()}}
        script = os.environ.get("POWERCAL_STUDIO_JS", str(STUDIO / "v3-studio.js"))
        result = subprocess.run(
            ["node", str(STUDIO / "tests/v3_source_edit_harness.cjs"), script],
            input=json.dumps(fixture), text=True, encoding="utf-8", capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        return files, json.loads(result.stdout)

    def test_extra_source_edit_and_binary_round_trip(self):
        files, result = self.run_studio(["host.read"])
        self.assertIn("web/edit.js", result["choices"])
        self.assertIn("web/clear-design.css", result["choices"])
        self.assertNotIn("web/binary.js", result["choices"])
        self.assertEqual(result["original"], files["web/edit.js"].decode("utf8"))
        payload = result["payload"]
        self.assertEqual(payload["manifest"]["capabilities"], ["host.read"])
        edited = {name: base64.b64decode(data) for name, data in payload["files"].items()}
        self.assertEqual(edited["web/edit.js"], files["web/edit.js"] + b"\n// studio-source-roundtrip\n")
        self.assertEqual(edited["project.json"], files["project.json"],
                         "editing a script must not rewrite untouched project metadata")
        for name in ("web/clear-design.css", "web/fonts/InterVariable.woff2", "web/binary.js"):
            self.assertEqual(edited[name], files[name], name)
        imported_manifest, imported = unpack(build_files(payload["manifest"], edited))
        self.assertEqual(imported_manifest["capabilities"], ["host.read"])
        self.assertEqual(imported, edited)

    def test_imported_capabilities_are_not_added_or_removed(self):
        for capabilities in ([], ["host.read"], ["host.read", "host.action", "host.edit"]):
            with self.subTest(capabilities=capabilities):
                _, result = self.run_studio(capabilities)
                payload = result["payload"]
                self.assertEqual(payload["manifest"]["capabilities"], capabilities)
                files = {name: base64.b64decode(data) for name, data in payload["files"].items()}
                imported, _ = unpack(build_files(payload["manifest"], files))
                self.assertEqual(imported["capabilities"], capabilities)

    def test_visual_change_updates_metadata_without_dropping_custom_fields(self):
        _, result = self.run_studio(["host.read"])
        payload = result["visualPayload"]
        files = {name: base64.b64decode(data) for name, data in payload["files"].items()}
        project = json.loads(files["project.json"])
        self.assertEqual(project["thirdPartyMetadata"], {"keep": "原样保留"})
        self.assertTrue(any(slot["id"] == "testAccent" for slot in project["slots"]))
        self.assertIn(b"--testAccent", files["web/style.css"])
        _, imported = unpack(build_files(payload["manifest"], files))
        self.assertEqual(imported, files)

    def test_project_tree_and_arbitrary_text_binary_files_are_editable(self):
        original, result = self.run_studio(["host.read"])
        self.assertIn("project.json", result["choices"])
        self.assertTrue(result["invalidProjectRejected"])
        self.assertTrue(result["invalidProjectDraftRestored"])
        self.assertTrue(result["rejectedTextExtension"])

        project_payload = result["projectPayload"]
        project_files = {name: base64.b64decode(value)
                         for name, value in project_payload["files"].items()}
        edited_project = json.loads(project_files["project.json"])
        self.assertEqual(edited_project["agentAuthoredComponents"], [{
            "id": "custom-panel", "type": "container", "children": [{"id": "title", "type": "text"}]
        }])
        self.assertEqual(edited_project["thirdPartyMetadata"], {"keep": "原样保留"})

        payload = result["managedPayload"]
        files = {name: base64.b64decode(value) for name, value in payload["files"].items()}
        self.assertEqual(files["project.json"], project_files["project.json"])
        self.assertEqual(files["web/components/agent-card.js"], b"export const card = 'custom';\n")
        self.assertNotIn("web/agent-unused-90f4.txt", files)
        self.assertEqual(files["assets/agent-new.bin"], bytes([0, 255, 65, 66]))
        self.assertEqual(files["assets/replace.bin"], bytes([255, 0, 128]),
                         "binary replacement must preserve the selected bytes exactly")
        imported_manifest, imported = unpack(build_files(payload["manifest"], files))
        self.assertEqual(imported_manifest["capabilities"], ["host.read"])
        self.assertEqual(imported, files)
        recovered = {name: base64.b64decode(value)
                     for name, value in result["recoveredPayload"]["files"].items()}
        self.assertEqual(recovered["project.json"], project_files["project.json"])
        self.assertIn("web/agent-unused-90f4.txt", original)


if __name__ == "__main__":
    unittest.main()

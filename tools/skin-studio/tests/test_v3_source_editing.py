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


if __name__ == "__main__":
    unittest.main()

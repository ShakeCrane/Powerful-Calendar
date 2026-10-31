import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent
STUDIO_DIR = TEST_DIR.parent
sys.path.insert(0, str(STUDIO_DIR))
import runtime_package_v3 as v3
import project_ops_v3 as project_ops


FIXTURE_DIR = TEST_DIR / "fixtures" / "android-delivery"
SOURCE_DIR = FIXTURE_DIR / "source"
MANIFEST = json.loads((FIXTURE_DIR / "manifest.json").read_text(encoding="utf-8"))
ANDROID_ASSET = (TEST_DIR.parents[2] / "app" / "src" / "androidTest" / "assets" /
                 "skin-delivery-fixture.pcskin")
AI_EDITED_ANDROID_ASSET = (TEST_DIR.parents[2] / "app" / "src" / "androidTest" / "assets" /
                           "skin-ai-edited-fixture.pcskin")
STUDIO_GUI_ANDROID_ASSET = (TEST_DIR.parents[2] / "app" / "src" / "androidTest" / "assets" /
                            "skin-studio-gui-edited-fixture.pcskin")
AI_EDIT_OPERATIONS = FIXTURE_DIR / "ai-runtime-edit-operations.json"


class AndroidDeliveryFixtureTest(unittest.TestCase):
    def run_cli(self, *arguments):
        return subprocess.run(
            [sys.executable, str(STUDIO_DIR / "skin_studio.py"),
             *(str(argument) for argument in arguments)],
            text=True, encoding="utf-8", capture_output=True, check=False)

    def test_android_test_package_is_exact_studio_v3_builder_output(self):
        package = ANDROID_ASSET.read_bytes()
        self.assertEqual(package, v3.build(MANIFEST, SOURCE_DIR))

        manifest, files = v3.unpack(package)
        self.assertEqual("pc-delivery-fixture", manifest["id"])
        self.assertEqual([], manifest["capabilities"])
        self.assertEqual({"web/index.html", "web/style.css", "web/app.js",
                          "assets/probe.png", "project.json"}, set(files))
        self.assertIn(b"studio-html-delivery-fixture", files["web/index.html"])
        self.assertIn(b"studio-js-delivery-fixture-v1", files["web/app.js"])
        self.assertIn(b"--delivery-css-probe", files["web/style.css"])
        self.assertTrue(files["assets/probe.png"].startswith(b"\x89PNG\r\n\x1a\n"))

    def test_android_runtime_skin_is_exact_guarded_studio_cli_edit_output(self):
        with tempfile.TemporaryDirectory(prefix="studio-ai-runtime-") as temp:
            root = Path(temp)
            baseline_path = root / "baseline.pcskin"
            built = self.run_cli(
                "build-v3", "--manifest", FIXTURE_DIR / "manifest.json",
                "--source-dir", SOURCE_DIR, "--out", baseline_path, "--json")
            self.assertEqual(built.returncode, 0, built.stderr)
            self.assertEqual("build-v3", json.loads(built.stdout)["command"])

            baseline = self.run_cli("inspect-v3", baseline_path)
            self.assertEqual(baseline.returncode, 0, baseline.stderr)
            baseline_receipt = json.loads(baseline.stdout)
            self.assertEqual("pc-delivery-fixture", baseline_receipt["manifest"]["id"])
            baseline_script = self.run_cli(
                "read-v3", "--file", baseline_path, "--path", "web/app.js")
            self.assertEqual(baseline_script.returncode, 0, baseline_script.stderr)
            self.assertIn("studio-js-delivery-fixture-v1",
                          json.loads(baseline_script.stdout)["content"])

            edited_path = root / "ai-edited.pcskin"
            edited = self.run_cli(
                "edit-v3", "--file", baseline_path,
                "--operations", AI_EDIT_OPERATIONS, "--out", edited_path)
            self.assertEqual(edited.returncode, 0, edited.stderr)
            edit_receipt = json.loads(edited.stdout)
            self.assertEqual("edit-v3", edit_receipt["command"])
            self.assertEqual({"web/index.html", "web/style.css", "web/app.js", "project.json"},
                             {change["path"] for change in edit_receipt["changes"]})

            validation = self.run_cli("validate", edited_path, "--json")
            self.assertEqual(validation.returncode, 0, validation.stderr)
            self.assertTrue(json.loads(validation.stdout)["ok"])
            inspected = self.run_cli("inspect-v3", edited_path)
            self.assertEqual(inspected.returncode, 0, inspected.stderr)
            inspected_receipt = json.loads(inspected.stdout)
            self.assertEqual("pc-delivery-fixture", inspected_receipt["manifest"]["id"])
            self.assertEqual("android-runtime-verification",
                             inspected_receipt["project"]["aiAutomationProbe"]["purpose"])

            expected_content = {
                "web/index.html": "AI_EDITED_RUNTIME_V1",
                "web/style.css": "#aiEditedProbe",
                "web/app.js": "studio-cli-ai-edit-v1",
                "project.json": "aiAutomationProbe",
            }
            for package_path, marker in expected_content.items():
                read = self.run_cli("read-v3", "--file", edited_path, "--path", package_path)
                self.assertEqual(read.returncode, 0, read.stderr)
                self.assertIn(marker, json.loads(read.stdout)["content"])

            self.assertEqual(edited_path.read_bytes(), AI_EDITED_ANDROID_ASSET.read_bytes())
            _, baseline_files = v3.unpack(baseline_path.read_bytes())
            _, edited_files = v3.unpack(AI_EDITED_ANDROID_ASSET.read_bytes())
            self.assertEqual(set(baseline_files), set(edited_files))
            self.assertEqual(baseline_files["assets/probe.png"], edited_files["assets/probe.png"])

    def test_android_runtime_skin_is_browser_studio_edited_package(self):
        package = STUDIO_GUI_ANDROID_ASSET.read_bytes()
        self.assertEqual(
            hashlib.sha256(package).hexdigest(),
            "885468b1ab1d615e2ebc137a0d513841760d3f26efa666dd893f1932d9822da6")
        manifest, files = v3.unpack(package)
        baseline_manifest, baseline_files = v3.unpack(ANDROID_ASSET.read_bytes())
        self.assertEqual(manifest["id"], baseline_manifest["id"])
        self.assertEqual(set(files), set(baseline_files))
        self.assertIn(b"studio-js-delivery-fixture-v2", files["web/app.js"])
        self.assertNotIn(b"studio-js-delivery-fixture-v1", files["web/app.js"])
        for path, content in baseline_files.items():
            if path != "web/app.js":
                self.assertEqual(files[path], content, f"Browser Studio unexpectedly changed {path}")

    def test_android_installed_v3_fixture_can_be_reopened_edited_and_reexported(self):
        original = ANDROID_ASSET.read_bytes()
        manifest, source_files = v3.unpack(original)
        self.assertEqual("pc-delivery-fixture", manifest["id"])

        app_source = source_files["web/app.js"]
        marker = b"studio-js-delivery-fixture-v1"
        edited_source = app_source.replace(marker, b"studio-js-delivery-fixture-v2")
        self.assertEqual(1, app_source.count(marker))

        edited_manifest, edited_files, exported, changes = project_ops.edit_package(
            original,
            [{
                "op": "write",
                "path": "web/app.js",
                "encoding": "utf-8",
                "content": edited_source.decode("utf-8"),
                "expectedSha256": hashlib.sha256(app_source).hexdigest(),
            }],
        )
        imported_manifest, imported_files = v3.unpack(exported)

        original_metadata = dict(manifest)
        edited_metadata = dict(edited_manifest)
        original_metadata.pop("sha256")
        edited_metadata.pop("sha256")
        self.assertEqual(original_metadata, edited_metadata)
        self.assertEqual(edited_manifest, imported_manifest)
        self.assertEqual(edited_files, imported_files)
        self.assertEqual(edited_source, imported_files["web/app.js"])
        self.assertNotEqual(original, exported)
        self.assertEqual({"web/app.js"}, {change["path"] for change in changes})
        for path, original_bytes in source_files.items():
            if path != "web/app.js":
                self.assertEqual(original_bytes, imported_files[path], path)

    def test_v3_optional_canvas_and_project_defaults_survive_studio_edit(self):
        manifest, source_files = v3.unpack(ANDROID_ASSET.read_bytes())
        manifest.pop("fullCanvas")
        project = json.loads(source_files["project.json"].decode("utf-8"))
        project.pop("slots")
        source_files = dict(source_files)
        source_files["project.json"] = json.dumps(project, ensure_ascii=False).encode("utf-8")
        original = v3.build_files(manifest, source_files)
        _, original_files = v3.unpack(original)

        style = original_files["web/style.css"]
        edited_manifest, edited_files, exported, _ = project_ops.edit_package(original, [{
            "op": "write",
            "path": "web/style.css",
            "encoding": "utf-8",
            "content": style.decode("utf-8") + "\n/* re-edit fixture */\n",
            "expectedSha256": hashlib.sha256(style).hexdigest(),
        }])
        imported_manifest, imported_files = v3.unpack(exported)

        self.assertNotIn("fullCanvas", edited_manifest)
        self.assertNotIn("fullCanvas", imported_manifest)
        self.assertNotIn("slots", json.loads(imported_files["project.json"]))
        self.assertEqual(edited_files, imported_files)
        self.assertEqual(edited_manifest, imported_manifest)
        self.assertTrue(imported_files["web/style.css"].endswith(b"/* re-edit fixture */\n"))


if __name__ == "__main__":
    unittest.main()

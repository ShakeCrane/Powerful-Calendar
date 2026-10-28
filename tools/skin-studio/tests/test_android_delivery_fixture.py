import hashlib
import json
import sys
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


class AndroidDeliveryFixtureTest(unittest.TestCase):
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

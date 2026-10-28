import json
import sys
import unittest
from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent
STUDIO_DIR = TEST_DIR.parent
sys.path.insert(0, str(STUDIO_DIR))
import runtime_package_v3 as v3


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


if __name__ == "__main__":
    unittest.main()

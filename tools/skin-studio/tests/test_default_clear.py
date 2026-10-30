"""Factory Clear default must be one bundle for Studio and the shipped APK."""
import base64
import hashlib
import json
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.request import urlopen
from zipfile import ZipFile

STUDIO=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(STUDIO))
from skin_studio import Handler,ThreadingHTTPServer
from build_default_clear import ASSET,expected_bytes,verify_apk_asset
from runtime_package_v3 import unpack

class DefaultClearSingleSourceTest(unittest.TestCase):
    def test_canonical_checkout_keeps_the_bundled_line_endings(self):
        repo = STUDIO.parents[1]
        files = {
            "tools/skin-studio/default-clear/source/web/app.js": "lf",
            "tools/skin-studio/default-clear/source/web/edit.js": "lf",
            "tools/skin-studio/default-clear/source/web/clear-design.css": "crlf",
        }
        for name, eol in files.items():
            with self.subTest(path=name):
                output = subprocess.check_output(
                    ["git", "check-attr", "eol", "--", name], cwd=repo, text=True)
                self.assertTrue(output.rstrip().endswith(": eol: " + eol), output)

    def test_apk_asset_is_reproducible_and_is_complete_v3_skin(self):
        original=ASSET.read_bytes()
        self.assertEqual(original,expected_bytes())
        self.assertEqual(original,expected_bytes(),"v3 builder must not encode wall clock timestamps")
        manifest,files=unpack(original)
        self.assertEqual(manifest["id"],"powercal-clear-default")
        self.assertEqual(manifest["schemaVersion"],3)
        self.assertTrue(manifest["fullCanvas"])
        self.assertIn("web/clear-design.css",files)
        self.assertIn("web/fonts/InterVariable.woff2",files)
        self.assertIn("web/fonts/LICENSE-Inter.txt",files)
        self.assertIn("web/app.js",files)
        self.assertIn("web/title-folding.js",files)
        self.assertIn("project.json",files)
        self.assertIn(b"powercal-port",files["web/app.js"])
        self.assertIn(b'<script src="title-folding.js"></script>',files["web/index.html"])
        self.assertIn(b"PowerCalTitleFolding",files["web/title-folding.js"])
        self.assertNotIn(b"https://",files["web/index.html"])
        self.assertLess(len(original),10_000_000)

    def test_clear_settings_has_host_confirmed_installed_skin_selection(self):
        _,files=unpack(expected_bytes())
        html=files["web/index.html"].decode("utf-8")
        script=files["web/app.js"].decode("utf-8")
        style=files["web/style.css"].decode("utf-8")
        self.assertIn('id="skinChoices"',html)
        self.assertIn('button.dataset.action="selectSkin"',script)
        self.assertIn('send(name,{id:button.dataset.skinId})',script)
        self.assertIn('.skin-choice-list',style)
        self.assertIn('item.name',script)
        self.assertNotIn('innerHTML',script)

    def test_compiled_apk_clear_asset_verifier_checks_exact_bytes(self):
        expected=expected_bytes()
        asset_name="assets/powercal-clear-default.pcskin"
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/"fixture.apk"
            with ZipFile(apk,"w") as archive:
                archive.writestr(asset_name,expected)
            self.assertEqual(hashlib.sha256(expected).hexdigest(),
                             verify_apk_asset(apk,expected))

            with ZipFile(apk,"w") as archive:
                archive.writestr(asset_name,b"x"*len(expected))
            with self.assertRaisesRegex(ValueError,"differs from canonical"):
                verify_apk_asset(apk,expected)

            with ZipFile(apk,"w") as archive:
                archive.writestr("assets/other.pcskin",expected)
            with self.assertRaisesRegex(ValueError,"exactly one"):
                verify_apk_asset(apk,expected)

    def test_studio_starter_reads_exact_same_apk_bundle(self):
        server=ThreadingHTTPServer(("127.0.0.1",0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True)
        thread.start()
        try:
            with urlopen(f"http://127.0.0.1:{server.server_port}/api/template-v3") as answer:
                project=json.loads(answer.read())
            asset_manifest,files=unpack(ASSET.read_bytes())
            self.assertEqual(project["manifest"],asset_manifest)
            self.assertEqual(set(project["files"]),set(files))
            for name,blob in files.items():
                self.assertEqual(base64.b64decode(project["files"][name]),blob,name)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)

if __name__=="__main__":
    unittest.main()

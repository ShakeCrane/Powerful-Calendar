import json, sys, tempfile, unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import runtime_package_v3 as v3

class RuntimeV3Tests(unittest.TestCase):
    def setUp(self):
        self.example = ROOT / "examples" / "v3"
        self.manifest = json.loads((self.example / "manifest.json").read_text("utf8"))

    def test_self_contained_round_trip(self):
        blob = v3.build(self.manifest, self.example / "source")
        manifest, files = v3.unpack(blob)
        self.assertEqual(3, manifest["schemaVersion"])
        self.assertTrue(set(v3.WEB_REQUIRED) <= set(files))
        self.assertEqual(set(files), set(manifest["sha256"]))

    def test_tamper_and_remote_source_rejected(self):
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        files["web/index.html"] = b'<script src="https://bad.test/x.js"></script>'
        with self.assertRaises(ValueError):
            v3.validate_manifest(self.manifest, files, verify_hash=False)

if __name__ == "__main__": unittest.main()

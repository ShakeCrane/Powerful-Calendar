import io, json, sys, tempfile, unittest
from pathlib import Path
from zipfile import ZipFile

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

    def test_unknown_manifest_metadata_is_preserved_and_capabilities_stay_closed(self):
        manifest = dict(self.manifest)
        metadata = {"publisher": "fixture", "custom": {"labels": ["preserve", "only"]},
                    "claims": ["host.action"]}
        manifest["x-vendor-metadata"] = metadata
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}

        blob = v3.build_files(manifest, files)
        imported_manifest, imported_files = v3.unpack(blob)
        self.assertEqual(metadata, imported_manifest["x-vendor-metadata"])
        self.assertEqual(files, imported_files)

        invalid_capabilities = dict(manifest)
        invalid_capabilities["capabilities"] = ["host.delete"]
        with self.assertRaisesRegex(ValueError, "unsupported bridge capability"):
            v3.validate_manifest(invalid_capabilities, files, verify_hash=False)

    def test_tamper_and_remote_source_rejected(self):
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        files["web/index.html"] = b'<script src="https://bad.test/x.js"></script>'
        with self.assertRaises(ValueError):
            v3.validate_manifest(self.manifest, files, verify_hash=False)

    def test_missing_local_resource_and_package_escape_are_rejected(self):
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        files["web/style.css"] += b'\n.probe{background-image:url("../assets/missing.png")}\n'
        with self.assertRaisesRegex(ValueError, "referenced resource is missing"):
            v3.build_files(self.manifest, files)
        files["web/style.css"] = files["web/style.css"].replace(
            b"../assets/missing.png", b"../../outside.png")
        with self.assertRaisesRegex(ValueError, "referenced resource is missing or escapes package"):
            v3.build_files(self.manifest, files)

    def test_missing_srcset_and_quoted_css_import_resources_are_rejected(self):
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        files["web/index.html"] += b'<img srcset="../assets/missing-small.png 1x, ../assets/missing-large.png 2x">'
        with self.assertRaisesRegex(ValueError, "referenced resource is missing"):
            v3.build_files(self.manifest, files)

        files["web/index.html"] = files["web/index.html"].replace(
            b'<img srcset="../assets/missing-small.png 1x, ../assets/missing-large.png 2x">', b"")
        files["web/style.css"] += b'\n@import "../assets/missing-theme.css";\n'
        with self.assertRaisesRegex(ValueError, "referenced resource is missing"):
            v3.build_files(self.manifest, files)

    def test_external_css_resources_are_rejected_even_when_local_files_are_optional(self):
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        files["web/style.css"] += b'\n@import url("https://bad.test/theme.css");\n'
        with self.assertRaisesRegex(ValueError, "remote resources are forbidden"):
            v3.build_files(self.manifest, files)

        files["web/style.css"] = files["web/style.css"].replace(
            b'@import url("https://bad.test/theme.css");', b'')
        files["web/style.css"] += b'\n.probe{background:url(//bad.test/image.png)}\n'
        with self.assertRaisesRegex(ValueError, "remote resources are forbidden"):
            v3.build_files(self.manifest, files)

    def test_v3_zip_entry_path_traversal_is_rejected(self):
        source = v3.build(self.manifest, self.example / "source")
        out = io.BytesIO()
        with ZipFile(io.BytesIO(source)) as original, ZipFile(out, "w") as malformed:
            for name in original.namelist():
                malformed.writestr(name, original.read(name))
            malformed.writestr("web/../../outside.js", b"window.bad=true;")
        with self.assertRaisesRegex(ValueError, "unsafe ZIP entry"):
            v3.unpack(out.getvalue())

    def test_missing_capabilities_do_not_grant_host_access(self):
        manifest = dict(self.manifest)
        manifest.pop("capabilities")
        files = {p.relative_to(self.example / "source").as_posix(): p.read_bytes()
                 for p in (self.example / "source").rglob("*") if p.is_file()}
        checked = v3.validate_manifest(manifest, files, verify_hash=False)
        self.assertEqual([], checked["capabilities"])
        built, _ = v3.unpack(v3.build_files(manifest, files))
        self.assertEqual([], built["capabilities"])
        # Android accepts an absent declaration as no capabilities; importing
        # an existing ZIP must not silently expand it either.
        source = v3.build_files(self.manifest, files)
        out = io.BytesIO()
        with ZipFile(io.BytesIO(source)) as old, ZipFile(out, "w") as new:
            for name in old.namelist():
                data = old.read(name)
                if name == "manifest.json":
                    document = json.loads(data)
                    document.pop("capabilities")
                    data = json.dumps(document).encode("utf8")
                new.writestr(name, data)
        imported, _ = v3.unpack(out.getvalue())
        self.assertEqual([], imported["capabilities"])

if __name__ == "__main__": unittest.main()

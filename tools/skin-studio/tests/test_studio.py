import io
import json
from pathlib import Path
import sys
import unittest
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from skin_studio import SkinError, pack, unpack, validated, dimensions


def manifest():
    return {"format": "powercal-skin", "schemaVersion": 1,
            "id": "studio-test", "name": "Test Studio",
            "light": {"primary": "#234567", "onPrimary": "#FFFFFF"},
            "dark": {"primary": "#DDCCBB", "onPrimary": "#121212"},
            "layout": {"fontScale": 1.1},
            "imagesLight": {}, "imagesDark": {}, "sha256": {}}


def png():
    import struct
    import zlib
    signature = b"\x89PNG\r\n\x1a\n"
    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data +
                struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff))
    return (signature + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\xff"))
            + chunk(b"IEND", b""))


class SkinStudioTest(unittest.TestCase):
    def test_archive_round_trip(self):
        src = manifest()
        data = pack(src, {})
        got, assets = unpack(data)
        self.assertEqual(got["id"], src["id"])
        self.assertEqual(got["layout"]["fontScale"], 1.1)
        self.assertEqual(assets, {})
        self.assertEqual(got["sha256"], {})

    def test_picture_checksum_and_dimensions(self):
        src = manifest()
        src["imagesLight"]["BRAND_MARK"] = "images/brand.png"
        raw = png()
        data = pack(src, {"images/brand.png": raw})
        got, assets = unpack(data)
        self.assertEqual(dimensions(raw, "images/brand.png"), (1, 1))
        self.assertEqual(len(got["sha256"]["images/brand.png"]), 64)
        self.assertEqual(assets["images/brand.png"], raw)

    def test_reject_semantics_and_invalid_geometry(self):
        for m in [
            {**manifest(), "schemaVersion": 2},
            {**manifest(), "id": "powercal-default"},
            {**manifest(), "light": {"primary": "#FFFFFF"}},
            {**manifest(), "layout": {"cardRadiusDp": 200}},
            {**manifest(), "script": "alert(1)"},
            {**manifest(), "name": ""},
        ]:
            with self.subTest(m=m):
                with self.assertRaises(SkinError):
                    pack(m, {})

    def test_reject_unlisted_and_invalid_images(self):
        m = manifest()
        with self.assertRaises(SkinError):
            pack(m, {"images/surprise.png": png()})
        m["imagesDark"]["AI_AVATAR"] = "images/avatar.png"
        with self.assertRaises(SkinError):
            pack(m, {"images/avatar.png": b"not-png"})

    def test_reject_hash_tampering(self):
        m = manifest()
        m["imagesLight"]["BRAND_MARK"] = "images/test.png"
        data = pack(m, {"images/test.png": png()})
        with ZipFile(io.BytesIO(data)) as z:
            manifest_from_zip = json.loads(z.read("manifest.json"))
        manifest_from_zip["sha256"]["images/test.png"] = "0" * 64
        with self.assertRaises(SkinError):
            validated(manifest_from_zip, {"images/test.png": png()})

    def test_reject_zip_traversal_and_duplicate(self):
        for entries in [
            [("../evil.txt", b"no"), ("manifest.json", b"{}")],
            [("manifest.json", b"{}"), ("manifest.json", b"{}")],
        ]:
            with io.BytesIO() as output:
                with ZipFile(output, "w") as zipfile:
                    for k, v in entries:
                        zipfile.writestr(k, v)
                with self.assertRaises(SkinError):
                    unpack(output.getvalue())


if __name__ == "__main__":
    unittest.main()

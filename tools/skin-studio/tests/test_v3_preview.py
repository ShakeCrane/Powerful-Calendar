"""Exact-file web preview must agree byte-for-byte with the exported v3 pack."""
import base64
import json
import sys
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from skin_studio import Handler, ThreadingHTTPServer
import runtime_package_v3 as v3


class PreviewParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.base = "http://127.0.0.1:" + str(cls.server.server_port)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def get(self, route):
        with urlopen(self.base + route) as response:
            return response.read()

    def post(self, route, payload):
        body = json.dumps(payload).encode()
        request = Request(self.base + route, body, {"Content-Type": "application/json"},
                          method="POST")
        with urlopen(request) as response:
            return response.read()

    def test_preview_and_zip_share_exact_web_bytes(self):
        obj = json.loads(self.get("/api/template-v3"))
        obj["files"]["assets/graphic.png"] = base64.b64encode(b"\x89PNG\r\nsource-bytes").decode()
        asset_url = "../assets/graphic.png"
        html = base64.b64decode(obj["files"]["web/index.html"]).decode()
        html = html.replace("</body>", '<img id="decoration" src="' + asset_url + '"></body>')
        obj["files"]["web/index.html"] = base64.b64encode(html.encode()).decode()

        endpoint = json.loads(self.post("/api/preview-v3", obj))["url"]
        self.assertTrue(endpoint.startswith("/preview/"))
        for name in ("web/index.html", "web/style.css", "web/app.js", "assets/graphic.png"):
            raw = base64.b64decode(obj["files"][name])
            actual = self.get(endpoint.replace("web/index.html", name))
            self.assertEqual(raw, actual, name)
        imported_manifest, exported_files = v3.unpack(self.post("/api/export-v3", obj))
        self.assertEqual(3, imported_manifest["schemaVersion"])
        for name, raw in exported_files.items():
            self.assertEqual(base64.b64decode(obj["files"][name]), raw, name)

    def test_block_traversal_and_missing_token(self):
        obj = json.loads(self.get("/api/template-v3"))
        endpoint = json.loads(self.post("/api/preview-v3", obj))["url"]
        for invalid in (endpoint.replace("web/index.html", "web/../index.html"),
                        "/preview/" + "0" * 32 + "/web/index.html",
                        endpoint.replace("web/index.html", "assets/notfound.png")):
            with self.subTest(invalid=invalid), self.assertRaises(HTTPError) as got:
                self.get(invalid)
            self.assertEqual(404, got.exception.code)

    def test_oversized_compressed_source_not_exported(self):
        obj = json.loads(self.get("/api/template-v3"))
        obj["files"]["web/style.css"] = base64.b64encode(b"x" * 650_000).decode()
        for endpoint in ("/api/preview-v3", "/api/export-v3"):
            with self.subTest(endpoint=endpoint), self.assertRaises(HTTPError) as got:
                self.post(endpoint, obj)
            self.assertEqual(400, got.exception.code)


if __name__ == "__main__":
    unittest.main()

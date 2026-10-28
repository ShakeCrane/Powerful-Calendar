"""Exact-file web preview must agree byte-for-byte with the exported v3 pack."""
import base64
import json
import sys
import threading
import unittest
from pathlib import Path
from urllib.parse import urlsplit
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from skin_studio import Handler, ThreadingHTTPServer
import runtime_package_v3 as v3
from studio_v3_preview import PREVIEW_CSP


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
        url, headers = self._request_target(route, {})
        with urlopen(Request(url, headers=headers)) as response:
            return response.read()

    def get_response(self, route, headers=None):
        url, request_headers = self._request_target(route, headers or {})
        request = Request(url, headers=request_headers)
        try:
            with urlopen(request) as response:
                return response.status, response.headers, response.read()
        except HTTPError as error:
            return error.code, error.headers, error.read()

    def _request_target(self, route, headers):
        parsed = urlsplit(route)
        if parsed.scheme and parsed.netloc:
            headers.setdefault("Host", parsed.netloc)
            route = parsed.path + (("?" + parsed.query) if parsed.query else "")
        return self.base + route, headers

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
        endpoint_parts = urlsplit(endpoint)
        self.assertRegex(endpoint_parts.hostname or "", r"^[a-f0-9]{32}\.localhost$")
        self.assertTrue(endpoint_parts.path.startswith("/preview/"))
        for name in ("web/index.html", "web/style.css", "web/app.js", "assets/graphic.png"):
            raw = base64.b64decode(obj["files"][name])
            actual = self.get(endpoint.replace("web/index.html", name))
            self.assertEqual(raw, actual, name)
        imported_manifest, exported_files = v3.unpack(self.post("/api/export-v3", obj))
        self.assertEqual(3, imported_manifest["schemaVersion"])
        for name, raw in exported_files.items():
            self.assertEqual(base64.b64decode(obj["files"][name]), raw, name)

    def test_import_preview_export_reimport_preserves_opaque_manifest_metadata(self):
        template = json.loads(self.get("/api/template-v3"))
        metadata = {"publisher": "fixture", "custom": {"labels": ["preserve", "only"]},
                    "claims": ["host.action"]}
        template["manifest"]["x-vendor-metadata"] = metadata
        source_files = {path: base64.b64decode(data)
                        for path, data in template["files"].items()}
        source_package = v3.build_files(template["manifest"], source_files)
        import_request = Request(self.base + "/api/import-v3", source_package,
                                 {"Content-Type": "application/octet-stream"}, method="POST")
        with urlopen(import_request) as response:
            imported = json.loads(response.read())
        self.assertEqual(metadata, imported["manifest"]["x-vendor-metadata"])

        edited = {"manifest": imported["manifest"], "files": imported["files"]}
        css = base64.b64decode(edited["files"]["web/style.css"]) + b"\n/* retained edit */\n"
        edited["files"]["web/style.css"] = base64.b64encode(css).decode()
        preview_url = json.loads(self.post("/api/preview-v3", edited))["url"]
        self.assertEqual(css, self.get(preview_url.replace("web/index.html", "web/style.css")))

        exported = self.post("/api/export-v3", edited)
        exported_manifest, exported_files = v3.unpack(exported)
        self.assertEqual(metadata, exported_manifest["x-vendor-metadata"])
        self.assertEqual(css, exported_files["web/style.css"])
        reimport_request = Request(self.base + "/api/import-v3", exported,
                                   {"Content-Type": "application/octet-stream"}, method="POST")
        with urlopen(reimport_request) as response:
            reimported = json.loads(response.read())
        self.assertEqual(metadata, reimported["manifest"]["x-vendor-metadata"])
        self.assertEqual(exported_files,
                         {path: base64.b64decode(data)
                          for path, data in reimported["files"].items()})

    def test_block_traversal_and_missing_token(self):
        obj = json.loads(self.get("/api/template-v3"))
        endpoint = json.loads(self.post("/api/preview-v3", obj))["url"]
        for invalid in (endpoint.replace("web/index.html", "web/../index.html"),
                        "/preview/" + "0" * 32 + "/web/index.html",
                        endpoint.replace("web/index.html", "assets/notfound.png")):
            with self.subTest(invalid=invalid), self.assertRaises(HTTPError) as got:
                self.get(invalid)
            self.assertEqual(404, got.exception.code)

    def test_root_relative_resources_resolve_from_preview_token_host_without_referer(self):
        obj = json.loads(self.get("/api/template-v3"))
        root_bytes = b"root-relative-package-resource"
        font_bytes = b"opaque-origin-font-resource"
        obj["files"]["assets/root.png"] = base64.b64encode(root_bytes).decode()
        obj["files"]["web/fonts/Probe.WOFF2"] = base64.b64encode(font_bytes).decode()
        endpoint = json.loads(self.post("/api/preview-v3", obj))["url"]
        preview_host = urlsplit(endpoint).netloc

        status, headers, body = self.get_response("/assets/root.png", {"Host": preview_host})
        self.assertEqual(200, status)
        self.assertEqual(root_bytes, body)
        self.assertEqual("image/png", headers.get_content_type())
        self.assertEqual(PREVIEW_CSP, headers.get("Content-Security-Policy"))
        self.assertEqual("nosniff", headers.get("X-Content-Type-Options"))

        status, headers, body = self.get_response(
            "/web/fonts/Probe.WOFF2", {"Host": preview_host, "Origin": "null"})
        self.assertEqual(200, status)
        self.assertEqual(font_bytes, body)
        self.assertEqual("font/woff2", headers.get_content_type())
        self.assertEqual("null", headers.get("Access-Control-Allow-Origin"))
        self.assertEqual("Origin", headers.get("Vary"))

        # Android resolves an empty URI path to the configured entrypoint.
        status, _, body = self.get_response("/", {"Host": preview_host})
        self.assertEqual(200, status)
        self.assertEqual(base64.b64decode(obj["files"]["web/index.html"]), body)

        # Android Uri.path decodes URL escapes before looking up the package map.
        for escaped in ("/assets/root%2Epng", "/assets%2Froot.png"):
            status, _, body = self.get_response(escaped, {"Host": preview_host})
            self.assertEqual(200, status, escaped)
            self.assertEqual(root_bytes, body, escaped)

        for traversal in ("/assets/%2E%2E/web/index.html",
                          "/assets/%252E%252E/web/index.html"):
            status, _, body = self.get_response(traversal, {"Host": preview_host})
            self.assertEqual(404, status, traversal)
            self.assertEqual(b"", body, traversal)

        for headers_in in ({"Host": urlsplit(self.base).netloc},
                           {"Host": urlsplit(self.base).netloc, "Referer": self.base + "/"},
                           {"Host": urlsplit(self.base).netloc,
                            "Referer": "http://example.invalid" + urlsplit(endpoint).path}):
            with self.subTest(headers=headers_in):
                status, _, body = self.get_response("/assets/root.png", headers_in)
                self.assertEqual(404, status)
                self.assertEqual(b"", body)

    def test_preview_matches_android_mime_query_and_missing_resource_rules(self):
        obj = json.loads(self.get("/api/template-v3"))
        obj["files"]["web/App.JS"] = base64.b64encode(b"window.parity = true;").decode()
        obj["files"]["web/fonts/Probe.WOFF2"] = base64.b64encode(b"font-bytes").decode()
        endpoint = json.loads(self.post("/api/preview-v3", obj))["url"]
        endpoint_parts = urlsplit(endpoint)
        token = (endpoint_parts.hostname or "").split(".", 1)[0]

        for path, expected in (("/preview/" + token + "/web/App.JS",
                                "application/javascript"),
                               ("/preview/" + token +
                                "/web/fonts/Probe.WOFF2", "font/woff2")):
            headers_in = {"Host": endpoint_parts.netloc}
            if expected.startswith("font/"):
                headers_in["Origin"] = "null"
            status, headers, _ = self.get_response(path, headers_in)
            self.assertEqual(200, status)
            self.assertEqual(expected, headers.get_content_type())
            self.assertEqual(PREVIEW_CSP, headers.get("Content-Security-Policy"))
            if expected.startswith("font/"):
                self.assertEqual("null", headers.get("Access-Control-Allow-Origin"))
                self.assertEqual("Origin", headers.get("Vary"))
            else:
                self.assertIsNone(headers.get("Access-Control-Allow-Origin"))

        for path, request_headers in (("/assets/not-found.png?cache=1",
                                       {"Host": endpoint_parts.netloc}),
                                      (endpoint.replace("web/index.html", "web/App.JS") + "?cache=1", {})):
            status, headers, body = self.get_response(path, request_headers)
            self.assertEqual(403, status)
            self.assertEqual(b"", body)
            self.assertEqual("text/plain", headers.get_content_type())
            self.assertEqual(PREVIEW_CSP, headers.get("Content-Security-Policy"))

        status, headers, body = self.get_response(
            "/assets/missing.png", {"Host": endpoint_parts.netloc})
        self.assertEqual(404, status)
        self.assertEqual(b"", body)
        self.assertEqual("text/plain", headers.get_content_type())
        self.assertEqual(PREVIEW_CSP, headers.get("Content-Security-Policy"))

    def test_oversized_compressed_source_not_exported(self):
        obj = json.loads(self.get("/api/template-v3"))
        obj["files"]["web/style.css"] = base64.b64encode(b"x" * 650_000).decode()
        for endpoint in ("/api/preview-v3", "/api/export-v3"):
            with self.subTest(endpoint=endpoint), self.assertRaises(HTTPError) as got:
                self.post(endpoint, obj)
            self.assertEqual(400, got.exception.code)


if __name__ == "__main__":
    unittest.main()

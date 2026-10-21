import json
from pathlib import Path
import sys
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from skin_studio import Handler, ThreadingHTTPServer, unpack


class LocalHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.url = "http://127.0.0.1:" + str(cls.server.server_port)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def test_html_static_files(self):
        for path, expected in [("/", b"Skin Studio"), ("/app.js", b"SCENES"),
                               ("/style.css", b".phone")]:
            with self.subTest(path=path), urlopen(self.url + path) as response:
                self.assertEqual(response.status, 200)
                self.assertIn(expected, response.read())

    def test_validate_and_export_roundtrip(self):
        src = {"manifest": {"format": "powercal-skin", "schemaVersion": 1,
                            "id": "web-test", "name": "Browser Test",
                            "light": {}, "dark": {}, "layout": {},
                            "imagesLight": {}, "imagesDark": {}, "sha256": {}},
               "images": {}}
        body = json.dumps(src).encode()
        for endpoint in ("/api/validate", "/api/export"):
            req = Request(self.url + endpoint, body,
                          {"Content-Type": "application/json"}, method="POST")
            with urlopen(req) as response:
                data = response.read()
                if endpoint.endswith("validate"):
                    self.assertTrue(json.loads(data)["ok"])
                else:
                    self.assertEqual(unpack(data)[0]["id"], "web-test")

    def test_refuse_cross_origin_and_unknown_route(self):
        data = b"{}"
        request = Request(self.url + "/api/export", data,
                          {"Content-Type": "application/json",
                           "Origin": "https://evil.example"}, method="POST")
        with self.assertRaises(HTTPError) as got:
            urlopen(request)
        self.assertEqual(got.exception.code, 403)
        with self.assertRaises(HTTPError) as got:
            urlopen(self.url + "/unknown")
        self.assertEqual(got.exception.code, 404)


if __name__ == "__main__":
    unittest.main()

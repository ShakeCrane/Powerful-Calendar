import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

STUDIO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STUDIO))

from build_default_clear import ASSET
from project_ops_v3 import audit_bridge_package
from runtime_package_v3 import build_files, unpack


class BridgeScriptAuditV3Test(unittest.TestCase):
    def make_package(self, html, app_js, capabilities=None):
        manifest, files = unpack(ASSET.read_bytes())
        manifest["id"] = "bridge-script-audit-test"
        manifest["name"] = "Bridge Script Audit Test"
        if capabilities is not None:
            manifest["capabilities"] = capabilities
        for path in list(files):
            if (path.startswith("web/") and
                    Path(path).suffix.lower() in {".js", ".mjs", ".cjs"}):
                del files[path]
        files["web/index.html"] = html.encode("utf-8")
        files["web/app.js"] = app_js.encode("utf-8")
        files["web/style.css"] = b""
        return build_files(manifest, files)

    def test_audit_indexes_literal_calls_and_marks_dynamic_sites_for_review(self):
        html = (
            "<!doctype html>\n<html><head><script src=\"app.js\"></script>"
            "</head><body>\n<script>\n"
            "send(\"today\");\n"
            "perform(\"deck.flush\");\n"
            "send(actionName);\n"
            "perform(resolveCommand());\n"
            "// send(\"commentOnly\")\n"
            "const note = 'perform(\"stringOnly\")';\n"
            "const generated = `${send(\"month\")}`;\n"
            "</script></body></html>"
        )
        app_js = (
            'send("month");\nperform("editor.save", {sessionId});\n'
            'port.postMessage("raw");\n'
            'window.PowerCalSkin?.sendCommand?.("dynamic");\n'
            'analytics.send("notABridgeAction");\n'
            'window.postMessage("notTheHostPort");\n'
            'if (ready) /send("regexIsNotACall")/.test(value);\n'
            'if (ready) {} /send("regexAfterBlock")/.test(value);\n'
            'const methods = {send(name) {}, perform(name) {}};\n'
        )
        source = self.make_package(html, app_js)

        audit = audit_bridge_package(source)

        self.assertTrue(audit["ok"])
        self.assertEqual(audit["requiredCapabilities"], ["host.action"])
        bindings = audit["scriptBindings"]
        literal_names = {(item["api"], item["name"]) for item in bindings
                         if item["name"] is not None}
        self.assertEqual(literal_names, {
            ("action", "today"), ("action", "month"),
            ("command", "deck.flush"), ("command", "editor.save"),
        })
        inline_today = next(item for item in bindings
                            if item["path"] == "web/index.html" and
                            item["name"] == "today")
        self.assertEqual(inline_today["line"], 4)
        inline_names = {item["name"] for item in bindings
                        if item["sourceKind"] == "inline"}
        self.assertNotIn("month", inline_names)
        self.assertNotIn("commentOnly", {item["name"] for item in bindings})
        self.assertNotIn("stringOnly", {item["name"] for item in bindings})
        self.assertNotIn("notABridgeAction", {item["name"] for item in bindings})
        self.assertNotIn("regexIsNotACall", {item["name"] for item in bindings})
        self.assertNotIn("regexAfterBlock", {item["name"] for item in bindings})
        self.assertEqual(len(bindings), 6)
        self.assertEqual(audit["unknownScriptBindings"], [])
        self.assertEqual({item["status"] for item in bindings if item["name"] is None},
                         {"REVIEW_REQUIRED_DYNAMIC_NAME"})
        self.assertTrue(all(item["payloadValidation"] == "NOT_VALIDATED"
                            for item in bindings if item["name"] is not None))
        self.assertIn("#/$defs/actionPayloads/today",
                      {item["payloadSchemaRef"] for item in bindings})
        self.assertIn("#/$defs/commandPayloads/deck.flush",
                      {item["payloadSchemaRef"] for item in bindings})
        self.assertTrue(audit["requiresSourceReview"])
        self.assertTrue(audit["reviewRequiredExpressions"])
        self.assertIn("template", audit["reviewRequiredExpressions"][0]["reason"])
        self.assertEqual(len(audit["scriptTransportCalls"]), 2)
        self.assertEqual({item["call"] for item in audit["scriptTransportCalls"]},
                         {"postMessage", "sendCommand"})
        self.assertTrue(all(item["status"] == "REVIEW_REQUIRED_RAW_TRANSPORT"
                            for item in audit["scriptTransportCalls"]))

    def test_inline_scripts_ignore_html_comments_and_decode_entity_encoded_types(self):
        html = (
            '<script src="app.js"></script>\n'
            '<!-- <script>send("commentOnlyFake")</script> -->\n'
            '<script type="text&#x2F;javascript; charset=utf-8"/>\n'
            'send("today");\n'
            '</script>'
        )
        source = self.make_package(html, "")

        audit = audit_bridge_package(source)

        self.assertEqual([item["name"] for item in audit["scriptBindings"]], ["today"])
        self.assertEqual(audit["unknownScriptBindings"], [])
        self.assertEqual(audit["scriptBindings"][0]["line"], 4)

    def test_template_interpolation_scan_is_bounded_and_requires_review(self):
        html = '<!doctype html><html><body><script src="app.js"></script></body></html>'
        large_template = "`" + "${plainValue}" * 5000 + '${send("today")}' + "`"
        source = self.make_package(html, f"const template = {large_template};")

        audit = audit_bridge_package(source)

        self.assertTrue(audit["requiresSourceReview"])
        self.assertEqual(audit["scriptBindings"], [])
        self.assertEqual(len(audit["reviewRequiredExpressions"]), 1)
        self.assertEqual(audit["reviewRequiredExpressions"][0]["status"],
                         "REVIEW_REQUIRED_TEMPLATE_EXPRESSION")

    def test_template_text_outside_interpolation_is_not_a_bridge_call(self):
        html = '<!doctype html><html><body><script src="app.js"></script></body></html>'
        source = self.make_package(
            html, 'const template = `${value} text send("notAnExpression")`;')

        audit = audit_bridge_package(source)

        self.assertEqual(audit["scriptBindings"], [])
        self.assertEqual(audit["reviewRequiredExpressions"], [])

    def test_inline_event_handler_calls_are_indexed_and_require_capability(self):
        html = (
            '<!doctype html><html><body><script src="app.js"></script>\n'
            '<button onclick="send(&quot;today&quot;)">today</button>\n'
            '<button onclick="perform(&quot;editor.notReal&quot;)">bad</button>'
            '</body></html>'
        )
        manifest, _ = unpack(ASSET.read_bytes())
        capabilities = [item for item in manifest["capabilities"]
                        if item != "host.action"]
        source = self.make_package(html, "", capabilities)

        audit = audit_bridge_package(source)

        self.assertFalse(audit["ok"])
        self.assertEqual(audit["requiredCapabilities"], ["host.action"])
        self.assertEqual(audit["missingCapabilities"], ["host.action"])
        self.assertEqual(len(audit["inlineEventHandlers"]), 2)
        today = next(item for item in audit["scriptBindings"]
                     if item["name"] == "today")
        self.assertEqual(today["sourceKind"], "html-event-handler")
        self.assertEqual(today["attribute"], "onclick")
        self.assertEqual(today["line"], 2)
        self.assertEqual({item["name"] for item in audit["unknownScriptBindings"]},
                         {"editor.notReal"})

    def test_unknown_script_names_and_missing_capability_fail_cli_as_json(self):
        html = '<!doctype html><html><body><script src="app.js"></script></body></html>'
        manifest, _ = unpack(ASSET.read_bytes())
        capabilities = [item for item in manifest["capabilities"]
                        if item != "host.action"]
        source = self.make_package(
            html, 'send("inventedAction");\nperform("editor.notReal");\n', capabilities)
        audit = audit_bridge_package(source)

        self.assertFalse(audit["ok"])
        self.assertEqual(audit["missingCapabilities"], ["host.action"])
        self.assertEqual({item["name"] for item in audit["unknownScriptBindings"]},
                         {"inventedAction", "editor.notReal"})

        with tempfile.TemporaryDirectory() as temp:
            package_path = Path(temp) / "invalid-script-audit.pcskin"
            package_path.write_bytes(source)
            result = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"),
                 "audit-bridge-v3", str(package_path)],
                cwd=STUDIO.parents[1], text=True, encoding="utf-8",
                capture_output=True, check=False)

        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        receipt = json.loads(result.stderr)
        self.assertFalse(receipt["ok"])
        self.assertEqual(receipt["unknownScriptBindings"], audit["unknownScriptBindings"])


if __name__ == "__main__":
    unittest.main()

"""Exercise an agent-style, machine-readable v3 skin authoring workflow."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

STUDIO = Path(__file__).resolve().parents[1]
ROOT = STUDIO.parents[1]
sys.path.insert(0, str(STUDIO))

from build_default_clear import ASSET
from runtime_package_v3 import unpack


class AiSkinWorkflowV3Test(unittest.TestCase):
    def cli_json(self, *args):
        result = subprocess.run(
            [sys.executable, str(STUDIO / "skin_studio.py"), *map(str, args)],
            cwd=ROOT, text=True, encoding="utf-8", capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        return json.loads(result.stdout)

    def test_clear_source_to_agent_authored_skin_package(self):
        evidence_root = ROOT / ".local" / "android-test"
        evidence_root.mkdir(parents=True, exist_ok=True)
        original_asset = ASSET.read_bytes()
        original_asset_sha = hashlib.sha256(original_asset).hexdigest()

        with tempfile.TemporaryDirectory(prefix="ai-skin-workflow-", dir=evidence_root) as temp:
            work = Path(temp)
            source_package = work / "clear-source.pcskin"
            output_package = work / "agent-authored.pcskin"
            manifest_path = STUDIO / "default-clear" / "manifest.json"
            source_dir = STUDIO / "default-clear" / "source"

            build = self.cli_json("build-v3", "--manifest", manifest_path,
                                  "--source-dir", source_dir,
                                  "--out", source_package, "--json")
            self.assertEqual(build["command"], "build-v3")
            self.assertTrue(build["ok"])
            self.assertEqual(build["packageSha256"], original_asset_sha)

            inspected = self.cli_json("inspect-v3", source_package)
            self.assertEqual(inspected["command"], "inspect-v3")
            self.assertEqual(inspected["packageSha256"], build["packageSha256"])
            self.assertEqual(inspected["manifest"]["capabilities"],
                             ["host.read", "host.action"])

            analysis = self.cli_json("analyze-v3", source_package)
            page = next(item for item in analysis["pages"]
                        if item["path"] == "web/index.html")
            app = next(node for node in page["components"] if node["id"] == "app")
            stylesheet = self.cli_json("read-v3", "--file", source_package,
                                        "--path", "web/style.css")
            self.assertEqual(stylesheet["encoding"], "utf-8")
            css_index = next(item for item in inspected["files"]
                             if item["path"] == "web/style.css")
            self.assertEqual(stylesheet["sha256"], css_index["sha256"])

            operations_path = work / "operations.json"
            operations_path.write_text(json.dumps({"operations": [
                {
                    "op": "component-insert",
                    "path": page["path"],
                    "expectedSha256": page["sourceSha256"],
                    "targetNodeId": app["nodeId"],
                    "position": "append",
                    "html": ('<section id="agent-workflow-panel" '
                             'data-component="agent-workflow"><h2>Agent authored</h2>'
                             '<p>Built from the documented v3 interfaces.</p>'
                             '<button data-action="month">Open month</button></section>'),
                },
                {
                    "op": "write",
                    "path": "web/style.css",
                    "encoding": "utf-8",
                    "expectedSha256": stylesheet["sha256"],
                    "content": stylesheet["content"] +
                               "\n#agent-workflow-panel{padding:1rem;border-radius:1rem}\n",
                },
            ]}, ensure_ascii=False), encoding="utf-8")

            edited = self.cli_json("edit-v3", "--file", source_package,
                                   "--operations", operations_path,
                                   "--out", output_package)
            self.assertEqual(edited["command"], "edit-v3")
            self.assertTrue(edited["ok"])
            self.assertEqual({change["path"] for change in edited["changes"]},
                             {"web/index.html", "web/style.css"})

            validation = self.cli_json("validate", output_package, "--json")
            self.assertEqual(validation["command"], "validate")
            self.assertTrue(validation["ok"])
            self.assertEqual(validation["packageSha256"], edited["packageSha256"])
            self.assertEqual(validation["skinId"], inspected["manifest"]["id"])

            bridge_audit = self.cli_json("audit-bridge-v3", output_package)
            self.assertTrue(bridge_audit["ok"])
            self.assertEqual(bridge_audit["requiredCapabilities"], ["host.action"])
            self.assertIn("month", {binding["action"]
                                     for binding in bridge_audit["bindings"]})
            self.assertTrue(bridge_audit["requiresSourceReview"])
            self.assertIn("payloads", " ".join(bridge_audit["limitations"]))

            final_inspection = self.cli_json("inspect-v3", output_package)
            final_analysis = self.cli_json("analyze-v3", output_package)
            final_stylesheet = self.cli_json("read-v3", "--file", output_package,
                                             "--path", "web/style.css")
            self.assertTrue(any(node["id"] == "agent-workflow-panel"
                                for node in final_analysis["pages"][0]["components"]))
            self.assertIn("#agent-workflow-panel", final_stylesheet["content"])
            self.assertEqual(final_inspection["manifest"]["capabilities"],
                             inspected["manifest"]["capabilities"])

            _, original_files = unpack(source_package.read_bytes())
            _, final_files = unpack(output_package.read_bytes())
            changed_paths = {path for path in original_files
                             if original_files[path] != final_files[path]}
            self.assertEqual(changed_paths, {"web/index.html", "web/style.css"})
            self.assertEqual(hashlib.sha256(ASSET.read_bytes()).hexdigest(), original_asset_sha)
            print("AI V3 WORKFLOW PASS " + json.dumps({
                "commands": ["build-v3", "inspect-v3", "analyze-v3", "read-v3",
                             "edit-v3", "validate", "audit-bridge-v3", "inspect-v3",
                             "analyze-v3", "read-v3"],
                "baselinePackageSha256": original_asset_sha,
                "editedPackageSha256": edited["packageSha256"],
                "changedPaths": sorted(changed_paths),
                "capabilitiesPreserved": True,
                "originalApkAssetPreserved": True,
            }, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    unittest.main()

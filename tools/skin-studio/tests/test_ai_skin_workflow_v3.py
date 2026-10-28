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
            structure_package = work / "agent-structure.pcskin"
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
            ]}, ensure_ascii=False), encoding="utf-8")

            edited = self.cli_json("edit-v3", "--file", source_package,
                                   "--operations", operations_path,
                                   "--out", structure_package)
            self.assertEqual(edited["command"], "edit-v3")
            self.assertTrue(edited["ok"])
            self.assertEqual({change["path"] for change in edited["changes"]},
                             {"web/index.html"})

            structured_analysis = self.cli_json("analyze-v3", structure_package)
            structured_page = next(item for item in structured_analysis["pages"]
                                   if item["path"] == "web/index.html")
            panel = next(node for node in structured_page["components"]
                         if node["id"] == "agent-workflow-panel")
            structured_stylesheet = self.cli_json("read-v3", "--file", structure_package,
                                                   "--path", "web/style.css")
            style_operations_path = work / "style-operations.json"
            style_operations_path.write_text(json.dumps({"operations": [{
                "op": "component-style",
                "path": structured_page["path"],
                "expectedSha256": structured_page["sourceSha256"],
                "targetNodeId": panel["nodeId"],
                "cssPath": "web/style.css",
                "expectedCssSha256": structured_stylesheet["sha256"],
                "setProperties": {"padding": "1rem", "border-radius": "1rem",
                                   "--agent-card-gap": "0.75rem"},
                "removeProperties": [],
            }]}, ensure_ascii=False), encoding="utf-8")
            edited = self.cli_json("edit-v3", "--file", structure_package,
                                   "--operations", style_operations_path,
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
            month_binding = next(binding for binding in bridge_audit["bindings"]
                                 if binding["action"] == "month")
            self.assertEqual(month_binding["payloadSchemaRef"],
                             "#/$defs/actionPayloads/month")
            self.assertEqual(bridge_audit["contractSchema"]["path"],
                             "docs/contracts/web-skin-bridge-v1.schema.json")
            self.assertTrue(bridge_audit["requiresSourceReview"])
            self.assertIn("payloads", " ".join(bridge_audit["limitations"]))

            final_inspection = self.cli_json("inspect-v3", output_package)
            final_analysis = self.cli_json("analyze-v3", output_package)
            final_stylesheet = self.cli_json("read-v3", "--file", output_package,
                                             "--path", "web/style.css")
            self.assertTrue(any(node["id"] == "agent-workflow-panel"
                                for node in final_analysis["pages"][0]["components"]))
            self.assertIn("--agent-card-gap: 0.75rem;", final_stylesheet["content"])
            final_panel = next(node for node in final_analysis["pages"][0]["components"]
                               if node["id"] == "agent-workflow-panel")
            self.assertTrue(any(attribute["name"] == "data-pcskin-studio-style"
                                 for attribute in final_panel["attributes"]))
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
                             "edit-v3", "analyze-v3", "read-v3", "edit-v3",
                             "validate", "audit-bridge-v3", "inspect-v3",
                             "analyze-v3", "read-v3"],
                "baselinePackageSha256": original_asset_sha,
                "editedPackageSha256": edited["packageSha256"],
                "changedPaths": sorted(changed_paths),
                "capabilitiesPreserved": True,
                "originalApkAssetPreserved": True,
            }, ensure_ascii=False, sort_keys=True))

    def test_scaffold_v3_creates_a_buildable_least_privilege_skin_from_scratch(self):
        evidence_root = ROOT / ".local" / "android-test"
        evidence_root.mkdir(parents=True, exist_ok=True)

        with tempfile.TemporaryDirectory(prefix="v3-scaffold-", dir=evidence_root) as temp:
            work = Path(temp)
            first = work / "blank-skin.pcskin"
            second = work / "same-blank-skin.pcskin"
            created = self.cli_json(
                "scaffold-v3", "--id", "agent-blank-v3", "--name", "Agent Blank Skin",
                "--out", first, "--json")

            self.assertEqual(created["command"], "scaffold-v3")
            self.assertTrue(created["ok"])
            self.assertEqual(created["manifest"]["capabilities"], [])
            self.assertEqual(created["fileCount"], 4)
            self.assertTrue(first.is_file())

            inspected = self.cli_json("inspect-v3", first)
            self.assertEqual(inspected["packageSha256"], created["packageSha256"])
            self.assertEqual(inspected["manifest"]["id"], "agent-blank-v3")
            self.assertEqual(inspected["manifest"]["capabilities"], [])
            self.assertEqual(
                {item["path"] for item in inspected["files"]},
                {"project.json", "web/index.html", "web/style.css", "web/app.js"})

            page = self.cli_json("analyze-v3", first)["pages"]
            self.assertEqual(len(page), 1)
            self.assertEqual(page[0]["path"], "web/index.html")
            audit = self.cli_json("audit-bridge-v3", first)
            self.assertTrue(audit["ok"])
            self.assertEqual(audit["bindings"], [])
            self.assertEqual(audit["requiredCapabilities"], [])
            self.assertTrue(audit["requiresSourceReview"])

            validation = self.cli_json("validate", first, "--json")
            self.assertEqual(validation["runtimeVersion"], 3)
            self.assertEqual(validation["packageSha256"], created["packageSha256"])

            repeat = self.cli_json(
                "scaffold-v3", "--id", "agent-blank-v3", "--name", "Agent Blank Skin",
                "--out", second, "--json")
            self.assertEqual(repeat["packageSha256"], created["packageSha256"])
            self.assertEqual(first.read_bytes(), second.read_bytes())

            explicit = self.cli_json(
                "scaffold-v3", "--id", "agent-requested-v3", "--name", "Explicit Request",
                "--capability", "host.read", "--capability", "host.action",
                "--out", work / "explicit.pcskin", "--json")
            self.assertEqual(explicit["manifest"]["capabilities"],
                             ["host.read", "host.action"])

            existing = work / "existing.pcskin"
            sentinel = b"leave this file untouched"
            existing.write_bytes(sentinel)
            rejected = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "scaffold-v3",
                 "--id", "agent-blank-v3", "--name", "Agent Blank Skin",
                 "--out", str(existing), "--json"],
                cwd=ROOT, text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(rejected.returncode, 2)
            error = json.loads(rejected.stderr)
            self.assertEqual(error["command"], "scaffold-v3")
            self.assertFalse(error["ok"])
            self.assertEqual(existing.read_bytes(), sentinel)

            invalid_capability = work / "invalid-capability.pcskin"
            rejected_capability = subprocess.run(
                [sys.executable, str(STUDIO / "skin_studio.py"), "scaffold-v3",
                 "--id", "agent-invalid-v3", "--name", "Invalid Capability",
                 "--capability", "host.database", "--out", str(invalid_capability), "--json"],
                cwd=ROOT, text=True, encoding="utf-8", capture_output=True, check=False)
            self.assertEqual(rejected_capability.returncode, 2)
            capability_error = json.loads(rejected_capability.stderr)
            self.assertEqual(capability_error["command"], "scaffold-v3")
            self.assertFalse(capability_error["ok"])
            self.assertFalse(invalid_capability.exists())


if __name__ == "__main__":
    unittest.main()

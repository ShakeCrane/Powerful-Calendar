"""Runtime v2 ZIP cross-validation and adversarial schema tests."""
import copy
import json
from pathlib import Path
import sys
import unittest
from zipfile import ZipFile
import io

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import runtime_package

EXAMPLES = Path(__file__).resolve().parents[1] / "examples"


class RuntimeV2Tests(unittest.TestCase):
    def setUp(self):
        self.m = json.loads((EXAMPLES / "nebula-flux-manifest.json").read_text("utf8"))
        self.ui = json.loads((EXAMPLES / "nebula-flux-ui.json").read_text("utf8"))

    def test_example_is_a_genuine_importable_v2_zip(self):
        blob = runtime_package.build(self.m, self.ui)
        manifest, unpacked = runtime_package.unpack(blob)
        self.assertEqual(manifest["schemaVersion"], 2)
        self.assertEqual(set(unpacked["screens"]), runtime_package.SCREEN_NAMES)
        self.assertIn("ui.json", manifest["sha256"])
        artifact_manifest, artifact_ui = runtime_package.unpack(
            (EXAMPLES / "Nebula-Flux-v2.pcskin").read_bytes())
        self.assertEqual(artifact_manifest["id"], manifest["id"])
        self.assertEqual(artifact_ui, unpacked)

    def test_changing_ui_without_rehashing_is_rejected(self):
        blob = runtime_package.build(self.m, self.ui)
        source = ZipFile(io.BytesIO(blob))
        output = io.BytesIO()
        from zipfile import ZIP_DEFLATED
        with ZipFile(output, "w", ZIP_DEFLATED) as dest:
            for entry in source.infolist():
                data = source.read(entry.filename)
                if entry.filename == "ui.json":
                    data = data.replace(b"slide-left", b"slide-up")
                dest.writestr(entry.filename, data)
        with self.assertRaises(ValueError):
            runtime_package.unpack(output.getvalue())

    def test_unauthorized_action_is_rejected(self):
        bad = copy.deepcopy(self.ui)
        bad["screens"]["timeline"]["children"][1]["children"][0]["action"] = "commit"
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)

    def test_missing_dialog_confirmation_is_rejected(self):
        bad = copy.deepcopy(self.ui)
        bad["screens"]["overlay"] = {"type": "card", "children": [
            {"type": "overlay-title"}, {"type": "overlay-body"}]}
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)

    def test_hidden_root_host_is_rejected(self):
        bad = copy.deepcopy(self.ui)
        bad["screens"]["chrome"] = {"type": "text", "text": "Blank screen"}
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)

    def test_excessive_tree_depth_and_node_count(self):
        bad = copy.deepcopy(self.ui)
        tree = {"type": "text", "text": "X"}
        for _ in range(12):
            tree = {"type": "box", "children": [tree]}
        bad["screens"]["timeline"] = tree
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)
        bad = copy.deepcopy(self.ui)
        def wide(level):
            if level == 0:
                return {"type": "text", "text": "x"}
            return {"type": "column", "children": [wide(level - 1) for _ in range(8)]}
        bad["screens"]["timeline"] = wide(3)
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)

    def test_v1_pack_format_remains_unchanged(self):
        import skin_studio
        old = dict(self.m)
        old["schemaVersion"] = 1
        old.pop("runtimeUi")
        data = skin_studio.pack(old, {})
        parsed, images = skin_studio.unpack(data)
        self.assertEqual(parsed["schemaVersion"], 1)
        self.assertEqual(images, {})

    def test_misplaced_sensitive_widget_is_rejected(self):
        bad = copy.deepcopy(self.ui)
        bad["screens"]["timeline"]["children"].append({"type": "credentials"})
        with self.assertRaises(ValueError):
            runtime_package.build(self.m, bad)


if __name__ == "__main__":
    unittest.main()

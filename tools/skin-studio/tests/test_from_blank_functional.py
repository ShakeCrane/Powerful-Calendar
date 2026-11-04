from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest
import zipfile
from io import BytesIO

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from host_binding_catalog import load_catalog, validate_project_bindings
from runtime_package_v3 import build, unpack

FIXTURE = HERE / "fixtures" / "from-blank-functional"


class FromBlankFunctionalSkinTest(unittest.TestCase):
    def package(self) -> bytes:
        manifest = json.loads((FIXTURE / "manifest.json").read_text(encoding="utf-8"))
        return build(manifest, FIXTURE / "source")

    def test_blank_project_builds_as_a_valid_five_page_host_skin(self):
        raw = self.package()
        manifest, files = unpack(raw)
        project = json.loads(files["project.json"].decode("utf-8"))
        self.assertEqual("powercal-from-blank-functional", manifest["id"])
        self.assertEqual(["host.read", "host.action"], manifest["capabilities"])
        self.assertEqual(["TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS"],
                         [page["route"] for page in project["pages"]])
        self.assertNotIn("default-clear", " ".join(files).lower())
        self.assertNotIn("source/web/app.js", " ".join(files).lower())
        validate_project_bindings(project)
        runtime = files["web/app.js"].decode("utf-8")
        self.assertIn('command("editor.open"', runtime)
        self.assertIn('command("editor.discardChanges"', runtime)
        self.assertIn('row.dataset.eventSeq = safe(item.eventSeq)', runtime)
        self.assertIn('command("editor.patch"', runtime)
        self.assertIn('command("editor.save"', runtime)

    def test_project_has_actual_feature_components_and_catalogued_bindings(self):
        _, files = unpack(self.package())
        project = json.loads(files["project.json"].decode("utf-8"))
        catalog = load_catalog()
        state_paths = {item["path"] for item in catalog["stateFrame"]["fields"] if item.get("bindable")}
        action_names = {item["name"] for item in catalog["actions"]}
        routes = {page["route"]: page for page in project["pages"]}

        def walk(nodes):
            for node in nodes:
                yield node
                yield from walk(node.get("children", []))

        components = {route: list(walk(page["components"])) for route, page in routes.items()}
        self.assertTrue({"timeline", "event-card"} <= {node["type"] for node in components["TIMELINE"]})
        self.assertTrue({"month-grid", "date-button"} <= {node["type"] for node in components["MONTH"]})
        self.assertIn("memo-row", {node["type"] for node in components["MEMO"]})
        self.assertIn("chat-item", {node["type"] for node in components["AI"]})
        self.assertIn("settings-row", {node["type"] for node in components["SETTINGS"]})
        for nodes in components.values():
            for node in nodes:
                props = node.get("props", {})
                if props.get("hostStateBinding"):
                    self.assertIn(props["hostStateBinding"], state_paths)
                if props.get("hostAction"):
                    self.assertIn(props["hostAction"]["name"], action_names)

    def test_package_is_reproducible_and_test_only(self):
        first = self.package()
        second = self.package()
        self.assertEqual(first, second)
        self.assertFalse((HERE.parents[2] / "app/src/main/assets/from-blank-functional.pcskin").exists())
        with zipfile.ZipFile(BytesIO(first)) as archive:
            names = archive.namelist()
        self.assertEqual(len(names), len(set(names)))


if __name__ == "__main__":
    unittest.main()

"""Distribution invariant: one factory .pcskin; demo skins are authoring fixtures."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]


class SingleDefaultDistributionTest(unittest.TestCase):
    def test_only_one_factory_skin_package_is_shipped(self):
        assets = ROOT / "app" / "src" / "main" / "assets"
        packages = [path.name for path in assets.glob("*.pcskin")]
        self.assertEqual(["powercal-clear-default.pcskin"], packages)

    def test_blank_starter_is_authoring_only(self):
        from sys import path as sys_path
        studio = ROOT / "tools" / "skin-studio"
        sys_path.insert(0, str(studio))
        try:
            from blank_project_v3 import blank_project
            manifest, files = blank_project()
        finally:
            sys_path.pop(0)
        self.assertEqual("powercal-blank-skin", manifest["id"])
        self.assertEqual(["host.read"], manifest["capabilities"])
        self.assertNotIn("assets", files)
        project = json.loads(files["project.json"])
        self.assertEqual(["TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS"],
                         [page["route"] for page in project["pages"]])
        node_ids = [page["id"] for page in project["pages"]]
        for page in project["pages"]:
            self.assertEqual(1, len(page["components"]))
            root_node = page["components"][0]
            self.assertEqual("container", root_node["type"])
            self.assertEqual([], root_node["children"])
            node_ids.append(root_node["id"])
        self.assertEqual(len(node_ids), len(set(node_ids)))
        self.assertFalse((ROOT / "app" / "src" / "main" / "assets" /
                          "powercal-blank-skin.pcskin").exists())


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import json
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from new_compiled_skin import create, make_kotlin, design_brief, MARKER


class CodegenTests(unittest.TestCase):
    def test_scaffold_all_ten_with_host_only_actions(self):
        s = make_kotlin("aurora-glass")
        self.assertIn("AuroraGlassSkin", s)
        self.assertEqual(s.count("context.Native()"), 10)
        for label in ("Timeline", "Month", "Memo", "Ai", "Settings", "Navigation", "Deck", "Overlay", "Picker", "Chrome"):
            self.assertIn("AuroraGlass" + label, s)
        self.assertNotIn("PowerStore.commit", s)
        self.assertNotIn("getSharedPreferences", s)

    def test_sanitized_names_no_traversal(self):
        for name in ("../hack", "ab", "AURORA", "skin_slash", "a/../b", "new skin", "x" * 33, "abc-", "abc--def"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                make_kotlin(name)

    def test_creation_and_opt_in_activation(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            reg = root / "ActiveVisualSkin.kt"
            reg.write_text('object R {\n const val PROFILE = "native"\n' +
                           MARKER + '\n}\n', encoding="utf-8")
            files = create("simple-skin", source_root=root, activate=True)
            self.assertEqual(len(files), 2)
            self.assertIn('const val PROFILE = "simple-skin"', reg.read_text(encoding="utf-8"))
            self.assertIn('"simple-skin" -> SimpleSkinSkin.install()', reg.read_text(encoding="utf-8"))
            with self.assertRaises(FileExistsError):
                create("simple-skin", source_root=root, activate=True)

    def test_design_brief_is_not_claimed_as_runtime_layout(self):
        sample = {"format": "powercal-skin-studio", "schemaVersion": 1,
                  "compiled": {"pages": {"MONTH": [{"id": "monthGrid",
                                                     "zone": "content",
                                                     "motion": "fade", "duration": 250}]}}}
        result = design_brief("aurora", sample)
        self.assertIn("monthGrid", result)
        self.assertIn("motion=fade", result)
        self.assertIn("NOT executable Android layout", result)

    def test_failed_activation_never_creates_a_partial_skin(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            (root / "ActiveVisualSkin.kt").write_text("unrelated file", encoding="utf-8")
            with self.assertRaises(ValueError):
                create("new-skin", source_root=root, activate=True)
            self.assertFalse((root / "NewSkinSkin.kt").exists())


if __name__ == "__main__":
    unittest.main()

"""Structural regression checks for the source-compiled presentation boundary."""
from pathlib import Path
import re
import unittest

REPO = Path(__file__).resolve().parents[3]
UI = REPO / "app/src/main/java/dev/powerfulcalendar/ui/v2"


class PresentationBoundaryTests(unittest.TestCase):
    def test_first_party_dialogs_are_routed_through_skinnable_surface(self):
        for filename in ("PowerCalApp.kt", "DraftDeck.kt", "MonthScreen.kt", "TimelineScreen.kt"):
            source = (UI / filename).read_text(encoding="utf-8")
            self.assertNotRegex(source, r"(?<!\w)AlertDialog\(",
                                "Direct AlertDialog bypasses the skin adapter: " + filename)
            self.assertIn("SkinAlertDialog(", source)
        self.assertIn("SkinWindowDialog(", (UI / "DraftDeck.kt").read_text("utf-8"))

    def test_host_shell_and_notices_are_compiled_skin_surfaces(self):
        host = (UI / "PowerCalApp.kt").read_text("utf-8")
        for binding in ("SkinAppShell {", "SkinNotice(", "PowerCalNavigation(",
                        "MemoCenterScreen(", "AiChatScreen(", "DraftDeckDialog(", "ToolsPage("):
            self.assertIn(binding, host)

    def test_privacy_editors_remain_host_owned_but_placeable(self):
        host = (UI / "PowerCalApp.kt").read_text("utf-8")
        api = (UI / "PresentationSkinRuntime.kt").read_text("utf-8")
        self.assertIn("internal fun ProtectedAiSettingsSection()", host)
        self.assertIn("internal fun ProtectedProfileSection()", host)
        self.assertIn("fun ProtectedAiCredentials()", api)
        self.assertIn("fun ProtectedProfile()", api)
        self.assertNotIn("val apiKey", api)
        self.assertIn("SkinPickers.pickDate(", (UI / "DraftDeck.kt").read_text("utf-8"))

    def test_full_route_registry_has_one_restore(self):
        registry = (UI / "PresentationSkinRuntime.kt").read_text("utf-8")
        example = (UI / "skins/ActiveVisualSkin.kt").read_text("utf-8")
        for key in ("month", "memo", "ai", "settings", "navigation", "overlay", "picker", "chrome"):
            self.assertIn("var " + key + " by mutableStateOf<", registry)
            self.assertIn(key + " = null", registry)
        self.assertIn("CompiledSkinRuntime.restoreNativeTimeline()", registry)
        self.assertIn("DeckPresentationSkinRegistry.reset()", registry)
        self.assertEqual(example.count("const val PROFILE = \"native\""), 1)
        self.assertIn("PresentationSkinRegistry.useChrome(StudioExampleChrome())", example)

    def test_runtime_activation_owns_no_database_or_reminder(self):
        starter = (REPO / "app/src/main/java/dev/powerfulcalendar/MainActivity.kt").read_text("utf-8")
        self.assertIn("ActiveVisualSkin.install()", starter)
        self.assertLess(starter.index("ActiveVisualSkin.install()"), starter.index("setContent {"))


if __name__ == "__main__":
    unittest.main()

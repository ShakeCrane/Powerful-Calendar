"""Source-level guard for the Android-only recoverability contract."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
UI = ROOT / "app" / "src" / "main" / "java" / "dev" / "powerfulcalendar" / "ui" / "v2"


class NativeRecoveryContractTest(unittest.TestCase):
    def test_import_does_not_select_new_code(self):
        app = (UI / "PowerCalApp.kt").read_text(encoding="utf-8")
        snippet = app.split("val importSkin =", 1)[1].split("val exportOriginalSkin =", 1)[0]
        self.assertIn("skins.install(uri)", snippet)
        self.assertNotIn("skins.select(result.id)", snippet)

    def test_native_settings_and_hidden_activity_rescue_are_both_available(self):
        app = (UI / "PowerCalApp.kt").read_text(encoding="utf-8")
        shell = (UI / "SkinnableChrome.kt").read_text(encoding="utf-8")
        web = (UI / "WebSkinHost.kt").read_text(encoding="utf-8")
        activity = (ROOT / "app/src/main/java/dev/powerfulcalendar/MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("forceNative = tools", app)
        self.assertIn("if (forceNative) native()", shell)
        self.assertNotIn("onOpenNativeSettings", web)
        self.assertIn("statusBarsPadding().navigationBarsPadding()", web)
        self.assertIn("onSkinFailure()", web)
        self.assertIn("override fun dispatchTouchEvent", activity)
        self.assertIn("AlternatingTapRecovery", activity)
        self.assertIn("selectNativeForEmergencyRecovery()", activity)
        self.assertIn("nativeRecoveryPersisted = SkinPackageManager(applicationContext)", activity)
        self.assertIn("nativeRecoverySignal++", activity)

    def test_skin_switch_warns_and_requires_explicit_confirm(self):
        app = (UI / "PowerCalApp.kt").read_text(encoding="utf-8")
        self.assertIn("skinToActivate?.let", app)
        self.assertIn("连续敲击 8 下", app)
        self.assertIn("确认切换", app)
        self.assertIn("skinToActivate = skinChoices.firstOrNull", app)
        self.assertNotIn("skins.select(result.id)", app)


if __name__ == "__main__":
    unittest.main()

# PC-ISSUE-SEPARATION-F09-CLEAR-NARROW-HEADER

- **Date:** 2026-11-04
- **Status:** Resolved in F-09 code; overall F-09 remains BLOCKED by TalkBack traversal evidence
- **Base:** `e78829a3889f2edafea267440f6ded6f86b42fa2`
- **Fix commit:** `3932427a`
- **Evidence:** `.local/android-test/reports/F-09-20261104/manifest.md`

## Finding and resolution

At a 288dp viewport with Android font scale 2×, the factory Clear header kept its title and toolbar in one row. The title was clipped outside the visible viewport. The CSS now switches the header to a vertical layout at widths up to 320px and lets toolbar buttons share the available width.

The post-fix Android WebView checks passed at 288dp and 360dp with 2× font scale and reduced motion. They verified that the title, bottom navigation, and timeline controls remain visible, navigation retains its expected named order, visible controls have accessible names, and the document does not overflow horizontally.

## Verification

- `ClearDisplayAdaptationInstrumentedTest`: baseline, 360dp, and 288dp runs PASS on the isolated API 35 AVD.
- `TimelineSystemFontScaleInstrumentedTest`, `TimelineSmallScreenInstrumentedTest`, and `SystemReducedMotionInstrumentedTest`: PASS with the required system settings.
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`: PASS; source package and APK asset are byte-identical.
- Exactly one `.pcskin` is packaged: `assets/powercal-clear-default.pcskin`.
- Factory package SHA-256: `b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`.

This is a responsive behavior fix only; Core and the frozen business contract were not changed. Actual TalkBack focus traversal remains unverified and blocks complete F-09 sign-off.

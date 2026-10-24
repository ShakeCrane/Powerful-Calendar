# PC-ISSUE-173 — Glass-disabled native fallback readability

Date: 2026-10-25

Status: **Robolectric API 35 renderer subcase PASS / system low-RAM trigger and emulator/OEM rendering NOT TESTED / core freeze HOLD**

## Scope and finding

`REC-P1-074` asks that the native fallback remain readable when the glass effect is disabled. The production theme already has a deterministic fallback branch: `PowerCalTheme` replaces the translucent glass fill with the Material surface and removes the highlight when `GlassEffects.blur` is false; `GlassPanel` uses that opaque surface with zero shadow. The missing item was a direct regression test of that branch in both color schemes. This work adds verification only; no product styling or layout changed.

## Regression test

Added `GlassComposeTest.disabledGlassKeepsOpaqueNativeSurfaceReadableInLightAndDarkThemes`. Robolectric runs the production `PowerCalTheme` and `GlassPanel` at API 35 / 360×800dp xhdpi with `blur=false`, then checks light and dark modes:

- fallback fill equals `MaterialTheme.colorScheme.surface`;
- glass highlight is transparent;
- `onSurface` contrast against the fallback surface is at least 4.5:1;
- fallback text and panel remain displayed.

This signs the theme renderer branch only. It does not exercise Android's low-RAM detection, an actual emulator GPU/compositor, or physical/OEM rendering.

## Verification evidence

- Focused `GlassComposeTest`: **17/17 PASS**, 0 failures/errors/skips. Log SHA-256 `80E634F4D20896DCE89671465D94C258E632FBA36427C692FFFE456E5FDB5322`; JUnit XML SHA-256 `69908972906A2FF5A8E9C8361F9F7A2981744705CE99154222E4E4C448811413`.
- Current main worktree source-boundary, JVM, Debug and lint gate: **543/543 tests / 75 suites**, 0 failures/errors/skips; 59 tasks; lint 0 errors / 56 warnings. Log SHA-256 `3D304123B3A4D072C334142FCDAD4C09936694A56D8B73BA5394FE7E262AA169`; Debug APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614`; lint XML SHA-256 `6254B2A5C445027068221A411E65E5798A8F8457FAD9716266253B2BFB52E4D4`.
- Clean detached worktree with the exact changed test source: **542/542 JVM tests / 74 suites**, 0 failures/errors/skips; **88/88 tasks** passed, including `assembleDebugAndroidTest`; lint 0 errors / 53 warnings. Log SHA-256 `24528085B4402C93F7DACDC21E89FD720D7872586E1A144B6644B7263BE2C1BC`; Debug / AndroidTest APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`; lint XML SHA-256 `B95E8834C3E3EFA3386E0391464F11FB5953ED428EA7C87FA70CE020661628C0`.
- The first focused compile attempt failed before test execution because the new test omitted the `androidx.compose.ui.platform.testTag` import. The import was added; final test and full gates passed. Failure log SHA-256 `4C3474B7F172F8A6FA0B1BA6AAEA83C5170545F0A730238F4E98BAEF380E31AD`.
- Structural ledger validation: 218 rows / 217 active reconstructed; 205 PASS / 0 FAIL / 10 BLOCKED / 1 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED; P0/P1/P2 = 145/71/1; historical IDs mapped = 0. Ledger SHA-256 `50F1485604B0CD46F2046A984FCC6EF68F4FAAED8136FA76BACB469F890A093B`; validator artifact SHA-256 `581B7C51C19119FC6C04DE59EF391110E545E18BA7B946B0D523FAFF436DC71A`; validator unit tests passed 2/2. Structural validation does not test product behavior.

Evidence files are under `.local/android-test/reports/freeze-20261025/pc-issue-173-glass-fallback-*` and the clean worktree at `.local/android-test/worktrees/pc-issue-173-glass-fallback/`. The main-worktree AndroidTest compilation was not attempted because a separate protected untracked D05 test has a known compile error; the clean worktree excludes it and successfully compiles the tracked AndroidTest suite.

## Remaining limits

`REC-P1-074` is PASS only for the explicit blur-disabled production theme branch. The OS low-RAM trigger and emulator/physical/OEM rendering are not tested; no active AVD was touched. Other open provider, recurrence-specification, reminder, accessibility and physical-device gates keep the overall freeze **HOLD**.

# Clear Settings exposes Host-owned calendar and data actions

**Date:** 2026-10-31
**Status:** This Settings slice is **PASS** on Chrome/CDP and an API 35 Android emulator. SEP-11 remains incomplete.

## Gap and fix

Clear Settings already showed reminder permissions, reconciliation, execution diagnostics and the native recovery route. Calendar import/export, backup/restore and legacy migration were only reachable from the separate data-options page, leaving the main skin Settings surface incomplete.

The Clear Settings page now renders the existing Host-projected optionGroups using the same registered invokeOption actions. It exposes six existing options for file import, system-calendar import, legacy migration, ICS export, backup export and backup restore. No Host capability or business action was added. The native full Settings/recovery entry remains available. The Bridge contract now documents this reuse and the Host-owned confirmation boundary.

## Evidence

- **API 35 emulator codex-clear-api35 / Android 15:** ClearSettingsOptionsInstrumentedTest, **1/1 PASS** against the packaged Clear WebView and production Host. It verified all six options, then opened the Host confirmation for legacy migration and cancelled it. Formal records, drafts, commit count, identity count and sequence watermark stayed unchanged. The Settings page and native recovery entry remained available.
- **Chrome 156/CDP:** canonical Clear Settings displayed all six Host-projected IDs and sent invokeOption(data.legacy.migrate). Permission and reminder controls still emitted their registered actions; the native recovery action remained present. The protocol Mock did not launch OS UI or read/write calendar data.
- **Skin Studio:** browser source/project/resource edit and export/re-import E2E passed; the complete Studio Python suite passed **62/62**. These verify Studio authoring and round-trip workflows, not Android Runtime execution.
- **Gradle gate:** :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain — **BUILD SUCCESSFUL**, **784/784 JVM tests**, 125 suites, 0 failures/errors/skips; both boundaries, AndroidTest compilation, Debug APK and lint passed (152 tasks).
- **Canonical package:** 9 files, 394,565 bytes. Source bundle and Debug APK asset SHA-256 both 601e84ffe4316140dcf0310d9977482478b3f9dbfadf986d5d6da4d0cb10a884. Debug APK SHA-256 7D6A3A37C86116DEB94A1CFEEC5E1235AD0245BBD4C1AFBCAEA096A9B727B634.

Detailed evidence is under .local/android-test/reports/clear-settings-options-20261031/:

- Gradle gate SHA-256: 5CF3A138AFCF6A0E62CDDA11D195F4E7F3215A8CDE344C7B09198D8575254675
- Android instrumentation log SHA-256: C054896E19C5D801065935683223FFEF5D9D1E0DEDDBAF2C7B1CFD0A8EBEFFEB
- Studio Python log SHA-256: CE2EE8D802EF521DA2F9F699F1BCA47F89740E39BFD29B82D6DD742CA43A1FB5
- Studio browser source round-trip log SHA-256: C0949E6F0C6AACC2C621860625EF49E899D67C03AD78F3EC0E81087B86B53C41
- Clear Settings Chrome/CDP result SHA-256: 8A065DF839F14049384526A265CB2E90983DF6FB92A0341783FE98C4FB75D425
- Screenshot: .local/android-test/reports/clear-settings-options-20261031/chrome/settings-2026-10-30T18-48-45-903Z.png

The first emulator attempt timed out while a fast polling loop repeatedly queried the WebView. The test now checks the Host projection separately and paces DOM polling; the final instrumented run passed. The initial failure is retained in android-instrumentation.log.

## Scope left open

This does not close SEP-11. The actual emulator run cancelled the legacy-migration confirmation; it did not complete SAF import/export, backup restore, or system-calendar permission flows. AI credential/profile editing, skin management, full Native/Skin replay, physical/OEM behavior and other Settings error paths remain unverified. Native recovery remains available.

## Git

Runtime and test commit: dfcd5948f4b5404302a202825f61507d1a415492 (fix(皮肤分离): 在Clear设置页开放宿主日历与数据操作), pushed to origin/feat/skin-first-separation.

## Follow-up — Clear installed skin selection (2026-10-31)

Clear Settings now renders the Host-projected installed catalogue and sends only a catalogue ID. The Host accepts the action only from Settings, resolves it against the current catalogue, and reuses the existing trusted activation confirmation, edit checkpoint, and package manager. No package path, credentials, or calendar data cross the Bridge. Studio Preview Mock supports the same confirmation/cancel interaction without changing Android state.

- **API 35 emulator `codex-clear-api35`: 9/9 actual packaged Clear WebView tests PASS** across Settings activation/cancellation, Memo, month/timeline create context, AI proposal review, and kind conversion. The Settings test verifies cancelling keeps Clear selected, confirming selects the Native recovery skin, and the teardown restores the emulator's original skin preference. Existing calendar/data cancellation assertions still show unchanged Store counters.
- During smoke setup, the kind-conversion test exposed three test-harness mismatches (CSS selector quoting, JSON-quoted `evaluateJavascript` strings, and the actual Host cancellation copy). Correcting the assertions made that test pass; no Clear runtime defect was reproduced in this path.
- Full gate: **784/784 JVM tests**, 125 suites, 0 failures/errors/skips; both source boundaries, AndroidTest Kotlin compilation, Debug APK and lint passed (152 tasks). Lint: 0 errors, 55 warnings. Studio Python **63/63 PASS**; Chrome/CDP Preview Mock E2E and JS syntax checks passed. Clear package verification matches the packaged APK asset byte-for-byte: 9 files, 395,062 bytes, SHA-256 `49e0f09edceb21834fa7bf2ac5ec0b15f2a81d75bb2ef67486531bd92f9246e2`. Debug APK SHA-256 `BF7B3633CC49CEB776F8A14E26C2DAC8E014E39E0F888595F0AA2EA60ADF2529`.
- Evidence under `.local/android-test/reports/sep11-clear-skin-catalog-20261031/`: full Gradle log SHA-256 `33BBDA7F3853FB0809CE94D9B879883AF9B3FAF75F63EC35571B250240834AB1`; connected instrumentation log `F1F0FE7CB8619137DC0D5392EF92F6D09B636E1E041EAA55DE12095C8B3F5CB6`; JUnit XML `AEA3B5EF01F293504DDC9EC10D478697D18BA702B5A6BDF5B91630B244DB4151`; Studio Python log `80E88373C31A4C8C61B4FEBD85C7279E0B97B14657E06DB2A965060D14138468`; Preview Mock JSON `83554BD191CDAEEBD9887E1BC039E680A08AA098320396BA19C5C459AA253509`.

This is evidence for the installed-skin selection slice only. It does not close SEP-11 or establish total Clear parity: AI profile/credential settings, skin import/export/delete, SAF and system permission completion, broader Native/Skin replay, accessibility and physical/OEM behavior remain unverified. The SEP-11 row remains **NOT TESTED** overall; S0-ST-06 remains **PARTIAL / HOLD**.

The implementation, tests, regenerated Clear source package, Studio Mock, matrix update, and this evidence update are committed together on the integration branch; the resulting SHA is recorded in the Git history and delivery summary.

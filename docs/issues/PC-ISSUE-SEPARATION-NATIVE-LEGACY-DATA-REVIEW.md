# Native legacy migration and backup review boundary

**Status:** CODE COMPLETE; focused and full JVM tests, source boundaries, AndroidTest Kotlin compilation, APK assembly and lint TESTED; Android/device recovery UI NOT TESTED.

**Scope:** Native renderer separation only. SEP-11/SEP-14 remain INCOMPLETE.

## Root cause

The Native Settings screen lived in `:native-skin`, but the legacy database migration and flat backup v2 restore warnings were composed inline in `PowerCalApp.kt`. Those surfaces mixed Native presentation copy/buttons with Host-only importer, original backup content, validation, recovery and persistence actions.

## Change

- Added `NativeLegacyDataReview` in `:native-skin` and the display-only `NativeLegacyDataReviewDialog` slot in `:skin-api`.
- Migrated only the migration and restore warning copy, titles and confirm/cancel buttons. The Host still owns `LegacyImporter`, the selected backup JSON, parsing, lineage/integrity checks, empty-database requirements, restore execution, reminder reconciliation and result messaging.
- Native receives only a review kind and callbacks. It has no database, backup parser, importer, scheduler, or restore capability.
- Added Compose tests for the read-only migration warning, authenticity caveat for old backups and explicit Host confirmation callbacks. Existing migration, Host migration and backup envelope tests remain green.
- No Clear source, frozen Core, persistence semantics, default skin or Native fallback changed. Two dirty WebSkin AndroidTest files were not staged or committed.

## Verification

All checks ran against implementation commit `1157da3e182870673a77a7edf518377485cfb369`, pushed to `origin/feat/skin-first-separation`.

Focused command:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest --tests '*NativeLegacyDataReviewComposeTest' --tests '*LegacyHostMigrationTest' --tests '*LegacyMigrationTest' --tests '*BackupEnvelopeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **31/31 PASS** — 2 Native Compose tests, 1 Host migration test, 4 legacy migration tests and 24 backup envelope tests. Log `.local/android-test/reports/native-legacy-data-review-20261030/focused.log`; SHA-256 `4690C13F015F78DB57E01FD4D37EDAD6DAD7C2B99B6A62CFBCE1805BC9CBF555`.

Full command:

```powershell
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks; **768/768 JVM tests passed in 117 suites**; source boundaries, AndroidTest Kotlin compilation, APK assembly and lint passed. Full log `.local/android-test/reports/native-legacy-data-review-20261030/full-gate.log`; SHA-256 `3B7F4042F9756B88DEEAA0ABC0C1EBED67A5C007512D9217C07D3B312CE354E1`.

Debug APK `app/build/outputs/apk/debug/app-debug.apk`: 66,500,178 bytes; SHA-256 `C975DFDB5DBBE34525A517930FBD9E79FC3871C14FD015F7126694F9E9DDE01B`.

ADB had no online devices. Android instrumentation and physical/OEM backup-recovery behavior are **NOT TESTED**; this does not close SEP-14.

## Remaining scope

This closes only Native legacy migration/restore confirmation presentation. Data recovery, process death, damaged skin, WebView failure, reboot and independent recovery entry acceptance remain open. No Clear repair or skin cutover is implied.

## Git

- Implementation: `1157da3e182870673a77a7edf518377485cfb369` — `refactor(皮肤分离): 将原生旧数据确认迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.

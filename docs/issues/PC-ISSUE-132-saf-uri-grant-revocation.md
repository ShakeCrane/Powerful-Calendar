# PC-ISSUE-132 — SAF URI grant revocation fails safely

Date: 2026-10-24

Priority: P0 candidate, REC-P0-107

Status: **PASS for the narrowed Android framework URI-grant revocation boundary; PC-ISSUE-003 and the overall freeze remain PARTIAL / HOLD**

## Finding and scope

The missing evidence was an Android-enforced read-permission revocation between selecting a content URI and opening it. The previous tests covered safe permission-error wording but had not caused Android to revoke a live URI grant. This was an evidence gap; the new run found no production behavior defect.

The SAF text read was extracted into the bounded core helper SafBackupReader, and the existing PowerCalApp restore path delegates to it. An Android instrumentation fixture provides a synthetic content URI, grants read access, then revokes it. A signature-protected provider read permission ensures the post-revocation read is denied by Android itself.

The API 35 test verifies:

- Reading succeeds while the URI grant is active.
- After provider revocation, the same read throws Android SecurityException.
- RestoreFailureExplanation gives the safe permission message, including that existing data was not overwritten.
- A uniquely named temporary SQLite store is seeded with a formal event and history; event rows, history, pending drafts, identity count, and next sequence are unchanged across the denied read.
- The test closes the store and deletes only its own temporary database in finally.

This is a synthetic-provider instrumentation test. It does not exercise DocumentsUI, a real document provider's lifecycle, the restore screen, or physical/OEM behavior. No UI was opened and no existing emulator app data was cleared.

## Verification evidence

Android API 35 emulator instrumentation: **1/1 PASS** on emulator-5556. The test output states OK (1 test). Raw result log: .local/android-test/reports/freeze-20261024/pc-issue-003-saf-uri-revocation-api35-r4.log, SHA-256 16F65C8757DBD6F3ACEB578ED3F468E8FFD8DFDF6596BA2AB725ADB428AB7DAF.

Three earlier attempts exposed test-fixture setup problems, not product failures:

1. The instrumentation target UID could not grant its own provider's URI permission. Log SHA-256 D40CDB8FE79D2771A835B9E048193C9FB9BAD04F01E83614185DC1AD811CA98E.
2. The Kotlin provider fixture crashed before tests because the Android test runtime lacked kotlin.jvm.internal.Intrinsics. Log SHA-256 D9C2CE779FC9830BBDCFD3F7BFE9695C8AE640C7A3410856FFA008143207AF54.
3. The first exported-provider setup did not enforce denial after revocation. Adding a signature-protected read permission made the OS denial observable. Log SHA-256 ED4875968066D91581756F128E1F4FFE9B8E33B1AE75DC6C8C5978BB594477E0.

The full local regression command was:

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain

Result: **BUILD SUCCESSFUL**, 468/468 JVM tests across 57 suites, zero failures/errors/skips; source-boundary check, Debug APK assembly, Android test APK assembly, and lint all succeeded. Lint reported 0 errors and 58 warnings. The full run includes the pre-existing protected untracked test file; that file was not modified or staged.

- Full Gradle log: .local/android-test/reports/freeze-20261024/pc-issue-003-full-gradle.log, SHA-256 80DCF4234520072A7149455B5B96545994727B85C41FCCCE2B48B3B228F4D2BE.
- Test build log: .local/android-test/reports/freeze-20261024/pc-issue-003-androidtest-build-r5.log, SHA-256 75E25178D6205B0E617106F2CAACD4975E4303A155985386081B4B516E078F8C.
- Full-run PowerStoreTest XML: .local/android-test/reports/freeze-20261024/pc-issue-132-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml, SHA-256 67E2DD5F259FE36C46614D9E7E3C30D3D568EE3B2DF2A34358DFF123A9157B98.
- Lint XML: .local/android-test/reports/freeze-20261024/pc-issue-132-junit/lint-results-debug.xml, SHA-256 2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47.
- Debug APK: app/build/outputs/apk/debug/app-debug.apk, SHA-256 35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4.
- Android test APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk, SHA-256 93B762B8B0DB9F9574EA51BDB7A5E6C6378E6EE5E8E7BD9C019FA321AA9F6A28.

## Freeze decision

REC-P0-107 is PASS only for the reconstructed, narrowed Android framework URI-grant revocation behavior using the synthetic API 35 provider. This does not sign the real SAF picker/provider lifecycle or physical-device acceptance. PC-ISSUE-003 remains PARTIAL / HOLD, and the overall core freeze remains HOLD.

Code/test commit 0d83d48f6bea18b64e8965ab62bcb39d55f164a8 — test(备份): 验证SAF授权撤销安全失败 — is pushed. This report and the related ledger/handoff updates are committed separately.

Structural-only ledger validation: .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-132.json, SHA-256 7D3BDB2012A2838CE6CE85F55682B38C1E211EF0A3DD64AAD55C924614AD23E2; ledger SHA-256 FD79266CD97115B90641DA75F49147F6C706FDD4B60F852663DFCB2B3754AD74. It validates row structure and counts only; product_behavior_tested=false.

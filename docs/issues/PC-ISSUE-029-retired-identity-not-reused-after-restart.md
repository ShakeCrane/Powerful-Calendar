# PC-ISSUE-029 — Deleted event identity stays retired across restart

Date: 2026-10-23

Severity: P0 candidate invariants

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

Deleting a formal event must keep its permanent identity as a tombstone and retire its `eventSeq`. Later event creation must allocate a new sequence, including after the app process/database has been reopened.

The existing store already wrote tombstones and did not reuse an ID in the same process. The evidence gap was that the reconstructed candidates `REC-P0-029/030` lacked a direct restart-boundary regression. No production defect was found; this issue adds that regression.

## Change

Added `PowerStoreTest.deletedIdentityRemainsRetiredAndIsNotReusedAfterRestart` in test commit `8270a8a47d55ad3e91cbdadd4b2caf1d7daefb20` (`test(冻结): 验证删除身份跨重启不复用`). The test:

1. Commits an Activity, then deletes it through the formal DELETE draft path.
2. Verifies the active lookup hides the event while the retired lookup returns its tombstone and `isRetired` is true.
3. Closes and reopens the same isolated SQLite database; verifies the tombstone, retired state, identity count, and sequence watermark persist.
4. Creates another event draft and verifies it receives `retiredSeq + 1`, while the prior sequence remains retired and hidden from normal lookup.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database. No Android UI, emulator, or physical device was tested.

- Pre-commit targeted run: **2/2 PASS**, covering the new restart regression and existing same-process deletion/draft-cleanup regression.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-029-retired-identity-targeted-precommit-20261023.log`, SHA-256 `FF1AA6BE1243F698E52F85B624C32932D38252BE2ACE4196CEC5851EE7149591`.
  - JUnit XML SHA-256 `4C1B346F9082BF43DD7190472A1ADAA7DCC3F9B8DAC4813DA9ED75A8C6A70314`.
- Post-commit targeted run: **2/2 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-029-retired-identity-targeted-postcommit-20261023.log`, SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`.
  - JUnit XML SHA-256 `84C6F0785079DA67C5B5A8A13BBEF90EFFE8D30A8AFCC44FA14F991E22240E5F`.
- Full command: `.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 392/392 tests, 0 failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes protected untracked `DraftDeckFeedbackTest.kt` (391 tracked tests + 1 protected untracked test); that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-029-retired-identity-full-gradle-20261023.log`, SHA-256 `2FB780ECF6F7F4D2D81D9004FAE8437856514D1BCA31F7447948BADC3EB60A52`.
- Full `PowerStoreTest` XML: 45 tests, zero failures/errors/skips; SHA-256 `0731E44979C82E4C68C9DF89B03DD69EEEDD59B98CCDE77A25D4A415EC860B13`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-029` and `REC-P0-030` are marked **PASS** in the reconstructed candidate ledger based on this case-specific store regression. Structural validation JSON `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-029.json` has SHA-256 `318ADA3BAB95430120553ACB6A14988E6A879E042297A88809DB7736CC61E5AD`; it validates ledger structure, not product behavior. The regression proves tombstone and sequence-allocation persistence in JVM SQLite; it does not sign off an application delete UI or device behavior. Both candidate rows remain `UNMAPPED` to the original 209-case checklist. Overall core freeze remains **HOLD**.

## Commit and push

- Test commit: `8270a8a47d55ad3e91cbdadd4b2caf1d7daefb20` — `test(冻结): 验证删除身份跨重启不复用`.
- The independent report and ledger/verdict synchronization are committed and pushed separately after review of the documentation diff.

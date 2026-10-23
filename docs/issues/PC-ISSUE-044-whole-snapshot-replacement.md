# PC-ISSUE-044 — Whole-snapshot replacement survives restart

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

PowerCal commits a proposal as a complete formal snapshot. Fields omitted or cleared in the accepted proposal must not be repopulated by silently merging older stored values. The event identity and creation timestamp remain identity metadata while the snapshot revision advances.

The existing whole-snapshot test verified selected updates within one process. This regression verifies the replacement contract after SQLite reopen and explicitly checks that old optional field values are cleared.

## Change

Added `PowerStoreTest.wholeSnapshotReplacementPersistsAcrossRestartWithoutFieldMerge` in test commit `05aef89a181426f2a3533ffb87be9c884223bf8d` (`test(冻结): 验证整对象替换跨重启`). The test creates an Activity with an old title, location and notes, then stages a replacement with a new title, blank location/notes and disabled state. After closing and reopening SQLite, it commits the replacement and verifies those fields match the proposal without restoring old values. The event sequence and original creation time remain unchanged, start/end geometry remains as proposed, and the formal revision advances from 1 to 2.

No production code, UI, business data, or protected untracked assets were changed.

## Verification evidence

Environment: Robolectric API 34 with an isolated PowerStore SQLite database.

- Pre-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-targeted-precommit-20261023.log`, SHA-256 `2B2F1657DB15F9874550E3FCA0B879FDFED1B29E4CD9F65A68E708751A79B23F`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-044-targeted-precommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `D9B6825B549878809BFB1974F1D52138617CD3B1C0BCDFCD711BC072F28CD6B6`.
- Post-commit targeted run: **1/1 PASS**, zero failures/errors/skips.
  - Log `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-targeted-postcommit-20261023.log`, SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`.
  - JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-044-targeted-postcommit-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `F5EBF62910C96D90E341BC9A5B4B364BE7F4FD657210008D5E9C22DFA44F7A5A`.
- Full command: ` .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 407/407 tests, zero failures/errors/skips across 55 JUnit suites; lint **0 errors / 55 warnings**. This includes 406 tracked tests and the protected untracked `DraftDeckFeedbackTest.kt`; that file was not modified or staged.
- Full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-full-gradle-20261023.log`, SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`.
- Full `PowerStoreTest` XML: 60 tests, zero failures/errors/skips; SHA-256 `1F9E906660EE8FC700BE0030E176A1B0D6A871BE62A8A6EFA6BA00B829357047`.
- Debug APK SHA-256: `EA0DE99328BF66FEA302AF5A55C82B3BD7EF3F7276922979AABBF7ABEC581A53`.

## Candidate sign-off and limits

`REC-P0-051` is marked **PASS** in the reconstructed candidate ledger for full-snapshot persistence semantics. It remains `UNMAPPED` to the original 209-case checklist. The overall core freeze remains **HOLD**.

The linked structural-only ledger validation is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-044.json`, SHA-256 `8907B937B7D0B7EF8DB08204824E977FC0FBF8EE2D6C752C5BFA49D8EA0E1197`; it validates row structure only, not product behavior.

## Commit and push

- Test commit: `05aef89a181426f2a3533ffb87be9c884223bf8d` — `test(冻结): 验证整对象替换跨重启` (pushed to `origin/main`).
- This report and the synchronized ledger/verdict updates are committed and pushed separately.

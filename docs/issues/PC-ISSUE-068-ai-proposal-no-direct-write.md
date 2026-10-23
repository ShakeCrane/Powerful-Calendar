# PC-ISSUE-068 — AI proposals do not write formal events directly

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-125`)

Status: **PARSER / DRAFT SERVICE / SQLITE CORE PASS / MODEL NETWORK AND DEVICE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The reconstructed acceptance ledger required AI Create/Modify responses to become typed proposals without directly changing formal events. Existing parser and draft-service tests did not tie both operations to an assertion that formal storage remains unchanged.

## Change

- Added `PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit`.
- The test parses typed CREATE and REPLACE responses, then routes both through `AiDraftService` into the isolated PowerStore SQLite database.
- It verifies both proposals remain pending drafts, the CREATE proposal has no formal event row, and the existing formal event retains its title, revision, and start time.
- Test-only change; no production, UI, schema, user data, or design asset changed.

## Verification

Environment: Robolectric API 34, isolated PowerStore SQLite; parser and draft service are local. No model/network request or Android device was used.

- Focused test `PowerStoreTest.parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-068-ai-typed-proposal-targeted-20261024.log`, SHA-256 `102500AD61A34D7F039BAC87833C4F8C1C2DF903F122538D506BDB39921D2ACA`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-068-targeted-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `FAA4934C08CC20FC95519B8015B6B187A140968F06772A4F490647B5C5E12EED`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **420/420** tests across 55 suites, zero failures/errors/skips, lint 0 errors / 55 warnings. The run includes 419 tracked tests and one protected, untracked test; that protected file was not modified or staged. Log `.local/android-test/reports/freeze-20261024/pc-issue-068-full-gradle-20261024.log`, SHA-256 `65303061E11CDA9BDBD9CEF3A86D47E70C6A5640F69CB96C199BB847D08F5F23`; full `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-068-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `60583B79475745F337E20A8033168F5CDB45AC0219EAA7655EDC2DDA57044469`.
- Debug APK SHA-256: `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-125` is PASS for the local typed-proposal → draft-storage contract. It does not establish that a live model returns valid proposals or cover network failure, authorization UX, Android UI, or device behavior. The reconstructed candidate remains `UNMAPPED` to the absent historical checklist; overall freeze remains **HOLD**.

## Git

- Test-only commit `95f1be3ebb99fea96b063e48191e634c0fda1dbc` (`test(AI): 验证提案不直接写入正式事件`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.

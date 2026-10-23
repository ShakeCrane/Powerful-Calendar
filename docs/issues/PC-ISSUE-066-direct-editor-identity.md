# PC-ISSUE-066 — Direct editing preserves event identity

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-057`)

Status: **CORE EDIT SESSION / STORAGE CONTRACT PASS / UI NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The reconstructed ledger had no row-level evidence for `REC-P0-057` (“Open editor and type; do not allocate a replacement draft or sequence”). The existing `EditSession` regression exercised typing and checkpoints, but did not explicitly assert the event-identity count and sequence watermark across those steps. No production defect was reproduced; the gap was in the strength and traceability of the core regression.

## Change

- Strengthened `PowerStoreTest.directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision` with assertions that opening/editing in memory creates no pending draft and does not advance identity count or sequence watermark.
- The first checkpoint creates one draft tied to the original `eventSeq`; repeated checkpointing reuses its `draftId`. Discard and a no-op Save leave the identity count and sequence watermark unchanged.
- No production code, UI, database schema, user data, or design asset changed. This test does not exercise screen rendering, IME behavior, or an Android device.

## Verification

Environment: Robolectric API 34 with isolated PowerStore SQLite.

- Focused test `PowerStoreTest.directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-066-direct-editor-identity-targeted-20261024.log`, SHA-256 `849A056CBF9FB44EC628A10B71D54E455DE3C5778917D6ABFF42799B1DA4F9EB`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-066-direct-editor-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `2B37CCF0D5A58BAF1CF8330BE141CAE527061E6AA72A78B3F3295FA7EC44B1E6`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, **418/418** tests across 55 suites, zero failures/errors/skips, lint 0 errors / 55 warnings. Log `.local/android-test/reports/freeze-20261024/pc-issue-066-full-gradle-20261024.log`, SHA-256 `ACCF4E819C5284768AA3156BF4567BAB580B7196B82B7866CD5242A8635CFF51`; full `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-066-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `A23218A90D838D9BF9F9D20EC24CFF0C998DD3CB24E346AA07459F3A0E849D0E`.
- Debug APK SHA-256: `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-057` is PASS for the direct-edit/session storage identity contract. UI editor behavior and device-level behavior remain untested. The candidate remains `UNMAPPED` to the absent historical checklist, and the overall freeze remains **HOLD**.

## Git

- Test-only commit `217b2999ec5ec57e34731bcaabf05edc3c3ff26d` (`test(编辑): 验证编辑暂存保持事件身份`) is pushed to `origin/main`.
- This report and the synchronized ledger/verdict updates are committed separately.

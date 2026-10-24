# PC-ISSUE-125 — AI proposal dependent-write rollback

Date: 2026-10-24

Status: **PASS for the narrowed per-proposal PowerStore transaction; overall freeze remains HOLD**

Case: `REC-P1-059` (reconstructed; unmapped to the historical checklist)

## Finding and scope

The approved core contract requires dependent writes to roll back atomically. In this narrowed check, the dependent write group for one AI CREATE proposal consists of its formal object, commit history, desired schedule intent, and draft finalization. `AiBatchRunner` sends each selected draft through `PowerStore.commit`, whose persistence work is within one SQLite transaction.

The existing SQLite fault-injection regression already failed a selected proposal's `schedule_intents` insert and showed that the formal object and schedule intent were absent while the draft remained pending. The evidence gap was that it did not explicitly assert absent commit history before and after reopening SQLite. Two assertions now cover that gap. No production defect was found and no production code changed.

This does not establish atomic orchestration across multiple proposals that have a separate dependency relationship. Such a relationship/group identifier is not represented in the current AI proposal model; the sign-off is intentionally limited to one proposal's dependent persistence effects. Independent proposals still partially succeed as covered by PC-ISSUE-095.

## Verification

Fault injection aborts `schedule_intents` insertion for the middle CREATE proposal in a three-item authorized batch. The test verifies:

- the failed proposal leaves no formal event, commit history, or desired schedule intent;
- its draft remains pending before and after SQLite reopen;
- the two independent neighboring proposals commit successfully;
- the failure report and batch FAILED state persist, the lock releases, the old authorization is fenced, and a newly authorized retry succeeds.

Targeted command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.PowerStoreTest.theAiRunnerContinuesIndependentItemsAfterOneCommitFailureAndPersistsPartialReport' --offline --rerun-tasks --console=plain
```

Result: **1/1 PASS**, Robolectric API 34 with isolated PowerStore SQLite; no model, network, UI, emulator, or physical device.

Full command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain
```

Result: **465/465** unit tests across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings; `BUILD SUCCESSFUL`. The full run includes the protected untracked `DraftDeckFeedbackTest.kt`, which was not staged.

- Targeted log: `.local/android-test/reports/freeze-20261024/pc-issue-125-dependent-write-rollback-targeted.log`, SHA-256 `5D1781877349FFE25BFEE8C242E85D26DAEDDA0C7562C8C8AED498080D5D926C`.
- Full log: `.local/android-test/reports/freeze-20261024/pc-issue-125-full-gradle.log`, SHA-256 `618C5E202BB190B3554E585F543C6B6FE8A778E8ED719C30741A5D181BB9CEAB`.
- `PowerStoreTest` JUnit XML: `app/build/test-results/testDebugUnitTest/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, 87/87, SHA-256 `6A517A62AB7B1A52D5F6C085C662FB7B932981D807F03FEE3CC3880F69EBF634`.
- Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `C5F20B47128753D1130E87D0B15885E5CDF9C6F6C755ACBCE50A6AB72BAFA05D`.

## Git

- Test-only commit `237486011a64a4598b3dd7a3183ee2c09314f034` — `test(冻结): 验证AI提案依赖写入原子回滚` — pushed to `origin/main`.
- This report and the corresponding narrowed ledger sign-off are committed separately.
- Structural-only ledger artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-125.json`, SHA-256 `49EB75C9641F72F273EA5C4532EE95B9B30E8EC67D69D273315FF33EFB59AEA0`; ledger SHA-256 `DB65C04E298584BB31EEB1B29DC0985CF2069A9FB27CC63382FDA3EB7DFA1452`. It checks ledger structure, not product behavior.

`REC-P1-059` is PASS only for one AI proposal's dependent SQLite writes. Cross-proposal dependency orchestration, live AI behavior, UI, Android scheduler delivery, historical case mapping, and physical-device behavior are not claimed. Overall core freeze remains **HOLD**.

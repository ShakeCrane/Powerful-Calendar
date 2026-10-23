# PC-ISSUE-065 — Attached Alarm Trigger keeps one Activity identity

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-024`)

Status: **CORE STORE/IDENTITY CONTRACT PASS / TIMELINE RENDERING NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The reconstructed candidate ledger had no one-to-one evidence entry for `REC-P0-024`. An existing PowerStore/Robolectric regression directly exercises the identity contract: commit an `ACTIVITY` with `ReminderMethod.ALARM`, verify its schedule intent references the same `eventSeq`, confirm the formal record remains `ACTIVITY`, and confirm the store still contains exactly that one event identity.

The current baseline passes this contract. No product defect was reproduced; the gap was candidate-to-test traceability. This signs off the storage and identity invariant only. It does not test how an attached Alarm projection is rendered on the timeline.

## Change

- No production or test source changes were needed. The existing direct regression was rerun and mapped to `REC-P0-024`.
- No UI, schema, user database, design asset, or device state was changed.

## Verification

Environment: Robolectric API 34 with isolated PowerStore SQLite. No Android device was used.

- Focused test `ReminderPersistenceTest.activityCanChooseExactAlarmOrMessageIndependentlyOfKind`: **1/1 PASS**, zero failures/errors. Log `.local/android-test/reports/freeze-20261024/pc-issue-065-attached-alarm-targeted-verified-20261024.log`, SHA-256 `1405EEECED12A404006A25234E8C66666168CBEA38227A01B22EF4071EF9DDDD`; JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-065-targeted-junit-verified/TEST-dev.powerfulcalendar.core.ReminderPersistenceTest.xml`, SHA-256 `238567F02EE4E5ADE257452CFAC995220CFBABDD7ADE2634F4FB1E9EEB2514CA`.
- The same test is present in the full `ReminderPersistenceTest` XML at the current code/test baseline: `.local/android-test/reports/freeze-20261024/pc-issue-064-full-junit/TEST-dev.powerfulcalendar.core.ReminderPersistenceTest.xml`, SHA-256 `B993FE8DE523803F3D08FB3B514EC47C5E6355125354E52E0FD91046B416D531`.
- Latest full command at that unchanged code/test baseline: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, **418/418** tests across 55 suites, zero failures/errors/skips; lint 0 errors / 55 warnings. Log `.local/android-test/reports/freeze-20261024/pc-issue-064-full-gradle-20261024.log`, SHA-256 `B8C47A004349B2991582682DFB7A994604119DD511E57765EAFFB6A80B4D164B`.
- Debug APK SHA-256: `DB944538964299348D31E8981D6A2F701F420FE74E60B508B8C53B253EC0517D`.

`REC-P0-024` is PASS for the no-duplicate-formal-identity store contract and remains UNMAPPED to the absent historical checklist. Timeline rendering and device behavior remain untested. The overall freeze remains **HOLD**.

## Git

- Current tested code/test baseline: `f497330abdd5851752f65f0e53112709a382b2b9`, pushed to `origin/main`.
- No code or test source commit was needed for this sign-off. This independent report and synchronized ledger are committed separately.
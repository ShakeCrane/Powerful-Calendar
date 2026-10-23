# PC-ISSUE-087 — Completion tracking and reminder state independence

Date: 2026-10-24

Candidate: `REC-P0-017` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE STATE-ISOLATION EVIDENCE PASS / REC-P0-017 NOT TESTED / overall freeze HOLD**

## Finding

The candidate ledger described this action as “Pause object,” but that term has no matching behavior in the approved contract or core model. The sourced requirement is `CORE_CONTRACT_FREEZE_2026-10-21.md` §Invariant 2: completion tracking, reminder scheduling, and event enabled state are independent. The reconstructed candidate was narrowed to this explicit core invariant; no pause workflow was invented.

The existing regression preserved the event enabled flag when completion tracking changed, but did not check the durable desired reminder intent through SQLite reopen.

## Change

- Added `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart`.
- Exercises both tracking transitions (`false → true` and `true → false`) on enabled Activity records with pending reminder intents.
- Verifies enabled state, reminder policy and completion keys remain unchanged; the desired intent keeps the same occurrence key, trigger instant, requirement and pending state at the updated record revision.
- Reopens the same SQLite database and rechecks both records, intents, history and identity watermarks.
- Test-only change; no production logic, UI, schema or business data changed.

## Verification

- Focused Robolectric API 34 regression: **1/1 PASS**. Log SHA-256 `486ED3A39D00EFC5B7ECAE685292F3D1CCD365BEA6AB16A6A2D9665FCC6EFBE8`; JUnit XML SHA-256 `395F805708218E12ED551E8FDD0C667E832F28A32D30677B8D1C6CEC6EBF672C`.
- Full Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **442/442 tests across 56 suites; zero failures, errors or skips; BUILD SUCCESSFUL; lint 0 errors / 58 warnings**. The working-tree run included the protected untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged. Full log SHA-256 `F36A991D37DF9CEEABD17623CFC7175740EFC05F51DDCCA882EB9ACF66FCB4CF`.
- Full `PowerStoreTest` XML SHA-256 `EE53294E5444E61C30CED7F16EF4915D323A9D4214FF165D5FD0F0C6F4EE887E`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`.
- Logs and copied JUnit XML are under `.local/android-test/reports/freeze-20261024/`.

## Git and disposition

- Test commit `5b610b7bc48951e1205cf18cc72fe83782d93fac` — `test(状态): 验证跟踪切换不改变提醒意图` — is pushed.
- `REC-P0-017` remains **NOT TESTED**. This is PowerStore/SQLite evidence; it does not cover Android system scheduling/delivery or physical/OEM behavior.
- Overall core freeze remains **HOLD**. Continue with the next feasible non-UI core acceptance item.

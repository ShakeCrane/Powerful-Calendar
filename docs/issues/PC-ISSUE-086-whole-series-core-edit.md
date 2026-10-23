# PC-ISSUE-086 — Whole-series recurring edit core persistence

Date: 2026-10-24

Candidate: `REC-P0-080` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE EDIT PERSISTENCE EVIDENCE PASS / REC-P0-080 NOT TESTED / overall freeze HOLD**

## Finding

The existing `EditSession.save()` path persists a recurring rule change on the existing formal event. A focused regression was needed to verify that changing the whole stored recurrence rule preserves the series identity and original time anchor, and that the changed rule and its occurrence projection survive SQLite reopen.

This test exercises the core persistence path. It does not exercise a user selecting “whole series” as an edit scope, so it is partial evidence for `REC-P0-080`, not a candidate sign-off.

## Change

- Added `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`.
- The test changes the series title, finite count and exception set through `EditSession`, then verifies the same `eventSeq`, unchanged `startMillis`, one allocated identity, revision/history behavior, and the expected remaining occurrence keys.
- Reopens the same SQLite database and verifies the persisted record, occurrence projection, identity count, sequence watermark and history again.
- No production logic, UI, schema or business data changed.

## Verification

- Focused Robolectric API 34 regression: **1/1 PASS**. Log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; JUnit XML SHA-256 `25D33D615FBE7DD1FDD0AF36BA4727C467EE698B0139F69BA25546FCC129F2CE`.
- Full Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **441/441 tests across 56 suites; zero failures, errors or skips; BUILD SUCCESSFUL; lint 0 errors / 58 warnings**. This working-tree run also included the protected untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged. Full log SHA-256 `1C898C47EFC096A0A6919609F543DE3FC3B25F63E0FA19EF8B263DA9DC602173`.
- Full `PowerStoreTest` XML SHA-256 `85915BA9F802BA8D24B664A408ECC6A42CD74ED92448D3EAC23A79C61ADC7B92`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`.
- Logs and copied JUnit XML are under `.local/android-test/reports/freeze-20261024/`.

## Git and disposition

- Test commit `49f334e8cea1005de59ef9b98a16e32f544b937e` — `test(重复): 验证整组编辑保留序列锚点` — is pushed.
- `REC-P0-080` remains **NOT TESTED**. The explicit whole-series scope selection and its call path are not covered; neither API 35 nor physical/OEM execution was performed. The core persistence regression is retained as partial evidence only.
- Overall core freeze remains **HOLD**. Continue with the next feasible non-UI core acceptance item.

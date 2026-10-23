# PC-ISSUE-088 — Preserve date-only Deadline precision when completing

Date: 2026-10-24

Candidate: `REC-P0-023` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE DATE-PRECISION EVIDENCE PASS / REC-P0-023 NOT TESTED / overall freeze HOLD**

## Finding

The acceptance candidate asks that completing a Deadline must not render it as a START–END interval. Per the current user scope, this issue adds no UI work. It verifies the core data invariant underneath that presentation: completing a date-only Deadline must preserve its `DATE` precision and must not synthesize instant or interval fields, including after SQLite reopen.

## Change

- Added `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart`.
- Commits a tracked date-only Deadline for `2026-10-24`, completes its `single` occurrence, and checks that `deadlineDate` remains unchanged while `deadlineMillis`, `startMillis` and `endMillis` remain null.
- Reopens the same SQLite database and verifies the completed record, precision, identity watermark and history.
- Test-only change; no production logic, UI, schema or business data changed.

## Verification

- Focused Robolectric API 34 regression: **1/1 PASS**. Log SHA-256 `09908CFC9350A75119B589F1AD665E2B14626B6AF39C71ED619B127F89FBF43F`; JUnit XML SHA-256 `3EFDF79FEAA9ED6862967256710C40E1522272741CAC74B9349D000F6E02CDB5`.
- Full Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **443/443 tests across 56 suites; zero failures, errors or skips; BUILD SUCCESSFUL; lint 0 errors / 58 warnings**. The working-tree run included protected untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged. Full log SHA-256 `84C5052C61812EFEE91155A59BFC42364DC3BEEB01701EA4DD1593F6474998CE`.
- Full `PowerStoreTest` XML SHA-256 `C934F3A7AEC882E27A29B7043B3C93FDC19EFC23C6F03408E897F7BFE5F2B3D5`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`.
- Logs and copied JUnit XML are under `.local/android-test/reports/freeze-20261024/`.

## Git and disposition

- Test commit `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8` — `test(截止): 验证日期型 Deadline 完成后精度保留` — is pushed.
- `REC-P0-023` remains **NOT TESTED**. The storage regression does not verify how any UI renders the Deadline; Android API 35 and physical/OEM behavior were not tested.
- Overall core freeze remains **HOLD**. Continue with the next feasible non-UI core acceptance item.

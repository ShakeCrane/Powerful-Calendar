# PC-ISSUE-089 — ICS read projection preserves the single formal event source

Date: 2026-10-24

Candidate: `REC-P0-082` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE ICS READ-PROJECTION EVIDENCE PASS / REC-P0-082 NOT TESTED / overall freeze HOLD**

## Finding

The stable data contract names SQLite as the sole source of formal event truth. ICS export is a projection of formal Activity records. This issue adds a core regression for that projection: exporting and parsing the current SQLite records must not create another formal event or mutate the stored event, history, reminder intent, identity count, or sequence allocator.

## Change

- Added `PowerStoreTest.icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart`.
- Commits one formal Activity, exports `store.all()` through `IcsCodec.exportActivities`, and parses the result. The parsed ICS contains exactly one event matching the formal title and start/end times, with no parse warnings.
- Checks that the formal rows, history, reminder intent, identity count, and sequence preview remain unchanged by the projection; closes and reopens the same SQLite database and checks the same state again.
- Test-only change. No production logic, UI, schema, or business data changed.

## Verification

- Focused Robolectric API 34 regression: **1/1 PASS**. Test XML SHA-256 `F6CC86C486E178FF119E8AB730626FDD0C7F940B557A7480DEBE850D68D051F1`; log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`.
- Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **444/444 tests across 56 suites; zero failures, errors, or skips; BUILD SUCCESSFUL; lint 0 errors / 58 warnings**. The run includes protected, untracked `DraftDeckFeedbackTest.kt`, which was not modified or staged. Full log SHA-256 `EE9DA7BC1A810D3C706DAA44F7CBE0D3EED6C5557572F5690F50C45621BFE44D`.
- Full `PowerStoreTest` XML SHA-256 `208C1EA21327775A8F10E501B284E09EF5D5ACA5CEDD495D17D9E44790C95592`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `86D48004E3CE00E94A459A4CCAA929A9B55F8816DDBE7E10D6E9937D0C11ED94`.
- Logs and copied JUnit XML are under `.local/android-test/reports/freeze-20261024/`.

## Scope and disposition

- `REC-P0-082` remains **NOT TESTED**: this evidence covers only the core SQLite → ICS export/parse projection. It does not exercise UI or AI projections, Android SAF/external calendar interaction, or device behavior.
- The 209 historical case IDs remain unavailable and unmapped. This result cannot establish their historical sign-off.
- Overall core freeze remains **HOLD**.

## Git

- Test commit `13e720bce54d0b31a179e68e9fa1a86eec518c61` — `test(互操作): 验证 ICS 投影不生成第二事件真相` — is pushed.

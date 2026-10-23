# PC-ISSUE-084 — Persist a single recurring-instance override

Date: 2026-10-24

Candidate: `REC-P0-078` (reconstructed P0; historical mapping remains `UNMAPPED`)

Status: **CORE INSTANCE-ISOLATION PARTIAL / REC-P0-078 NOT TESTED / overall freeze HOLD**

## Finding

The recurrence model already represents moved instances as overrides keyed by their immutable original slot. Existing tests covered projection in memory and another test covered persisted skips, but there was no focused regression proving that moving one occurrence through `EditSession` persists only that override across a SQLite reopen.

## Change and evidence

Added `PowerStoreTest.editingOneRecurringOccurrencePersistsOverrideWithoutChangingOtherSlotsAcrossRestart`. It creates a five-slot daily series, moves only the October 21 occurrence from 07:30 to 09:15 through the formal edit path, and verifies that:

- the event identity, series anchor, and original occurrence keys remain stable;
- the selected occurrence projects at 09:15 while every other occurrence keeps its original time;
- closing and reopening the same SQLite database preserves the formal record and identical occurrence projection;
- the edit creates one revision and one history entry without creating another event identity.

This is Robolectric API 34 core PowerStore/SQLite evidence. Production code did not change. The test supplies the selected original slot explicitly. It does not verify the default-scope choice, UI wiring, this-and-future edits, or whole-series edits; therefore `REC-P0-078` remains **NOT TESTED** rather than PASS.

## Verification

- Focused `:app:testDebugUnitTest --tests dev.powerfulcalendar.core.PowerStoreTest.editingOneRecurringOccurrencePersistsOverrideWithoutChangingOtherSlotsAcrossRestart --offline --rerun-tasks --console=plain`: **1/1 PASS**. Log SHA-256 `6569CF98DBA20A6FB6E4AA8A38336AA7DB2AD127CDD62CD2DE65415ACD079971`.
- Full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain`: **436/436 tests across 56 suites; zero failures, errors, or skips; APK build successful; lint 0 errors / 58 warnings**. The working-tree run included one pre-existing protected, untracked test file; it was not modified or staged. Full log SHA-256 `B0E5EEA4CDEC0BA7CF37CAE53EE85E287C1813A3397D876FE6DCACBC9110B4A8`; full `PowerStoreTest` XML SHA-256 `F1FC2ECC8C55AE1CF2BCB63022A752B61C9010EB8875AE32EE8A50F111625C1E`; lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git and disposition

- Test-only commit `6ff8afb3fa4696d07f06981c83d00ad58abb8cbb` (`test(重复): 验证单次实例改期隔离持久化`) is pushed to `origin/main`.
- This closes the core persistence evidence gap for an explicitly selected single occurrence. It does not sign off the full reconstructed candidate. The overall core freeze remains **HOLD**.

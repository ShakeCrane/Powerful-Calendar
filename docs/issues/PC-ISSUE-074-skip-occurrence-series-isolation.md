# PC-ISSUE-074 — Skip one occurrence without changing its recurrence series

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-120`)

Status: **CORE SQLITE PERSISTENCE PASS / API 35 EMULATOR AND PHYSICAL DEVICE NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The acceptance candidate requires skipping one occurrence while preserving the rest of its series. The existing recurrence projection test covered omission and later-slot stability in memory, but did not cover saving the exception to the formal record or reopening SQLite. This was an evidence gap; no production defect was found.

## Change and evidence

Added `PowerStoreTest.skippingOneRecurringOccurrencePersistsWithoutChangingTheSeriesAcrossRestart`. The test creates a five-slot daily Alarm series, records its original occurrence keys, adds one exception through `EditSession.save()`, then closes and reopens the same isolated SQLite database. It verifies:

- the same `eventSeq` and start anchor remain;
- the formal revision advances exactly once;
- the full recurrence rule equals the original rule with only the selected key added to `exceptions`;
- projection contains every original key except the selected one, including all later keys;
- after database reopen, the record and projected keys remain identical, with one identity and two commit-history entries.

Environment: Robolectric API 34 with isolated PowerStore SQLite. This signs the core persistence contract. It does not claim API 35 emulator, Android UI action wiring, AlarmManager delivery, or physical-device behavior. The reconstructed candidate remains `UNMAPPED` to the missing historical case list.

## Verification

- Focused test: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-074-targeted-final-20261024.log`, SHA-256 `A6DF8B1298DF20686233F1FFA0F4DF397F3B1FF351D2B596C4816AC5F9B46A7C`; focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-074-targeted-final-PowerStoreTest.xml`, SHA-256 `DF9C6F6B870D2FB8F0924A5E77EEBE8322C7CA3323EDAE3FFC7A9B53441CA295`.
- Full command: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **426/426 tests across 55 suites, zero failures/errors/skips; APK build successful; lint 0 errors / 58 warnings**. Full log `.local/android-test/reports/freeze-20261024/pc-issue-074-full-gradle-freeze-20261024.log`, SHA-256 `6DBE2C449A70D4EC5076AA1BD70CFD6618DA1208AC7DA20A782F1649577B15EC`. Full `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-074-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `0708D187CD30FC7B5ED10DA53B28B93056ADEFB6C3402DA1D8D03CE8AA771FF5`. All 55 suite XML files and lint XML are retained under the same ignored evidence directory.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439` (unchanged from the prior core baseline).

## Git

- Test-only commit `531186ec99046290116b5e63c8aabd57914e8558` (`test(核心): 验证单次跳过不改动重复序列`) is pushed to `origin/main`.
- No production or UI code, business data, or design assets changed.

`REC-P0-120` is signed PASS for the tested core SQLite persistence contract. API 35 emulator and physical-device acceptance remain untested; the overall freeze remains **HOLD**.

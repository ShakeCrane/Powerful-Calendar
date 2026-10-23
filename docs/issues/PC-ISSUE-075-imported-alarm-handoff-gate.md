# PC-ISSUE-075 — Keep an imported old-device Alarm disabled until handoff

Date: 2026-10-24

Priority: P0 candidate (`REC-P0-121`)

Status: **CORE IMPORT SAFETY PASS / API 35, OEM INVENTORY, AND END-TO-END HANDOFF NOT TESTED / OVERALL FREEZE HOLD**

## Finding

The product baseline requires imported Alarm records to remain disabled until the old-clock handoff is verified, preventing duplicate ringing. The existing `PowerStore` import path already defaults `alarmNeedsHandoff` to true, stores the imported Alarm disabled, and writes no desired schedule for a disabled record. Its regression verified the immediate disabled state and idempotency, but did not check the stored schedule state or behavior after SQLite reopen. This was a test-coverage gap; no production defect was found.

## Change and evidence

Extended `PowerStoreTest.structuredImportIsIdempotentAndDisablesImportedAlarms`. The source record is enabled, uses an exact Alarm reminder with zero-minute offset, and has a future trigger time, so an accidental enabled import would produce a durable schedule intent. The test verifies:

- default structured import returns `enabled=false` and stores a disabled formal record;
- no desired schedule intent is created;
- repeating the same `(source, externalId)` import remains idempotent and leaves one identity/history entry;
- after closing and reopening the same isolated SQLite database, the record remains disabled, no intent exists, and the same single identity/history entry remains.

This signs the core storage gate for structured Alarm imports. It does not verify that a real old OEM clock has been disabled, that a handoff confirmation workflow is exercised, or that Android `AlarmManager`/OEM delivery behaves as intended. The reconstructed candidate remains `UNMAPPED` to the missing historical case list.

Environment: Robolectric API 34 with isolated PowerStore SQLite. The candidate's API 35 emulator, physical-device, OEM alarm inventory, and end-to-end handoff paths remain untested.

## Verification

- Focused test: **1/1 PASS**. Log `.local/android-test/reports/freeze-20261024/pc-issue-075-targeted-20261024.log`, SHA-256 `1570009954AC1830CADD7CE88D15C26E82EE7AE5F79CD0590C9CAB5C224C964D`; focused JUnit XML `.local/android-test/reports/freeze-20261024/pc-issue-075-targeted-PowerStoreTest.xml`, SHA-256 `783DD548AA6A668294F614BA61454E3C192D2D72CAC5F5EB0F5C07664DF15F6D`.
- Full command: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **426/426 tests across 55 suites, zero failures/errors/skips; APK build successful; lint 0 errors / 58 warnings**. Full log `.local/android-test/reports/freeze-20261024/pc-issue-075-full-gradle-20261024.log`, SHA-256 `7C86322AC0231F9DDE9721CA5CBD07F3584D8AA4927775F599A0E8723FC3805F`. Full `PowerStoreTest` XML `.local/android-test/reports/freeze-20261024/pc-issue-075-full-junit/TEST-dev.powerfulcalendar.core.PowerStoreTest.xml`, SHA-256 `B580DD16E60CA1B90404CC913C2E608A571F0A142409E90A0035E3813472FF61`. All 55 suite XML files and lint XML are retained in the same ignored evidence directory.
- Debug APK SHA-256: `59FB0B18B5EDC6F02370D1ABE309F2376842F33B32BFEA7C371E1F0DF4290439`.

## Git

- Test-only commit `25f69e1dc6ffa94078e7797e7034f881150be1c3` (`test(迁移): 验证导入闹钟保持停用`) is pushed to `origin/main`.
- No production or UI code, business data, or design assets changed.

`REC-P0-121` is signed PASS for the tested core import/storage contract. Real-device safe handoff remains unverified; overall freeze remains **HOLD**.

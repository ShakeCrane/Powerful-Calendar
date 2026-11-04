# PC-ISSUE-QA-SYSTEM-CALENDAR-IMPORT-ROBOLECTRIC-CONTENTION — import test completion barrier

日期：2026-11-04

状态：F-03 closure **PASS**. The issue was a test synchronization gap; product Store and import behavior were not changed. Product Cutover remains **HOLD**.

## 根因与修复

`SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` waited until the `migrations.finished_ms` row became visible, then opened a separate read-only SQLite connection to assert `commits` remained unchanged. In `PowerCalApp.importSystemCalendar`, persisting the terminal migration row precedes the final projection reload, reminder reconciliation, and Host notice. The test's database-row signal therefore did not mean the asynchronous Host operation had finished. Robolectric reported `SQLITE_BUSY` at the independent `SELECT COUNT(*) FROM commits` connection in two prior full-suite runs.

The regression now waits for the Host terminal notice (`hostNotice` containing `有 2 条未导入`) before reading Store invariants. That notice is set after the import's trailing reload and reconciliation. All record, identity, sequence, pending-draft, and commit-count assertions remain. No sleep, retry, Store change, or production behavior change was introduced.

The failing stack identified the blocked read connection, not the transaction holding the lock. The incomplete test completion barrier is confirmed by source ordering; the specific Robolectric connection that owned the lock was not directly instrumented. This is classified as test synchronization contention, with no evidence of a production Store deadlock.

## 验证

- Targeted test after the change: **1/1 PASS** (`focused-lock-sync-fix-1.log`).
- Complete `:app:testDebugUnitTest`: **967/967 PASS**, 140 XML suites, 0 failures/errors/skips (`full-jvm-gate.log`). The relevant classes passed: `SystemCalendarImportTest` 4/4 and `WebSkinHostProjectionTest` 24/24.
- Serial full Gradle gate: both source boundaries, unit tests, Debug APK, lint (**0 errors / 56 warnings**) and AndroidTest Kotlin compilation **PASS** (`full-gradle-gate.log`).
- API 35 AVD `PowerCal_Wave2_QA35_20261104`, serial `emulator-5554`: month Native/Clear parity methods 3/3 PASS; two separately prepared → force-stopped → restored cross-month runs passed with new process identities; WebView recurrence open/single-scope test 1/1 PASS. Store snapshots remained unchanged during read/navigation/open checks.
- Debug APK SHA-256: `F5E5FA6FE4194AD213EF60D5933018422A818D0AFB97E7B68FD223B3F54E3522`. Default package SHA-256: `299E4BDD78C8936F6C15DB0200172E659AAA5B0E0643F0D70C115966AC35A9F4`.
- Evidence: `.local/android-test/reports/F-03-20261104-wave2/`.

Physical/OEM devices remain **NOT TESTED**. F-03 closes its task gate; SSA-03 remains PARTIAL until its Studio and broader product-acceptance gaps are closed.

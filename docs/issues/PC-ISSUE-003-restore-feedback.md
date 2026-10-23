# PC-ISSUE-003 — Nonempty restore feedback

Date: 2026-10-23 | Severity P2
Status: **PARTIAL / HOLD** — SAF UI checks pass on Android 15/API 35 emulators; physical-device and OS-level permission-denial acceptance remain unverified.

## Root cause
Nonempty-backup restore refusal depended on parsing/localizing generic `IllegalArgumentException` text and the Compose front-end sometimes rendered `IllegalArgumentException` instead of a cause with actionable advice.

## Change
- The authoritative `PowerStore.restoreBackup()` now raises `NonEmptyRestoreTargetException` at **both** the preflight check and the transaction-local recheck. Neither check is removed; existing data is still never overwritten.
- `RestoreFailureExplanation` recognizes typed errors through wrapped cause chains. It tells users to export existing data first and restore only to a known-empty app database.
- Known invalid JSON, failed hash check, and SAF security permission issues have specific safe explanations. Unknown errors no longer disclose provider/file exception internals.
- Both normal restore and consented old-format restore entry points use the same explanation.

## Tests
- Fix commit: `92f083b44582c4485bc8ab8a0d4a0505691ce14a` (`fix(backup): explain nonempty restore refusal without exposing internals`).
- `RestoreFailureExplanationTest`: **5/5 PASS**. `BackupEnvelopeTest`: **15/15 PASS**, including data-bearing round trip and schedule rebuild, hash tampering before writes, transaction rollback, legacy consent, and refusal for nonempty targets.
- Current full Gradle verification: **345/345 PASS**, 0 failures/errors/skips; boundary, APK assembly and lint succeeded (0 lint errors / 56 warnings). Log: `.local/android-test/reports/freeze-20261023/issue001-vtimezone-full-final.log` (SHA-256 `E5F5001B73851E752FB46C33C7B197495027F0E6DD951CB2D84D23561611C6F5`).
- Test APK `905 / 0.9.5-rc.1`, SHA-256 `A2CED9A71952716D2DA7870D7963F5085167EE586497DD6E2959CE671B8DA0D4`.

## Android SAF evidence

- `emulator-5556` is a new Android 15/API 35 AVD with independent userdata. Only its hardware `config.ini` was copied; no app database, snapshot, or user data was cloned. It started with zero identities, objects, drafts, commits, schedule intents, or executions.
- Native Envelope export returned the app's “SHA-256 匹配” read-back success message. The 481-byte empty Envelope is `.local/android-test/reports/freeze-20261023/issue003-empty-envelope.json` (SHA-256 `2F5F86DB0A6BBB9A918042B40B697CED58961D4E45A6222A441959B19392DF36`). Restoring it through SAF succeeded: every payload table remained empty and the database lineage matched the envelope.
- A test copy with only `payload.nextSeq` changed and the original hash retained was rejected with the integrity-failure explanation; the target remained empty and retained its original lineage. Evidence: `.local/android-test/reports/freeze-20261023/issue003-tampered-envelope.json` and screenshot `issue003-tampered-rejection.png` (SHA-256 `C37AB698D887EADFD319443BBCC01250DA1191D78A06C2996D028AE2FED9DA73`).
- Invalid JSON was rejected with a safe, actionable message; all business and schedule tables remained empty.
- A synthetic empty flat v2 file displayed the explicit warning about missing SHA-256/lineage and required the “仍要恢复” action. After confirming, restore succeeded as a new lineage; the database remained empty. Prompt screenshot: `issue003-legacy-consent.png` (SHA-256 `628798F7D16CC3E3DCB4801CDF0252B8BE7293B96D335717B074CC875D4BDEB0`); result screenshot: `issue003-legacy-success.png` (SHA-256 `731712771F92DC49C4A602A00D8731BB8AFE40C2D2D5304E698BCF3DA49BD2C4`).
- On existing `emulator-5554`, a two-byte `{}` probe was refused with the nonempty-target explanation. The database had 8 active objects before and after. A second per-table comparison showed all 11 other business/authority tables unchanged; the four `schedule_intents` rows kept the same semantic SHA-256 (`a972b7dda0f48c4cdc934661daee28ac9f19346759b6adf91cfc08928ab69ed3`). Only `updated_ms` changed because returning from DocumentsUI triggers the app's normal `ON_RESUME` reminder reconciliation. Opening and cancelling the same picker without restoring reproduced the timestamp-only update. Evidence is under `.local/android-test/reports/freeze-20261023/issue003-nonempty-semantic-*`, `issue003-nonempty-repeat-*`, and `issue003-resume-control-*`; the read-only fingerprint script is `.local/android-test/scripts/issue003_database_fingerprint.py`. Refusal screenshot: `issue003-nonempty-refusal.png` (SHA-256 `0CA865C1BC321B827C07B5AC16A9D25D00D434BAC0DAD944E94A38E14DD3EAE5`).

## Limits
No physical Android device was attached (`adb devices -l` listed emulators only). An actual SAF permission-revocation failure was not induced; its safe explanation is covered by unit tests. Data-bearing restore, schedule rebuild, and mid-transaction rollback are covered by JVM tests, while the real SAF successful restore used an empty Envelope. The existing `emulator-5554` was never cleared or restored over. Keep PC-ISSUE-003 **PARTIAL / HOLD** until physical-device coverage and an OS-level denied-permission SAF case are available.

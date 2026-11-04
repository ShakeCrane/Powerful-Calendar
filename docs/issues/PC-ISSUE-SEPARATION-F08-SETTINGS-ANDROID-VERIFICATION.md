# PC-ISSUE-SEPARATION-F08-SETTINGS-ANDROID-VERIFICATION

- **Date:** 2026-11-04
- **Status:** F-08 task verification PASS; SSA-09 and SSA-10 remain PARTIAL
- **Base:** `82b456fd2bfc15df379fd15d967fac3da57b899d`
- **Evidence:** `.local/android-test/reports/F-08-20261104/manifest.md`

## Finding and resolution

The first broad `ClearSettingsOptionsInstrumentedTest` run did not establish a product failure. Its combined malformed/future-schema restore test timed out during a later DocumentsUI flow, and its future-schema assertion expected an outdated message. The actual API rejection was `INVALID_BACKUP`, surfaced as “备份格式或内容无效”. The same broad run also exposed an AI attachment-history ordering assertion outside F-08.

The restore test was split into separate malformed-JSON and future-schema cases. The latter now asserts the current API error mapping; a targeted visible-text diagnostic is included for a future failure. Both cases passed separately on the isolated API 35 AVD. No product, Core, permission, import/export, or default-skin behavior was changed.

An Android instrumentation case was also added to verify the Clear exact-alarm permission projection, app-specific system Settings navigation and Back return, plus empty-Store reminder reconciliation without Store writes.

## Verification

- Focused JVM settings/import-export set: 69/69 PASS.
- Full JVM gate: 970/970, 140 suites, 0 failures/errors/skips.
- Android API 35 targeted F-08 instrumentation: 13/13 methods PASS across permission, reminder, calendar, ICS, backup, rejection, and protected-settings paths.
- Skin and Native boundaries, APK assembly, lint (0 errors / 56 warnings), and AndroidTest Kotlin compilation: PASS.
- Exactly one production `.pcskin`; source and APK bytes match, SHA-256 `299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4`.
- Physical/OEM reminder delivery, notification grant through the Skin request flow, ICS-picker cancellation, and reminder reboot/time-zone/DST boundaries remain NOT TESTED.

The exact run log inventory and the failed attempts are retained under `.local/android-test/reports/F-08-20261104/`.

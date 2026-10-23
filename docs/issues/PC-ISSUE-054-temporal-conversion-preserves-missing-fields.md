# PC-ISSUE-054 — Preserve missing critical fields during type conversion

Date: 2026-10-23

Severity: P0 candidate

Status: **PURE MODEL CONTRACT PASS / UI PRESENTATION NOT TESTED**

## Scope and finding

The reconstructed cases `REC-P0-021` and `REC-P0-022` cover type conversion when the destination kind needs a critical time that the source does not provide. A read-only source and test audit found the existing `TemporalConversion` model retains valid information, returns missing fields for review, and does not fabricate a time for a date-only deadline. The exact regressions passed on the current code/test baseline. No product or test source change was needed; this closes an evidence gap rather than repairing a reproduced defect.

## Candidate mapping

- `REC-P0-021`: `TemporalProtocolTest.alarmToActivityRequiresTheMissingEndTime` converts an Alarm point to an Activity. It preserves the source as `startText`, leaves `endText` empty, and includes `DraftField.END` in `required`.
- `REC-P0-022`: `TemporalProtocolTest.deadlineDateToAlarmMustNotInventATime` converts a date-only Deadline to an Alarm. It leaves `startText` empty and includes `DraftField.START` in `required`; no instant is inferred from the date.

Both candidates remain `UNMAPPED` to the unavailable original 209-case checklist. The evidence covers the pure model result and required-field contract only; it does not establish how an editor presents those requirements.

## Verification evidence

Environment: Gradle JVM unit tests, pure `TemporalConversion` model; no UI or device test.

- Targeted command: `:app:testDebugUnitTest --tests "dev.powerfulcalendar.core.TemporalProtocolTest.alarmToActivityRequiresTheMissingEndTime" --tests "dev.powerfulcalendar.core.TemporalProtocolTest.deadlineDateToAlarmMustNotInventATime" --offline --rerun-tasks --console=plain` — **2/2 PASS**, zero failures/errors/skips.
- Log `.local/android-test/reports/freeze-20261023/pc-issue-054-temporal-conversion-targeted-20261023.log`, SHA-256 `9F27CEC16D6F006D1876B24D6E685E50DD2A8670EA51B8AF39957449D8D4F62B`.
- JUnit XML `.local/android-test/reports/freeze-20261023/pc-issue-054-targeted-junit/TEST-dev.powerfulcalendar.core.TemporalProtocolTest.xml`, SHA-256 `4AB98B1E2E452AB8B6914524D076877206F2C3C6B059ED2A02B62484E86295DD`; 2 tests, 0 failures/errors/skips.
- Tested code/test baseline: `c2f2ad5efb5b84b887fb38b4ee03916c0c23906e`; the latest full Gradle run on that baseline passed **409/409** across 55 suites, lint 0 errors / 55 warnings. Full log SHA-256 `EACE03207765FED33CD83882FAD41F9CC477BB48BD26EA5CF14D1B6FD1A07FEE`; Debug APK SHA-256 `80521C6A833B189FF96EF11D6543B4E6877DFE8A89D64BB1269C5220E8CD3D92`.
- Structural-only ledger validation after this sign-off: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-054.json`, SHA-256 `BF3A99B621F0B25D969E0D886ED22595B7580D1DEA504E5E148AABFAD368EA11`; ledger SHA-256 `E7950013F8932D9D2F8B4498F32673ADF66DD36EEFCA783008B043CDA53A76ED`. It confirms 209 unique candidate IDs and 102/0/9/91/7 status counts. Structural validation does not test product behavior or prove historical mapping.

## Candidate sign-off and limits

`REC-P0-021` and `REC-P0-022` are PASS for their pure model conversion contracts. No UI, Android device, or historical checklist mapping is claimed. Overall core freeze remains **HOLD**.

## Git

This sign-off updates the independent report, acceptance ledger, and synchronized freeze documentation; it is committed separately using a Chinese conventional commit and pushed to `origin/main`.

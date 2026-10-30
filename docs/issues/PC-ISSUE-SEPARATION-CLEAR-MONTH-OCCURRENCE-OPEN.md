# PC-ISSUE-SEPARATION-CLEAR-MONTH-OCCURRENCE-OPEN

Date: 2026-10-29
Area: Skin separation / reconstructed S0-MO-02 (P1)
State: **Source path confirmed; JVM and Chrome Mock evidence PASS; Android WebView recheck PASS (2026-10-31); full Native/Clear parity NOT TESTED**

> Status update (2026-10-31): the Android WebView method described below was executed on the integrated mainline; see “Integrated API 35 runtime recheck” at the end. The original “NOT TESTED” statements describe the 2026-10-29 report-time state.

## Scope and outcome

S0-MO-02 requires opening a repeating occurrence from the Month agenda with its formal event owner, revision, and original recurrence slot key intact. This follow-up closes the missing JVM/Chrome evidence slice and adds a real Android WebView regression. It does not sign off S0-MO-02 because that Android test could not be run in the current environment.

No production behavior defect was reproduced, so no production app or canonical Clear package code changed. The implementation path already preserves the slot key. The changes here make the handoff observable and testable across projection, Clear UI routing, and the editor bridge.

## Call path audit

1. `WebSkinMonthProjection.build` emits each month occurrence with the formal owner's `eventSeq` and `revision`, plus `originalOccurrenceKey` for repeating records.
2. Clear's `renderMonth` places those values on each `.agenda-event` button. The delegated `openEvent` action enters `PowerCalEdit.routeAction`, which calls `editor.open` with `eventSeq`, `expectedRevision`, and `occurrenceKey` copied from the button.
3. `WebEditWire.decode` accepts the closed `editor.open` schema. `WebSkinEditService` re-reads the owner, checks its revision, validates the key with `RecurrenceEngine.occurrenceForKey`, and passes it to `EditSession.open`.
4. The editor retains `selectedOccurrenceKey`; choosing `SINGLE_OCCURRENCE` loads the corresponding slot form. The selected slot remains tied to the formal series owner.

The Studio Preview Mock previously omitted `expectedRevision` from its resulting editor snapshot. It now records that value so the Chrome regression can inspect the entire action tuple.

## Changes

- Added a JVM integration regression that feeds a real month projection item into `WebSkinEditService`, selects the single-occurrence scope, verifies the expected occurrence time, and confirms the operation creates no draft, identity, or commit.
- Expanded the Android WebView recurrence regression to navigate to Month, select the day, open the agenda occurrence, verify the formal owner and selected-slot capability, then verify the single-occurrence editor form and saved override key.
- Added a dependency-free Chrome/CDP regression using the canonical Clear `.pcskin` and Studio Preview Mock. It asserts the agenda button and editor snapshot retain `eventSeq=8`, `revision=2`, and `occurrenceKey=2026-10-22T09:00` through scope selection.
- Updated the Studio Mock editor snapshot to include the passed expected revision.

## Test evidence

### Focused JVM and complete Android build gate

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.WebSkinRecurrenceBridgeTest' --offline --console=plain
```

Result: **12/12 tests PASS**, zero failures, errors, or skips. This includes the projection-to-editor regression.

Full command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL; 623/623 JVM tests across 84 suites; 88/88 Gradle tasks; lint 0 errors and 52 warnings.** The added AndroidTest compiled and was packaged.

Full log: `.local/android-test/reports/separation-s0-timeline-drag/month-occurrence-cdp/gradle-full-20261029.log`
SHA-256: `712A32CEE9B9E3FD58567B46B6F9799306626CDE6283C600BB7D6DA7E20A605A`

APK SHA-256:

- Debug: `F730EDD3953C534FA7C9441D0061B4815FF33BC13EFCE407127E98D0EA76735F`
- AndroidTest: `883539DF6F008F84DFBDD1A45403A4BB1BDDE2438D64004775F1EC1882FA748B`

### Clear browser replay and Studio tests

- Chrome **156.0.8078.48** / CDP replayed the canonical 9-file Clear package through Month agenda → `editor.open` → `SINGLE_OCCURRENCE`. Observed tuple: `eventSeq=8`, `revision=2`, `occurrenceKey=2026-10-22T09:00`; selected form start: `2026-10-22 09:00`.
- Result JSON SHA-256: `15847F1D36A166C58F0BB9E7CE389446A591C628C4ECAB900EF03FACE3415F70`.
- Screenshot SHA-256: `A50225CC138B71F2E60E4B8F2E8143A68FC83A364495F83EDA90B89093337712`.
- Result and screenshot: `.local/android-test/reports/separation-s0-timeline-drag/month-occurrence-cdp/`.
- Skin Studio Python suite: **61/61 PASS**. Log SHA-256: `A58D9EB40CED2FE4CC3D2C55CBBA2D33719BEB83790A2E290007BC65C6C38BBE`.
- `node --check` passed for `tools/skin-studio/v3-studio.js` and `tools/skin-studio/tests/e2e_v3_month_occurrence_open.cjs`.
- Canonical package verification passed: 9 files / 386,157 bytes; SHA-256 `8C0BAE30D2B4D059D2CB120441DED47E88D2C29B9E3DF71846CB99BD4C24123C`.

The browser run exercised the canonical Clear JavaScript package in Studio Preview with a mock Host. It is not an Android WebView or persistent PowerStore run.

### Android WebView execution status

The new AndroidTest method is `actualWebViewMonthOccurrenceOpenPreservesOriginalSlotForSingleEdit` in `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/WebSkinRecurrenceInstrumentedTest.kt`. Its source compiled and the AndroidTest APK was assembled, but it has **NOT BEEN EXECUTED**.

`adb devices -l` listed no connected devices. The SDK has no Emulator or API 35 system image. Installing those packages failed because SDK Manager could not download the repository manifest (`IO exception while downloading manifest`). No AVD was created, wiped, or modified. Availability evidence: `.local/android-test/reports/separation-s0-timeline-drag/month-occurrence-cdp/androidtest-device-availability-20261029.txt`, SHA-256 `5F5AF12F974B69F2B83EA354044C0491D80C086EF8F17B31AF3C1EDA61AA0DA3`.

Therefore Android WebView runtime behavior, paired Native/Clear replay, and full S0-MO-02 parity remain **NOT TESTED**. This report does not claim those gates passed.

## Matrix sign-off

- JVM projection + production edit-service handoff: **PASS**.
- Canonical Clear JavaScript + Studio Host Mock action tuple: **PASS**.
- Android WebView instrumentation execution: **NOT TESTED**.
- Matched Native/Clear replay and complete parity: **NOT TESTED**.
- Overall reconstructed row S0-MO-02 remains **NOT TESTED / unsigned** until Android WebView execution and matched replay are available.

## Git delivery

- Test and instrumentation commit: `e4aa5228884fd3beb72e5b53e9152e888848b5c5` — `test(分离): 验证月历重复事件的原始槽位传递`.
- Pushed to `origin/codex/separation-s0-timeline-drag`.
- No production app behavior or canonical Clear package changed.

## Integrated API 35 runtime recheck (2026-10-31)

- On source SHA `2f69d27c578e18688fcf60f6d46276479e11bce8`, API 35 AVD `codex-clear-api35` / Android 15 / WebView 124.0.6367.219, `WebSkinRecurrenceInstrumentedTest#actualWebViewMonthOccurrenceOpenPreservesOriginalSlotForSingleEdit` passed as part of the class **4/4 PASS**. The actual packaged Clear WebView opened the Month occurrence, preserved owner/revision/original slot, selected `SINGLE_OCCURRENCE`, and exposed the matching slot form. Direct instrumentation log: `.local/android-test/reports/clear-runtime-diagnostic-20261031/WebSkinRecurrenceInstrumentedTest-direct.log`, SHA-256 `9EED3A5C5D74712ADF032C0B1182C804B518F18CBCCBD7AAF63B1600CEAAE870`.
- The AVD's buffered logcat contained an older `querySelector` `SyntaxError` at 19:41:57, before the current app install/update at 21:14:22. The current Clear `edit.js` contains no such malformed selector and the current scoped-occurrence path did not reproduce it. No current uncaught JS exception was found after install; CSP rejection lines came from the deliberate CSP probe in `WebSkinResourceParityInstrumentedTest`.
- The rerun used a current API 35 WebView and provides runtime evidence, but the test file contains another Agent's uncommitted diagnostic edits. Those edits were left untouched and are not part of this documentation commit. Matched Native/Clear replay, complete recurrence-scope parity, TalkBack and physical/OEM behavior remain **NOT TESTED**; S0-MO-02 remains **PARTIAL / HOLD**.

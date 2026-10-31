# PC-ISSUE-SEPARATION-CLEAR-MONTH-SELECTION

Date: 2026-10-29
Area: Skin separation / reconstructed S0-MO-01 and S0-MO-03 (P1 behavior slice)
State: **IMPLEMENTED; selected-date Activity CREATE has a matched Android 15 Native/Clear PASS; general Month parity remains PARTIAL / HOLD**

## Summary

Clear's Month page previously treated a date-cell click as a timeline jump. This collapsed two separate Native interactions: selecting a day to inspect its agenda and explicitly opening that day on the timeline. Month creation also had no Host-owned selected-date state to use independently from the timeline camera.

Month now has an independent Host-owned month and selected date. Selecting a day stays in Month and updates the agenda. Month/year navigation and Today select the first/current day. Creating an Activity uses the selected local date while leaving the timeline camera unchanged. The explicit “在时间轴查看这一天” action remains the only Month action that moves the camera and opens Timeline.

This closes the reported Clear date-selection/create behavior slice and now has a matched Android WebView Native/Clear Activity-creation replay. It does **not** close S0-MO-02, prove occurrence-opening parity, establish full Month navigation equivalence, or prove physical/OEM behavior.

## Root cause

- `WebSkinMonthProjection` derived the visible month from the timeline anchor and had no independent selected-date state.
- Clear rendered date cells without a `selectMonthDate` Host action; activating a day used the same jump path as the explicit timeline action.
- Host navigation therefore changed page/camera as a side effect of selecting a date, and Activity creation had no selected month date to inherit.
- Studio's Preview Mock did not initially allow the new Month-selection actions or propagate the selected day into its non-writing creation form.

## Changes

- Added pure `WebSkinMonthViewState` transitions/projection for initialization, in-month date selection, month selection, bounded month browsing and Today.
- Added strict Host decoding for `selectMonthDate` and `selectMonth`; persisted Month state is saveable and independent from the timeline camera.
- Updated Month projection/action routing, Activity creation context and explicit timeline jump behavior.
- Updated Clear HTML/JS/CSS: accessible selectable date buttons, agenda refresh, year/month picker, month arrows/swipe, Today and a distinct timeline-jump action.
- Updated Studio Preview Mock action registration, page guards, selected-date Activity form and interaction log.
- Rebuilt the canonical `powercal-clear-default.pcskin` from the Clear source.
- Added Compose regressions and Android WebView instrumentation for selection, Activity creation, camera independence, zero-write close and explicit jump. The original Clear-only method remains in the class; a separate matched Native/Clear selected-date CREATE now executes on API 35.

## Evidence and results

### JVM and Android build gate

Command:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

Environment: Windows, JDK 20.0.1 (`C:\Program Files\Java\jdk-20`), Gradle 8.14.3, Android SDK `E:\android-sdk` (platform android-36; app targetSdk 35). These paths were set only in the build process; no `local.properties` or global environment configuration was changed.

The final-tree serial full gate passed:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL in 3m 31s; 622/622 JVM tests, 84 suites, zero failures/errors/skips; 88/88 tasks passed; lint 0 errors and 52 warnings.** The new AndroidTest method compiled in this gate.

- Final full log: `.local/android-test/reports/separation-s0-timeline-drag/month-selection-cdp/gradle-full-final-20261029.log`
- Final full log SHA-256: `930E62AF05C3A562636D94E69D218B2D53CE48D3003A769D21B5EE4F58CEEF1A`
- Debug APK SHA-256: `F730EDD3953C534FA7C9441D0061B4815FF33BC13EFCE407127E98D0EA76735F`
- AndroidTest APK SHA-256 (includes the new method): `56CEDFC8B0EC343C7D148FA185C89443D498A571F3371C9A5D61D98E13DA5064`
- JUnit XML independently parsed: 84 files, 622 tests, 0 failures/errors/skips.
- Lint XML independently parsed: 52 warnings, 0 errors.

One preceding full-gate attempt failed only `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` at its 20-second second-page Compose wait. An isolated rerun initially failed once, then passed 1/1 on a diagnostic rerun. The temporary diagnostic wrapper was removed, leaving the existing test source unchanged; the complete final serial gate above passed. The failed-attempt logs are retained: `gradle-full-20261029.log` (SHA-256 `BE6C9DF9FCAA1C6EF22C41BF39EA6CD88AE79DB4F7F013A48E8CAA40E130FAFE`) and `gradle-memo-isolated-20261029.log` (SHA-256 `B40FBE4774E226BB4C17E955219CEF054482A92AC60CC51B566B3988A3C0BD05`). This intermittent unrelated timeout remains worth watching; no production or permanent test change was made for it.

### Skin Studio and real-browser Preview

- Skin Studio Python suite: **61/61 PASS**. Final log: `.local/android-test/reports/separation-s0-timeline-drag/month-selection-cdp/skin-studio-unittest-final-20261029.log`; SHA-256 `38896CF3B102EE807DCDBDFECC0DE607E68F33540BFC5AE5662E53B6A26282B2`.
- The first final-tree attempt had 3 test-subprocess errors because Git rejected the SYSTEM-owned worktree as dubious. The rerun used process-local `GIT_CONFIG_*` safe.directory settings only; no global Git config changed. The first verbose log path was reused, so only this note—not the original full log—is retained: `skin-studio-unittest-initial-failure-20261029.txt` (SHA-256 `B381D917555AA0362968A9043DD48EE6D2AB6554598607DD57BF31FB73BF86D9`).
- Chrome 156 Headless/CDP imported the exact canonical package into Skin Studio Preview and verified: October 23 selection updates agenda without moving the camera; selecting April 2027 chooses its first; arrow and swipe browse months; June 19 selection and agenda; Today returns to the simulated date without moving the camera; Activity Mock form uses June 19 without camera movement; explicit jump moves to June 19 local noon and switches to Timeline.
- Chrome/CDP log SHA-256: `7E0B0286A7514D336100C40E61280F989DDC46BDA6F43BBB71E04DCA0C9A07D7`.
- Structured result SHA-256: `80712F5998E351601B5F8412900A9D159D48D7D90DCF0F6E2660A36D8C1D3CF8`.
- Preview screenshot SHA-256: `41828997930DC0FCF985BD4E22297A11D07148A0A462E6F38533802159BD30FF`.

This is real Chrome rendering of the Studio Preview Mock, not Android WebView and not a persistent Store write test.

### Source and package checks

- `node --check` passed for the changed Clear `app.js`, Studio `v3-studio.js` and the new CDP test script.
- `git diff --check` will be rerun after final report and handoff edits before commit.
- `build_default_clear.py --verify` passed for the rebuilt canonical package (9 files, 386,157 bytes).
- Canonical package SHA-256: `8C0BAE30D2B4D059D2CB120441DED47E88D2C29B9E3DF71846CB99BD4C24123C`.
- APK extraction at the earlier full gate matched the canonical asset hash above.

### Android WebView/device execution

At the time of the original report, ADB failed with `could not read ok from ADB Server` / `failed to start daemon`; the source compiled but had no device result. The later rerun on API 35 `emulator-5570` completes a same-fixture Native/Clear Month-selected Activity CREATE. Both begin with empty isolated Stores; the selected local date is carried into the Activity start/end dates, and the final `DraftForm`, eventSeq, revision, commits, identities and sequence watermark match. Clear month navigation, date cell, create entry and sheet Save are each injected with `SOURCE_TOUCHSCREEN`; the WebView capture asserts `event.isTrusted=true` for every action. Native editor publish and Host approval use Compose touchscreen input. Stores remain unchanged before publish/Host approval.

- Focused pair **1/1 PASS**; full `TimelineCreateContextWebViewInstrumentedTest` **3/3 PASS**, Android 15/API 35/WebView 124/`emulator-5570`.
- Full offline gate: **785/785 JVM / 125 suites / zero failures/errors/skips / 171 tasks**, both boundaries, Debug/AndroidTest APK and Lint pass (0 errors / 55 warnings). Debug APK SHA-256 `ED2F8F9827EF820361CC663F9C89D7F84A2545AB6DB313F36587C2E1DA1D064B`; AndroidTest APK SHA-256 `3169C0E361A74C3CF3B26512CD9A830F7F9C8770D4F270E94D51AD236A9BC551`.
- Test source commit `337ddce` is pushed. Focused and class logs, gate and APK hashes are indexed by `.local/android-test/reports/sep02-month-create-parity-20261031/manifest.json` (SHA-256 `59D4F7431561747EDB654EAF400A8BD96CF47F57D62639C303D4FF48C2477C08`). The two independently modified AndroidTest files (`WebSkinFormalDeleteInstrumentedTest.kt`, `WebSkinRecurrenceInstrumentedTest.kt`) were present and compiled into the AndroidTest APK, but were not changed/staged by this task or executed by the focused invocation.
- Test inputs for the Clear title/start/end values are normalized by the instrumentation script so both sides use the exact Native persisted form; this is not a software-keyboard parity test. No app data, installed user data, or AVD was cleared or wiped. Physical/OEM remains owner-run.

## Matrix sign-off

- `S0-MO-01`: implementation and narrow Compose/Chrome behavior evidence **PASS**; the paired device case now selects a date as part of creation, but general month navigation/agenda parity remains **NOT TESTED**.
- `S0-MO-03`: selected-date Activity creation and explicit-jump behavior have Compose/Chrome evidence; selected-date Activity CREATE now has a matched API 35 Native/Clear persisted result **PASS**. Full Month create/lifecycle parity remains **PARTIAL / HOLD**.
- `S0-MO-02`: unchanged, **NOT TESTED**.
- This Issue does not authorize an overall separation GO or close S0/S5.

The saved Chrome Preview frame is 484 px tall; the selected-day DOM rectangle was `{left:159.42, top:416, right:208.56, bottom:473}` after Today. The fixed bottom tab bar overlaps part of that viewport row in this frame. The page is vertically scrollable, but automatic selected-cell visibility at short heights was not separately tested; recheck in Android WebView when ADB is available.

## Reproduction/evidence locations

- Chrome log/result/screenshot: `.local/android-test/reports/separation-s0-timeline-drag/month-selection-cdp/`
- Android instrumentation source: `app/src/androidTest/java/dev/powerfulcalendar/ui/v2/TimelineCreateContextWebViewInstrumentedTest.kt`
- Reconstructed matrix: `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md`

## Git delivery

- Implementation commit: `008532a505530a8d060dbaaae88bc0bdd76376ec` (`feat(皮肤分离): 让月历选日独立于时间轴`).
- Pushed as a normal fast-forward to `origin/codex/separation-s0-timeline-drag`.

## Matched Native/Clear Month-selected Activity CREATE follow-up（2026-10-31）

Added `monthSelectedActivityCreateMatchesNativeAndClearBusinessRecord` to the existing create-context instrumentation class. The AVD test taps the Native Month tab/day/create/publish through Compose and uses touchscreen injection for Clear Month navigation, the selected date, create entry and sheet Save; all four WebView clicks are checked as `isTrusted=true`. Clear's Host confirmation is a Native Compose touchscreen action. The Native saved start/end text is copied into the Clear form so the pair compares identical user-visible time values, not two separately captured defaults.

- The selected date is retained on both Activity start and end; `DraftForm`, `eventSeq`, revision, commit/identity counts and next sequence match. Both editors are transient until publish/Host approval, and Clear has no write before approval.
- Focused pair **1/1 PASS**; containing instrumentation class **3/3 PASS**; API 35 / Android 15 / WebView 124 / `emulator-5570`.
- Full offline gate: **785/785 JVM / 125 suites / zero failures/errors/skips / 171 tasks**, both Skin boundaries, Debug/AndroidTest APK assembly and Lint pass (0 errors / 55 warnings). Debug / AndroidTest APK hashes: `ED2F8F9827EF820361CC663F9C89D7F84A2545AB6DB313F36587C2E1DA1D064B` / `3169C0E361A74C3CF3B26512CD9A830F7F9C8770D4F270E94D51AD236A9BC551`.
- Test commit `337ddce` is pushed. Evidence manifest SHA-256 `59D4F7431561747EDB654EAF400A8BD96CF47F57D62639C303D4FF48C2477C08`; artifacts are under `.local/android-test/reports/sep02-month-create-parity-20261031/`. The two independently modified AndroidTest files were included in APK compilation, not changed/staged by this task, and not run by this test invocation.

This closes only the selected-date Activity-create slice; month/year paging, occurrence opening, other Month operations, Studio-edited package parity, accessibility and physical/OEM remain incomplete. SEP-02 stays **PARTIAL / HOLD**; SEP-02 and SEP-04 are not globally closed.

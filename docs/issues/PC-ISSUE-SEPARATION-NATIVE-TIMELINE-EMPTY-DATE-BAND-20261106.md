# PC Issue: Native 0.9.5 Timeline date-only Deadline empty band

Date: 2026-11-06
Status: RESOLVED — narrow slice PASS; Timeline remains PARTIAL
Reference source: `b1f7629f0c52812e1887443872f43aa6c49908c4`
Reference APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`
Product Cutover: HOLD

## Observed difference

When a date-only Deadline exists in the queried calendar range but its date is outside the current Camera, Native keeps a 64dp date band and displays “可见日期内没有日期级截止”. The regular “还没有事件” hint is based on query-level records. Web v3 previously rendered the date band only from currently visible items and based its no-events hint on the visible timeline list, so it lost the Native empty band and could say the calendar had no events while records existed elsewhere in the query range.

## Change

- `WebSkinTimelineProjection.kt` projects `hasRecords`, `hasDateOnlyDeadlines`, `hasVisibleDateOnlyDeadlines`, and system font scale from existing Host data.
- `PowerCalApp.kt` supplies the Android system font scale.
- `app.js` and `style.css` render the Native empty-band state and use query-level record presence for the no-events hint.
- `build_default_clear.py` regenerated the canonical factory package; package ID remains `powercal-clear-default`.
- No Core/Host business rule or Store behavior changed.

## Reproduction and result

On isolated API 35 AVD `PowerCal_Wave2_QA35_20261104` (`emulator-5556`, 1080×2400, 420dpi, WebView 124.0.6367.219), the Android instrumentation test created the same one-record date-only Deadline in independent Native and Web Store fixtures, due four days after today. The actual WebView showed the expected empty band, at 64 CSS px height, with its label centered 32px above the timeline viewport top. Host state marked records and date-only Deadlines present while none were visible in the current Camera. Both Stores retained only the initial fixture commit, zero drafts, and unchanged record/revision.

`TimelineDragWebViewInstrumentedTest#nativeAndClearMatchTheEmptyDateOnlyDeadlineBand`: **1/1 PASS**.

The raw frozen APK screenshot also contains the same empty-state message, but it was captured with a different user-visible fixture. The measured height/centering evidence above is the paired Native Compose and real Android WebView instrumentation run; it is not presented as a raw-APK same-database screenshot comparison.

## Verification

- Full serial Gradle gate: PASS; 977 JVM tests, 0 failures/errors/skips; both source boundaries, APK, lint and AndroidTest compile/package PASS.
- Single-default package tests: 2/2 PASS; Node syntax check PASS.
- `build_default_clear.py --verify --apk`: PASS; one `.pcskin`, ID `powercal-clear-default`, 10 files / 410938 bytes; source and APK asset SHA-256 `a60e791b35d8793082fd944974b6a05cc45a6d1b8040af8ff891a5985458f964`.
- Debug APK SHA-256: `254d2ff1a8e4da4ebb3af4cbbffd68f9a2f3c13ff92fdf57cbd2ac3f509683de`.
- Full log and screenshots: `.local/android-test/reports/native095-empty-date-band-20261106/`.

## Limits

This closes only the off-camera date-only Deadline band and the related query-level empty hint. It does not sign off the full Timeline or other pages, event-kind cards, large-font rendering, dark theme, or every tap/scroll state. Native recovery and Core Store semantics are unchanged. Product Cutover remains HOLD.

# PC-ISSUE-SEPARATION-AI-IMAGE-UPLOAD-HISTORY — image consent and chat persistence

Date: 2026-10-27

Status: **The bounded Robolectric privacy slice and the 2026-11-01 API 35 Native/Clear Android Photo Picker image-chat replay pass. No production source changed in the follow-up. This does not close SEP-09 or SEP-10; both remain PARTIAL / HOLD.**

## Scope and finding

The preceding attachment report covered selected text-file extraction, redaction, explicit submission and a pending typed proposal. It did not cover the image result path or raw-image privacy in persisted chat history. This change adds those regression checks and a direct chat-history replacement regression.

The first full-suite run exposed a Windows Robolectric host issue: Android `AtomicFile` commits by replacing its base file with the `.new` file, while Java `File.renameTo` on this Windows host does not replace an existing target. That failure did not demonstrate a production Android defect. The test now uses a Robolectric-only `ShadowAtomicFileReplaceOnWindows` backed by `Files.move(..., REPLACE_EXISTING)` to model the Android replacement contract; no production storage code changed.

A subsequent full run also caught a test-side race: the test read the history file directly while the asynchronous chat-history write/replace was in progress. The test now waits, with a bounded timeout, for the expected persisted contents to become readable. The final full suite passes with this synchronization.

## Verified behavior

`WebAiAttachmentFlowIntegrationTest.skinImageRequiresExplicitUploadConsentAndNeverStoresRawImageInChatHistory` drives the production `PowerCalApp` and `WebSkinBridgeRuntime` path using a synthetic one-pixel PNG, a local `ContentProvider`, a recorded Android picker result and a loopback HTTP model stub.

- Returning an image from the picker does not submit it. Cancelling the consent prompt also sends no request and leaves calendar-store counters unchanged.
- After the explicit “发送原图给 AI” action, the local stub receives the selected image as a PNG data URI. The request is local and synthetic; no live model or external service is used.
- The Host projection and persisted conversation retain the consent marker and assistant response. Neither the decoded chat history nor the serialized history file contains the image's base64 bytes.
- Clearing chat empties the Host projection and durably replaces the private history file with `[]`; formal events, drafts, identities and commit counts remain unchanged.
- `chatHistoryStoreReplacesLongerSnapshotWithShorterAndEmptyHistory` verifies that a shorter snapshot replaces old content and that an empty snapshot replaces it with `[]`.

The Windows shadow is confined to this Robolectric test class. It models file replacement but is not evidence of Android filesystem crash behavior. The independent selected-text-file redaction/proposal flow remains covered by `skinSelectedFileCanBeRedactedBeforeAiProposalAndDoesNotWriteCalendarData`.

## Test evidence

Focused command, run three times after the synchronization fix:

    .\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest --tests dev.powerfulcalendar.ai.AiConversationTaskSnapshotTest.persistedChatRetainsEvidenceIdsTimesAndReconstructsSameTaskRevision

Each run passed **4/4 tests** (3 attachment-flow tests plus 1 persisted-conversation snapshot test), with zero failures/errors/skips. Logs:

- `attachment-class-repeat-fixed-1.log`, SHA-256 `4F83E65658AE1A1BBD1907B4CEEA5960475D28AF0A5655173963329CB64A510A`
- `attachment-class-repeat-fixed-2.log`, SHA-256 `869F3AE15FA15845A8AFD57B2589EA9C742778B762027846BC77415A81A624B5`
- `attachment-class-repeat-fixed-3.log`, SHA-256 `5045300FBCC9F2008290BE2EA757DCF153B4A8330D54549D30F6C91AAAB3AB78`

Full offline gate:

    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain

Final result: **BUILD SUCCESSFUL; 588/588 JVM tests, 83 suites, zero failures/errors/skips; 88/88 Gradle tasks successful.** Source-boundary verification, Debug APK, AndroidTest APK and lint passed. Lint reported **0 errors / 52 warnings**. Final log SHA-256: `203CBEC9A015ACADD9C63853B161677DFB9E15B3A1D9CA3055A211D31DECAAED`.

The earlier full-gate attempt before the bounded file-read wait had 1 failure in 588 tests (Windows file lock during a direct test read). Its diagnostic log SHA-256 is `0BF4D37896AC90C0439BF7822EFE577D46501D33BDA1DC232983E282401A1DDE`; it is retained and is not counted as passing evidence. The final run above followed the test-harness fix.

## Reproducible artifacts

All logs and test artifacts are under `.local/android-test/reports/separation-20261027/ai-attachment-followup/` (ignored local evidence):

- Final attachment integration JUnit XML SHA-256: `DE9288C90AE9422FAB854353C2BA8812068CE606BAB2092984275F07D169ACD7`
- Persisted conversation snapshot JUnit XML SHA-256: `F1D35E50D6A8DC1BA786CDAC0C4B9A1FF7303CC04C7259004028481E890A4076`
- Test source SHA-256: `BE1A1AC41FA833F6EB47A687FFB4AFEFC13483FABD633859A2E373EF49634770`
- Debug APK SHA-256: `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D`
- AndroidTest APK SHA-256: `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`

No emulator binary or Android system image is available in this SDK environment. Actual Android chooser UI, Android WebView/device behavior, OCR, ICS, live-provider errors/retries and physical/OEM behavior are **NOT TESTED**. The user performs physical-device testing. SEP-09 and SEP-10 remain **NOT TESTED** as complete parity rows.

## Git

The test-only change and narrow SEP-09 evidence update were committed as `327a9aa9604d83bffa0154f795faeabb3913e22a` (`test(skin): 验证图片上传授权与聊天历史隐私`) and pushed to `origin/feat/skin-first-separation`.

## Android Photo Picker Native/Clear follow-up (2026-11-01)

API 35 Android 15 `emulator-5570` / WebView `124.0.6367.219` now runs the real Android Photo Picker through Native Compose and the packaged Clear WebView. Each path selects the same isolated PNG fixture. Picker cancellation and image-consent cancellation send no provider request, write no chat history, and leave the isolated Store unchanged. After the explicit “发送原图给 AI” approval, both paths send the identical PNG data URI to a loopback deterministic free stub; Native and Clear chat histories and Clear's Host-projected history match. The image bytes are absent from chat history, serialized history, and WebView state. Formal events, drafts, pending counts, commits, identities and sequence watermarks remain unchanged.

The instrumentation test creates the image in the Android Pictures collection so the real Photo Picker exposes its accessible photo tile. The image is removed in the test `finally` path. The earlier ADB picker probe was also removed by exact path after diagnosis. This covers the AI chat image-analysis path only; it does not verify text-file extraction/redaction, image OCR review, ICS import, live/external providers, provider recovery, full message-history parity, accessibility or physical/OEM behavior.

### Verification evidence

- `PowerCalAiProposalReviewWebViewInstrumentedTest` on API 35: **7/7 PASS**, zero failures/errors/skips. The class includes real Native/Clear AI chat and proposal-review paths; the image marker records `picker=Android15_PhotoPicker`, `consentRequired=true`, `providerRequests=2`, `imageBytesPersisted=false`, and zero Store counts on both sides.
- Image-case XML SHA-256: `12E34308FEB72BDA8D744338B2106312EDAD529CC257CCB77B2AA6453A7A25F7`; device logcat SHA-256: `6A6A6E2C4460D0E37C8EDA18C1294D35DADDEB549D032DAB86C0A16AAE140B59`.
- Full offline Gradle gate: **786/786 JVM, 125 suites, 0 failures/errors/skips, 171/171 tasks PASS**. Native and skin source boundaries, Debug/AndroidTest APKs, and Lint pass; lint reports 0 errors / 56 warnings. Gate SHA-256: `5D6A872EA5E0A036A822B5A3349DC6F6DA3027DC9DB46E92824A1B9C824CD77C`.
- Skin Studio Python suite: **65/65 PASS**, log SHA-256 `65875745B94A3C9B04372C975FA1B41F378F35C4BF019D6BBCF5AF1AF1088E42`. No Studio source changed in this follow-up; prior non-reading attachment Mock behavior is not elevated to device or provider evidence.
- Debug APK SHA-256 `EF85EA15AB31353F9A26C3EEEC3B5DBFC9CEB38BC73069752F7AB66F85965650`; full-gate AndroidTest APK SHA-256 `F5C29D76A569B5A987769E550C6F4BE6D2AA5BC322345EECFA5AFF61B1EB39C8`. The final focused rerun used AndroidTest APK SHA-256 `8324FEDFA02DC9D6891B989BC8BF4BFC563147B4C5D75BF297FB6942E474F3DB`; it includes the two protected other-Agent instrumentation files, and the focused class did not execute those test methods. Current test source SHA-256: `D03C15C55038B0B2FF8E64CF00B4CA829CBCB9628E253792062E0867A78D0E83`.
- Reproducible local artifacts: `.local/android-test/reports/sep09-native-clear-photo-picker-parity-20261101/` (`ai-webview-class.xml`, `ai-image-picker-logcat.txt`, `full-gradle-gate.log`, `skin-studio-python.log`, `summary.txt`).

This follow-up closes only the real Android AI chat image picker → explicit consent → local upload → privacy/history/store parity slice. SEP-09 remains **PARTIAL / HOLD / INCOMPLETE**; SEP-10 is unchanged.

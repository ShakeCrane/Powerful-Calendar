# PC Issue: Native 0.9.5 AI chat input limit parity

Date: 2026-11-06

Status: RESOLVED — input boundary slice PASS; AI page remains PARTIAL

Reference source: `b1f7629f0c52812e1887443872f43aa6c49908c4`

Reference APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`
Product Cutover: HOLD

## Observed difference

Frozen Native `AiChatScreen` and `AiClient` accept up to 6000 UTF-16 code units. Core’s current `StoreAiFeatureApiV1` enforces the same 6000-character bound. The default Web skin capped its textarea and submit path at 4000, and `WebSkinHost.decodeBridgeAction` also rejected messages over 4000. In addition, the generic bridge JSON envelope limit of 8192 could reject a valid 6000-character message when JSON escaping expanded its representation.

## Change

- `tools/skin-studio/default-clear/source/web/index.html`: set AI chat textarea `maxlength` to 6000.
- `tools/skin-studio/default-clear/source/web/app.js`: allow submissions through 6000.
- `app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinHost.kt`: accept `sendChat` text through 6000 and bound action JSON at 40,960 characters, enough for worst-case JSON escaping of a 6000-code-unit string plus envelope fields. Per-action schemas and the decoded `sendChat` limit remain enforced.
- Rebuilt `app/src/main/assets/powercal-clear-default.pcskin` only with `tools/skin-studio/build_default_clear.py`; package ID and manifest capabilities did not change.
- Added JVM and Android instrumentation boundary coverage. No Core logic, Store schema, credentials, or calendar data changed.

## Verification

- Full serial Gradle gate: PASS; 983/983 JVM, zero failures/errors/skips; both source boundaries, `assembleDebug`, `lintDebug`, AndroidTest Kotlin compile and package PASS.
- `WebSkinHostProjectionTest`: 6000 accepted, 6001 rejected, 6000 control code units after JSON escaping accepted, 40,961-character JSON envelope rejected.
- Isolated AVD `PowerCal_Wave2_QA35_20261104` / `emulator-5556`, API 35, System WebView 124.0.6367.219: `clearWebChatAcceptsCoreMaxLengthAtRealWebViewInputBoundary` PASS (1/1). Actual WebView form submission traversed its WebMessagePort, Host, Core, and local AI stub; provider content and persisted conversation retained all 6000 characters. Calendar Store snapshot was unchanged.
- The device test filled the real WebView textarea from the test script and invoked the page form submit handler; hardware keyboard entry at the limit was not tested. The raw 0.9.5-rc.1 APK was not used for a 6000-character end-to-end input test.
- Studio `node tools/skin-studio/tests/e2e_clear_studio_bridge.cjs`: PASS.
- `python tools/skin-studio/build_default_clear.py --verify --apk app/build/outputs/apk/debug/app-debug.apk`: PASS. One `.pcskin` in the APK; source/APK asset SHA-256 `2c503559a2cd7fae57c2f40e529a05913d41b3192931458d17771af2714f9e4e`; 10 files / 411736 bytes. Debug APK SHA-256 `EF29297DF78D253E2A9C3ED03B24C81664CC23CD2A429874B8C7E348D3E3789B`.
- Detailed command logs and failed harness attempts: `.local/android-test/reports/native095-ai-input-20261106/manifest.md`.

## Test attempts and scope limits

An initial edit temporarily expanded the existing Native/Clear paired test to a 6000-character Native prompt. It timed out waiting for the local provider reply on the Native side, before reaching the Web leg; the test was restored to its short baseline prompt. A standalone Web attempt missed the navigation target during simulated touch, and a subsequent DOM-click route attempt did not advance Host page state. These attempts are recorded as unsuccessful test setups, not passing evidence or confirmed product defects. The final test isolated navigation by using the already-bound Host route callback, then exercised the actual WebView input element, form handler, bridge action, Core chat API, and local provider. This test does not sign off physical navigation or AI-page touch parity.

This closes only the maximum AI composer length slice. Native raw-APK keyboard entry, full AI history parity, attachments, proposal review, errors/retry, and menu screenshot comparison remain outstanding. Do not mark the AI page or overall Native visual parity complete from this issue.

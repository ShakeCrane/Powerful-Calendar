# Native AI batch authorization review module

**Status:** CODE COMPLETE; focused and full JVM/build checks TESTED; Android dialog UI NOT TESTED.

**Scope:** Native presentation module separation only. SEP-10 as a whole remains PARTIAL.

## Root cause

The Host-owned batch authorization wrapper also rendered the entire review dialog body and action controls in `:app`. The `DraftCard` values contain draft IDs and versions needed for authorization, so moving that composable unchanged into `:native-skin` would have exposed Core persistence models to the renderer.

## Change

- Added display-only batch review DTOs to `:skin-api`: mode, total count, per-operation display counts, a delete-warning flag, and rows containing formatted sequence labels, operation labels, and titles. Draft IDs, draft versions, revisions, and Core objects do not cross into Native rendering.
- Added `NativeAiBatchAuthorizationReview` in `:native-skin`. It renders the same frozen-scope explanation, operation counts, formal-delete warning, ordered review rows, confirmation state, and cancel action.
- Kept the Host `AiBatchAuthorizationReview` as a thin adapter. It takes a copied `DraftCard` snapshot, projects display-only values, injects `SkinAlertDialog` as the dialog slot, and binds explicit authorization to the original snapshot. Existing `PowerCalApp` processing still passes the reviewed IDs/versions to `store.beginAiBatch`, where transaction/version checks remain authoritative.
- Added projection coverage for CREATE/REPLACE/DELETE counts, row order and formatted labels, PUBLISH deletion warning, and DISCARD mode. Existing Compose tests continue to cover no authorization on display/cancel and busy-state confirmation disabling.
- No Clear, frozen Core, batch transaction, default skin, or Native fallback code changed. The two pre-existing dirty WebSkin AndroidTest files were not staged or committed.

## Verification

All checks below ran against the exact implementation tree committed as `6db8e09313bcfb1ace321b6f91ea6a8e0108b2f4` and pushed to `origin/feat/skin-first-separation`.

Focused review and projection regression:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.AiProposalReviewComposeTest' --tests 'dev.powerfulcalendar.ui.v2.AiReviewComposeTest' --tests 'dev.powerfulcalendar.ui.v2.NativeAiProjectionTest' --tests 'dev.powerfulcalendar.ui.v2.NativeAiChatScreenComposeTest' --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **12/12 PASS** across the four selected suites. Log: `.local/android-test/reports/native-ai-batch-auth-review-20261030/focused.log`; SHA-256 `4C07207EE12EA4C63F6729013EF3EF9B91004B50F32DB4EB3B9BDE99C4CBE2A8`.

Full build and regression gate:

```powershell
$env:ANDROID_HOME = 'E:\android-sdk'
.\gradlew.bat :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=2 --console=plain
```

Result: **BUILD SUCCESSFUL**, 152 actionable tasks; Native and skin source boundaries PASS; AndroidTest Kotlin compilation PASS; Debug APK assembly PASS; lint PASS; **760/760 JVM tests PASS, 114 XML suites, 0 failures, 0 errors, 0 skipped**. Full log: `.local/android-test/reports/native-ai-batch-auth-review-20261030/full-gate.log`; SHA-256 `98DCECF6BCA3C48398F75E784326825AF08B68C893470DE7E0117BE68413BAD0`.

Debug APK at `app/build/outputs/apk/debug/app-debug.apk`: 66,500,178 bytes; SHA-256 `794EDF6192472A72054ACD8A13883994587165250066DEAB82141B561768F763`.

`adb devices -l` returned no online devices. Android instrumentation and physical-device interaction are **NOT TESTED**; AndroidTest source compilation is not represented as device evidence.

## Remaining scope

This closes the Native batch-review renderer slice, not SEP-10. Other AI workflows, full Native/Skin parity and device evidence remain open. No skin cutover or default change is implied.

## Git

- Implementation: `6db8e09313bcfb1ace321b6f91ea6a8e0108b2f4` — `refactor(皮肤分离): 将原生AI批量授权审核迁入模块`.
- Pushed as a normal fast-forward to `origin/feat/skin-first-separation`.

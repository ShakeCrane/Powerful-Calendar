# PC-ISSUE-CORE-EVENT-KIND-CONVERSION-API-V1 — Event kind conversion through the shared API

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / FULL JVM AND BUILD GATE PASS; DEVICE NOT TESTED**

## Goal and result

Move deterministic event-kind conversion behind `EventFeatureApiV1.convertFormKind()` so Native and Web Host workflows use the same Core rule. Keep the operation detached and side-effect free: it converts the supplied form and returns the converted form, required-field identifiers, and review note; it does not open an editor, mutate a draft, or write a formal event.

The initial call-site reconciliation found `WebEditWire.patch()` invoking `TemporalConversion` directly. The broader Native/Web check then found the same Core decision in the Native formal editor, new-event editor, Native Draft Deck, and runtime-Skin Draft Deck. All these production paths now call the same API. `TemporalConversion` is retained inside the Core adapter, and the existing editor and DraftReview APIs still own form updates, validation, and writes.

## Implementation

- Added `EventFormConversionV1` and `EventFeatureApiV1.convertFormKind()` to the shared `:core-api` contract.
- Implemented the adapter with the existing `TemporalConversion`, returning a detached `EventFormV1`, stable required-field names, and the existing conversion note.
- Routed Web editor and Draft Deck kind patches, Native formal/new-event editor conversion, Native Draft Deck conversion, and runtime-Skin Draft Deck conversion through the API.
- Kept type conversion separate from approval and persistence. Existing Host and DraftReview transactions continue to validate and commit edits.
- Updated Compose and instrumented-test fixtures to inject the shared API.

Files:

- [`CoreFeatureApiV1.kt`](../../core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt)
- [`PowerCalCoreFeatureApiV1.kt`](../../app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt)
- [`DirectEventEditor.kt`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/DirectEventEditor.kt)
- [`NativeDraftDeckHost.kt`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/NativeDraftDeckHost.kt)
- [`SkinnableDraftDeck.kt`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/SkinnableDraftDeck.kt)
- [`WebSkinEditBridge.kt`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt)

## Verification

| Check | Result |
| --- | --- |
| `EventFeatureApiV1Test`, `WebSkinEditBridgeTest`, Native formal/new-event editor and Deck Compose tests, and Memo paging race test | **78/78 PASS** across the selected suites. |
| Full forced serial JVM suite | **903/903 PASS**, 139 XML suites, 0 failures/errors/skips. |
| `verifySkinSourceBoundary` and `verifyNativeSkinBoundary` | **PASS**. |
| Debug APK / `lintDebug` / AndroidTest Kotlin compilation | **PASS**, 154 actionable tasks; lint 0 errors / 54 warnings. APK 67,043,950 bytes, SHA-256 `56E2DB62956862F722E78C33A0EF9D16642831A57DA62EF7BD926E47956CB22D`. |
| `git diff --check` | **PASS**. |
| Device / AVD / packaged WebView | **NOT TESTED**; `adb devices -l` listed no devices. |

Focused and full logs are under `.local/android-test/reports/phase1-web-event-kind-conversion-20261102/` (ignored by Git). The focused and final full-gate log SHA-256 values are `3A77A235F77735256D51308BBB360C73E2A3F82C58C1F29C764890BA62BCF01A` and `D5E20B82BE9A25F040F33F1E3C82AAEA116726EEEDF408A0F82E141FB974FBE2`.

The source API regressions cover conversion required fields and notes, date-only deadline precision, same-kind behavior, and Web patch routing. An intermediate AndroidTest Kotlin compile found eight fixtures using the prior editor/Deck constructor shape; those fixtures now inject the Event API, and the final full gate compiles them successfully.

## Remaining work

- Continue the bounded Phase 1 Host-call-site and feature-contract reconciliation. This slice does not close Phase 1.
- Android instrumentation, packaged WebView behavior, Native/Skin parity, and device/OEM behavior remain **NOT TESTED**.
- This API does not authorize publication or change event semantics, editor transactions, or frozen Core contracts.

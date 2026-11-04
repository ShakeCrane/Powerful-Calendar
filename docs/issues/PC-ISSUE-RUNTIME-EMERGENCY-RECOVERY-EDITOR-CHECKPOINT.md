# PC-ISSUE-RUNTIME-EMERGENCY-RECOVERY-EDITOR-CHECKPOINT

- **Date:** 2026-11-05
- **Status:** PASS — emergency Native recovery checkpoints a dirty Skin editor first
- **Task:** R-07
- **Code commit:** `b71cf0eedbd6dba9dafe0d9215ab33457258d045`
- **Evidence:** `.local/android-test/reports/R-07-20261105/`

## Defect and cause

The Activity-level eight-tap emergency gesture called `SkinPackageManager.selectNativeForEmergencyRecovery()` immediately. That changed the active presentation before `PowerCalApp` could call the existing `WebSkinEditService.prepareSkinChange()` checkpoint. A dirty ACTIVITY form could therefore leave the Web editor without a pending draft. An API 35 instrumentation reproduction observed Native activation with no checkpointed form.

## Fix

`MainActivity` now emits a recovery request and suppresses duplicate requests while it is being handled. `PowerCalApp`, after constructing the existing edit service, calls `prepareSkinChange()` before changing the active Skin. If checkpointing returns failure or raises a non-cancellation error, it retains the current editor and shows a retry message. Coroutine cancellation propagates without selecting Native. After a successful checkpoint, the existing Native recovery ID is persisted and the Native recovery surface opens. No new business API or second runtime was introduced.

## Verification

- Final offline Gradle gate: **971/971 JVM tests**, 141 XML suites, 0 failures/errors/skips; both source boundaries, Debug assemble, lint, AndroidTest Kotlin compile and APK packaging passed. Log: `full-gate-final.log`.
- Exact final Debug APK: `EC27EBE7A85FD117CB83F846F26CD4D6A374D4908F09001FC051231A8592C340`; API 35 final emergency-checkpoint instrumentation **1/1 PASS** and persisted Clear cold boot **1/1 PASS**.
- Emergency gesture check verified exactly one pending draft, no formal event, zero commits, and Native activation. Store implementation and frozen event semantics were not changed.
- Additional device checks covered factory package present/corrupt/missing, malformed selected package, process recreation, Web renderer termination, uncaught Web error, repeated boot, and recovery back to Clear.
- APK has exactly one `.pcskin`; official builder reports 9 files / 397,023 bytes and matching source/APK SHA-256 `b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`.

## Limits

TalkBack traversal, physical/OEM reminder delivery and live AI provider reliability remain NOT TESTED. A forced failure of the draft checkpoint I/O path was not injected. Product Cutover remains HOLD and historical SEP sign-off remains incomplete.

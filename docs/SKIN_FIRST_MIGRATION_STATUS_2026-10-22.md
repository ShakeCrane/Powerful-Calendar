# PowerCal Skin-First Migration — Implementation Checkpoint

Date: 2026-10-22. Branch: main.

## Target architecture

The complete normal product UI comes from one replaceable .pcskin package.
PowerCal Core owns data and all authorized business operations. Skin Studio
opens that exact bundled skin. If the skin is missing or corrupted, only a
minimal independent recovery facility remains.

**This checkpoint is an incremental milestone, NOT a completed factory cutover.**
The native product UI remains the safe fallback until functional parity.

## Implemented

- Canonical tracked source: tools/skin-studio/default-clear/source/.
  The Clear draft's visual tokens and licensed font have been copied into
  this runtime source without changing the original untracked design folder.
- Reproducible build script: tools/skin-studio/build_default_clear.py.
  The APK asset is app/src/main/assets/powercal-clear-default.pcskin.
  Verify mode fails when APK bytes differ from the canonical source.
- Skin Studio's startup endpoint now reads that same APK asset, checks hashes
  and returns the same HTML/CSS/JS and artwork. For ordinary user exports,
  Studio forks the protected factory ID to a new installable custom ID.
- The Android skin chooser exposes a verified, protected, opt-in Clear
  candidate. The export command copies the same APK asset unchanged, and
  import/delete cannot overwrite the protected factory ID.
- Real host projections and guarded actions now support timeline, a
  recurrence-aware 42-day month view, Memo completion, AI history/chat
  sending, and creating an event or Memo draft.
- The versioned WebMessagePort bridge enforces bounded inputs, event revisions,
  exact action schemas and host-side locks. No direct database, permissions,
  model credentials, or arbitrary Android calls are exposed to skin code.
- Studio permits HTML form events while CSP blocks actual form navigation.
  Decorative text/images are pointer-transparent, so ordinary users do not
  accidentally hide real interactions behind decoration layers.

## Outstanding blockers — do not switch the factory startup skin yet

1. A full Web event editor: form validation, recurrence, commit/delete,
   confirmation, conflict detection and recovery.
2. AI proposal review/publish, attachments, batch operations, full chat
   states, and all error/cancellation paths.
3. Fully functional Web draft deck and editing/confirmation.
4. Protected settings and native permission controls represented in skin
   layout without leaking credentials to Web scripts.
5. Clear visual acceptance for all pages, sheets, overlays, dark/reduced
   motion and accessibility; the source design is explicitly still a draft.
6. Android device-level screenshot and interaction parity across DPI,
   font metrics, WebView versions, keyboard, hot switching, crash and resume.
7. Only after these tests pass may we activate this package by default,
   safely migrate existing selections and remove the redundant full
   native product UI. Keep a minimal independent recovery path.

## Acceptance contract

- Source build, APK asset, Studio startup and in-app bundled export must
  match byte-for-byte. Custom exported forks change only the skin ID/name
  and the user's intentionally edited files.
- No lost business operations when switching away from native UI.
- The user must be able to change only the background or icons in Studio,
  export, import, and still use the entire app.
- No empty/interrupted skin can lock the user out of recovery or destroy data.
- Factory cutover is forbidden until full-device functional evidence exists.

## Verification commands

    python tools/skin-studio/build_default_clear.py --verify
    python -m unittest discover -s tools/skin-studio/tests -q
    node --check tools/skin-studio/default-clear/source/web/app.js
    .\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

Browser E2E: tools/skin-studio/tests/e2e_v3_browser.cjs

# Skin Runtime Parity — official Clear and third-party v3 packages

Status: **implemented and JVM-tested** for the Web v3 runtime. This document
does not claim that the whole product has finished the Skin-First migration.

## Non-negotiable rule

A skin's `id`, author, physical storage location, or "builtIn" catalogue
flag must never grant a different rendering engine, Host Bridge command,
database privilege, or lifecycle behavior.

The official Clear v3 package is a factory-distributed instance of the same
package format used for imports. The bundled package and any imported v3 skin
are verified using the same `decode(bytes, loadImages=true)`, activated
through the same `PowerCalSkinRegistry.activate(pack)` and rendered using
the same `SkinAppShell -> WebSkinRoot -> SecureWebSkin` path. They receive
read/action capability decisions **only** from the package's declared,
validated capability set, not a trusted-brand or factory-skin check.

## Allowed distribution-only differences

- Clear is supplied as an APK asset, uses a reserved factory ID and cannot
  be overwritten/deleted as a user import.
- Third-party skins are installed in a bounded app-private package directory
  and may be deleted by the user. Installation does not switch the active skin.
- Missing or damaged packages cause a safe recovery. The original native
  fallback is temporarily retained until the remaining product UI is
  fully expressed through the shared Host Bridge.
- Skin Studio forks the reserved factory ID before exporting an editable
  third-party copy, without rewriting its HTML, CSS, JS or binary assets.

No WebView, database, AI, event, notification or settings operation is
authorized on the basis of being the default Clear package.

## Automated proof

`SkinPackageManagerTest.officialClearAndReidentifiedThirdPartyCloneHaveIdenticalRuntime`

1. Reads the **real APK Clear asset**, not a synthetic minimal package.
2. Repackages it with only the manifest ID/name changed.
3. Installs it through the public third-party import path.
4. Activates factory and imported packages through the same picker API.
5. Compares every runtime Web file byte-for-byte, entrypoint, declared
   capabilities, fullCanvas, tokens, and effective Host Bridge read/action
   policy. No factory-only capability is exposed.
6. Verifies third-party selection persists and can be restored after
   the skin manager reloads.
7. Returns to the independent native safety fallback, and removes the
   test import without touching calendar data.

`SkinPackageManagerTest.webBridgeCapabilitiesDependOnDeclaredPermissionsNotSkinIdentity`

Proves that read/action access is gated by the package's capability
declaration, not the package ID.

## Remaining Skin-First work

This milestone removes a real discrepancy in v3 decoding and selection.
It **does not** switch fresh installations to Clear: the original native
Compose UI is still the initial safe fallback, and some business paths
(event editor, draft deck, full AI proposal review, system settings)
still lack complete Web contracts.

Only after the feature-complete v3 Host Bridge, Web screens, emergency
recovery isolation and Android-device acceptance can the factory selection
change and the redundant full native product UI be removed. That eventual
factory switch must not add an ID-based exception to the runtime.

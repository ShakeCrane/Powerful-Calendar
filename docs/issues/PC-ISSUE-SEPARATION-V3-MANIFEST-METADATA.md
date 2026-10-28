# PC-ISSUE-SEPARATION-V3-MANIFEST-METADATA — v3 Manifest metadata compatibility

Date: 2026-10-29

Status: **PASS — narrow S0-PKG-04 v3 metadata compatibility only**

Branch: `codex/separation-s0-inventory`

Implementation commit: `fe94518b74e927f5f7eef39bcd28804a6b32ce59` (pushed to `origin/codex/separation-s0-inventory`)

## Scope and acceptance

This closes the specific mismatch in reconstructed acceptance item `S0-PKG-04`: the Python Skin Studio v3 validator and Android package loader must agree on the handling of top-level v3 Manifest fields. The chosen compatibility rule is that unknown top-level fields are opaque metadata. Studio preserves their JSON values through import/edit/export/re-import; the current Android host ignores them and keeps the imported package archive intact. Only the closed, explicitly parsed `capabilities` field can affect Host access, and unsupported capability values remain rejected.

This is not a PASS for the full reconstructed `S0-PKG-04` matrix row, arbitrary third-party packages, installed-package re-edit, Android WebView behavior, or Native/Skin parity. Those remain `NOT TESTED` as recorded in the matrix.

## Root cause

`tools/skin-studio/runtime_package_v3.py::validate_manifest` rejected every top-level Manifest key outside the Python Studio allowlist. `SkinPackageManager.decode` validated fields it consumed and the supported capability values, but ignored unrelated top-level keys. Therefore Android could install a v3 package that Studio could not inspect/edit/re-import. This was an interoperability mismatch, not evidence that unknown metadata granted runtime authority.

## Changes

- Removed the Studio v3 root-key rejection while retaining all required-field, type, package, hash, path, remote-resource, and capability validation. Existing manifest-copy/edit flows preserve unrecognized JSON fields.
- Documented the opaque metadata and closed-capability policy in `docs/SKIN_PACK_V3.md` and `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`.
- Added Python tests for metadata build/unpack, CLI inspect/edit/validate/re-import, and HTTP Studio import/preview/export/re-import. Tests also verify package source files remain byte-identical and an unsupported capability is still rejected.
- Added a Robolectric `SkinPackageManagerTest` using a package with `x-vendor-metadata.claims = ["host.action"]`. It installs/selects the package, verifies the exact ZIP bytes are retained, verifies the metadata grants neither read nor action access, verifies `host.delete` is rejected, and verifies the calendar database file length does not grow. Production Android parser semantics did not need to change; its existing ignore-and-retain behavior is now documented and covered.
- Updated `docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` and this report. No user data, credentials, or package assets were committed.

## Reproduction and test evidence

### Before the fix

The new Python regression and the minimal `validate_manifest` probe failed at the prior `unknown v3 manifest key` check for an otherwise valid package with `x-vendor-metadata`. The initial red-run console output was not saved as a standalone artifact, so it has no log hash and is cited only as a reproduced failure, not as a retained evidence artifact.

### After the fix

| Check | Result | Evidence |
| --- | --- | --- |
| Runtime package tests (`test_runtime_v3.py`) | **4/4 PASS**; includes opaque metadata round-trip and unsupported capability rejection | Included in the full Studio log below |
| Project operations tests (`test_project_ops_v3.py`) | **9/9 PASS**; CLI inspect/edit/validate/re-import preserves metadata and source bytes | Included in the full Studio log below |
| HTTP Studio preview tests (`test_v3_preview.py`) | **4/4 PASS**; import → CSS edit → preview → export → re-import preserves metadata and files | `.local/android-test/reports/separation-s0-v3-manifest/studio-manifest-preview-focused.log`; SHA-256 `E8FA0BF0BCB26ED24ED55F1F07DBA04B1C07002CB680D386B5D1909A3807DF7C` |
| Full Skin Studio Python suite | **54/54 PASS** | `.local/android-test/reports/separation-s0-v3-manifest/studio-python-full.log`; SHA-256 `8F3B13A19460527292A6A8EE9D48FA142EE68ADC472185538FB4AE401E1F0EA4` |
| Focused Android Robolectric class (`SkinPackageManagerTest`) | **10/10 PASS**, including the installed-byte, no-access-grant and unsupported-capability assertions | `.local/android-test/reports/separation-s0-v3-manifest/gradle-skin-package-manager-focused.log`; SHA-256 `4C031B01D3D887C72C89EE806EF46197C4D642CCE806352D04473D7FE104DF73`; focused JUnit XML SHA-256 `68AD2357032D207D76945615B11E2F0B5A3BF39AA4B8CA0213C5575C4F80BF8D` |
| Full offline Gradle gate | **606/606 JVM tests, 83 suites, 0 failures/errors/skips; 88/88 tasks PASS**. `verifySkinSourceBoundary`, Debug and AndroidTest APK assembly succeeded; lint reported **0 errors / 51 warnings**. This full run preceded the final assertion-only addition to the focused test; the updated class was then rerun 10/10. Production sources did not change after the full gate. | `.local/android-test/reports/separation-s0-v3-manifest/gradle-full-gate.log`; SHA-256 `95C21763D5F592450DDA11A6B533EB454059380C3B4C42946134EAC4FD06F651` |
| Chrome/CDP Studio E2E | **PASS** as adjacent Studio source/project/Memo/AI mock coverage; it is not Android WebView evidence and is not the metadata acceptance test | `.local/android-test/reports/separation-s0-v3-manifest/chrome-studio-e2e.log`; SHA-256 `E1A453DB24EDEBB97B142770AB293174F90A80F7C02CEB8D4FDB9650E55F3A8A` |
| Canonical Clear package verification | **PASS**, 8 files / 378,172 bytes | `.local/android-test/reports/separation-s0-v3-manifest/clear-package-verify.log`; log SHA-256 `75C26E16C5523825A08B0F8DC77E2B2B49317F4305E15074C9F6477AC056CDAF`; package SHA-256 `0dc5a9bc6606e3b7a60aa2af5014ccedeb3303f421c7956dad0c0ba036d353a7` |
| JavaScript syntax checks | **PASS** for `v3-studio.js` and `e2e_v3_source.cjs` (`node --check`) | Exit code 0 for both checks |

Gradle was run on Windows using Gradle 8.14.3 in offline mode. The wrapper could not create its lock under the default user Gradle cache in this restricted worktree; the direct Gradle distribution and workspace-local Gradle home were used with the existing dependency cache read-only. No cache was cleared. The full APK hashes are:

- Debug APK, 66,238,329 bytes: `3532A2D731525A03D228FFA054FBF2F8658A94A03C2EB3D1AB3E849CC2C1242A`.
- AndroidTest APK, 1,287,639 bytes: `60F08FFC420D8C67860FE9D85938C5237CF3C48CD49D5EFC260DC1187C5C9B90`.
- Lint XML: `00991887413720FF29884BC81997F76A854C4E8FDFBA403D06D78C7D1423578A`.

The full gate command was equivalent to:

```text
gradle :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

### Device boundary

No AVD launcher was available at the checked repository-local SDK path. Android verification here is Robolectric plus APK compilation, not an installed Android WebView run. Android WebView, emulator runtime, and physical/OEM device behavior are **NOT TESTED**. Physical-device testing remains with the user.

## Files and commits

Implementation and contract commit `fe94518b74e927f5f7eef39bcd28804a6b32ce59` (`fix(分离): 保留 v3 Manifest 扩展元数据`) was pushed first. This report, the matrix row, and the progress entry are delivered in a separate documentation commit pushed afterward.

Implementation files:

- `app/src/main/java/dev/powerfulcalendar/ui/v2/SkinPackageManager.kt`
- `app/src/test/java/dev/powerfulcalendar/ui/v2/SkinPackageManagerTest.kt`
- `tools/skin-studio/runtime_package_v3.py`
- `tools/skin-studio/tests/test_runtime_v3.py`
- `tools/skin-studio/tests/test_project_ops_v3.py`
- `tools/skin-studio/tests/test_v3_preview.py`
- `docs/SKIN_PACK_V3.md`
- `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md`

## Remaining work

- `S0-PKG-04` broader package-field/type matrix and Android WebView parity: **NOT TESTED**.
- `S0-PKG-05` Studio export → Android install/activate/restart/resource equality: **NOT TESTED**.
- `S0-PKG-06` Studio preview versus Android WebView resource/CSP/capability parity: **NOT TESTED**.
- `S0-PKG-07` re-editing an installed third-party package: **NOT TESTED**.
- Full S0 reconstructed interaction parity and overall Skin-First separation remain **IN PROGRESS / NOT TESTED**. This issue does not constitute a separation GO or skin cutover authorization.

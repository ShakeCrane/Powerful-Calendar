# PC-ISSUE-SEPARATION-S0-FUNCTION-INVENTORY — reconstructed source inventory

Date: 2026-10-28

Status: **S0 IN PROGRESS — source inventory expanded; complete S0 and all Native/Skin parity remain NOT TESTED**

Audit worktree: `codex/separation-s0-inventory`

Audited source baseline: `cc31026f81d4df6704dd89704a4ee93f030a5c00`

Frozen Core source baseline: `b1f7629f0c52812e1887443872f43aa6c49908c4`

## Purpose and evidence boundary

The committed matrix grouped the separation surface into 16 SEP rows. Those rows were too coarse to track independently testable controls, write paths, error recovery and Studio/package behavior. This audit expands them into **62 RECONSTRUCTED child IDs**. These IDs are derived from current source, contracts, existing tests and Issue evidence; they are not claimed to be historical IDs from the unavailable 209-item plan.

The audit distinguishes three call chains:

1. **Native → Core:** Compose pages call the existing `PowerCalApp` handlers, `PowerStore`, `EditSession`, `DeckController`, AI/review services, import/export services, Android launchers and reminder controllers.
2. **Clear → Host Bridge → Core:** packaged `web/app.js` / `web/edit.js` send typed WebMessagePort actions; `WebSkinHost` and `WebSkinEditBridge` check bundle, port, capability and schema; `PowerCalApp` re-reads current records and delegates to existing Core services. Sensitive writes and Android permissions remain Host-owned.
3. **Studio → source → `.pcskin` → Runtime:** canonical Clear source and manifest build the APK asset; Studio loads the v3 template, edits a shared package-file model, previews through a Mock Host and exports through the v3 builder; `SkinPackageManager` validates/imports/selects the package and Android WebView serves its files through `SkinAppShell`, `WebSkinRoot` and `SecureWebSkin`.

Primary source anchors include `TimelineScreen.kt`, `MonthScreen.kt`, `MemoCenterScreen.kt`, `PowerCalApp.kt` (`NativeToolsPage`, route handlers and import/export launchers), `MainActivity.kt` (Activity-level recovery), `WebSkinHost.kt`, `WebSkinEditBridge.kt`, `WebSkinTimelineProjection.kt`, `tools/skin-studio/default-clear/source/web/app.js`, `edit.js`, `runtime_package_v3.py`, `project_ops_v3.py`, `v3-studio.js`, `build_default_clear.py` and `SkinPackageManager.kt`. The Native Tools composable has several callbacks injected that its body does not call; those are not counted as reachable UI actions merely because a callback exists. For example, `onImportIcs` has no visible Tools row; a separate AI file route is tracked in `S0-ST-13`.

Chrome preview and Python/JVM tests are not Android WebView evidence. Android WebView evidence is not physical/OEM evidence. Source presence is not behavior verification. Accordingly, the audit did not upgrade any parent SEP row or child parity row to PASS.

## Findings

The expanded [Skin separation parity matrix](../SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md) records, for each child, the Native entry/Core state, Host command/projection and Clear entry, Studio editability, available narrow evidence, and remaining parity status.

| Inventory group | Child IDs | Source-level findings |
| --- | --- | --- |
| Timeline | `S0-TL-01..06` | Native zoom, independent horizontal wings, drag-to-move, true geometry and measured-title folding are not all represented by current Clear/Bridge controls. Clear timeline rendering also substitutes a minimum height / synthetic end in cases where Native uses the event's actual temporal precision. |
| Month | `S0-MO-01..03` | Month paging/date selection, opening an occurrence and creating on a selected date are distinct behaviors. Clear grid taps leave Month for Timeline; this is not the same as retaining an in-month selection. A top-level Activity create may carry the selected local date, which is a narrower path. |
| Memo | `S0-ME-01..03` | The projection is bounded at 250 rows and has no continuation action. Completion toggle is bridged. The Clear Memo row does not currently route to the existing general formal-event editor action. |
| Editor and recurrence | `S0-ED-01..05`, `S0-RP-01` | Transient create, formal edit, lifecycle, delete and occurrence-scope paths are separately tracked. Existing Host actions are not evidence of full Clear validation/conflict/recovery equivalence. |
| Draft deck | `S0-DK-01..03` | Clear supports portions of patch/flush/commit/discard/undo. Formal-event search/replacement, arbitrary deck navigation, full conflict replacement and deleted-event restoration have no demonstrated equivalent command flow. |
| AI | `S0-AI-01..04` | Attachment and selected proposal paths have narrow local-stub/WebView evidence. Full chat controls, batch/cancellation recovery, all proposal operations, live-provider behavior and restart recovery remain unproven. |
| Settings/system operations | `S0-ST-01..22` | Clear `settings` routes to Native Tools. Profile and AI configuration, system-calendar/ICS, backup/restore, skin management/recovery, OS permission flows, reminder diagnostics and general diagnostics do not have complete v3 settings/actions/projections. |
| IDE authoring | `S0-IDE-01..08` | Studio supports raw source/project/resource editing and machine operations. Its layer visual editor is limited, and preview uses a Mock Host. Package authoring and exact Android runtime parity are separate acceptance items. |
| Package/runtime | `S0-PKG-01..07` | Source/APK/template provenance, combined package round trips, Android activation/restart, Studio-vs-WebView behavior and installed third-party re-edit are now independent checks. |

### Confirmed source discrepancy: v3 Manifest unknown fields

`tools/skin-studio/runtime_package_v3.py::validate_manifest` rejects Manifest keys outside the Python `FIELDS` set plus the declared v3 keys. `SkinPackageManager.decode` validates the fields it consumes and the allowed capability values but does not reject unrelated Manifest keys. Thus an archive with an extra Manifest key can be accepted by Android and rejected by the Studio/CLI v3 validator. This is recorded as `S0-PKG-04`, a **source-level parser compatibility discrepancy**. No paired package fixture was executed, so its runtime consequence is not promoted beyond the code observation. The issue remains open for a follow-up that chooses and tests a compatible policy; this report does not silently change the `.pcskin` contract.

Other concrete gaps are enumerated in the matrix, including the missing Memo-row open action, no Clear drag-move command, missing deck conflict/restore commands, no complete AI batch command set, and Native-only settings/system flows. They are candidate implementation tasks, not waived requirements.

## Validation performed

The checks below validate the audited Studio/source/package baseline. They **do not** prove the 62 interaction rows or full Native/Skin parity.

| Check | Result | Evidence |
| --- | --- | --- |
| `python -m unittest discover -s tools/skin-studio/tests -q` | **51/51 PASS** | `.local/android-test/reports/separation-s0-inventory/studio-unittest.log`; SHA-256 `E6C116DBB5ADF6BAC3C37E57A7C0DF1C51076487DF22E6984171792140C5AA5C` |
| `python tools/skin-studio/build_default_clear.py --verify` | **PASS**; 8 files, 377,973 bytes; package SHA-256 `683e1cf8fedd5f46a7099b7dbbf8a637efd60ceaa2647efca25ea2c3a329edd1` | `.local/android-test/reports/separation-s0-inventory/clear-package-verify.log`; log SHA-256 `42B3FC0F50A90A2D440BF4B3B7C31B4D7036CF9F2D99878827DD9777C5ECC3CA` |
| `node --check` on Clear `app.js`, Clear `edit.js`, and `v3-studio.js` | **PASS** (all three exit code 0) | Checks validate JS syntax only. |
| `git diff --cached --check` on all three deliverable documents | **PASS** | No whitespace errors in the staged matrix, Issue report or progress record. |

No Android JVM task, Android WebView/emulator task, physical-device test, live AI Provider, or Native-versus-Skin replay was run as part of this documentation/inventory audit. Existing narrow evidence remains labelled in its own reports and is not restated as a new run.

## Disposition and next work

- The 62 child entries are all `RECONSTRUCTED`; full parity remains `NOT TESTED`, and the 16 SEP parent rows remain `NOT TESTED`.
- S0 is **not complete**: the matrix now provides a much finer source map, but a final pass is still needed for dormant Native callbacks, rare provider/permission failures and a reusable paired replay harness for durable state, identities, reminders, AI authorization, errors and restart recovery.
- No product code or package contract changed in this audit. The next code slice should open an independent Issue and add a regression before changing behavior. High-priority candidates are the Memo-row open action and the v3 Manifest validator compatibility discrepancy; keep the second one within the existing package contract and avoid a silent policy change.
- Native recovery remains intact. No default skin switch, Core business-semantic change or cutover is claimed or authorized by this report.

# PC-ISSUE-047 — Human/AI same-event draft cardinality

Date: 2026-10-24

Severity: P0 candidate invariant

Status: **RESOLVED / PASS (narrow PowerStore persistence contract)**

## Owner decision

The owner clarified that a same-event human draft and AI proposal do not become separate actionable cards. Keep at most one actionable draft for an existing event. Each later successfully persisted proposal overwrites that draft in place, preserving `draftId` and `queueOrder`; the last saved proposal supplies the current draft contents. The formal event remains unchanged until a separate explicit confirmation and commit. The existing writer gate continues to reject AI writes while a human editor lease is active.

This decision supersedes the earlier G9-06 wording in `PRODUCT_BASELINE_G1-G11.md` that allowed multiple same-event cards and resolves the conflict with `CORE_EDITING_V4.md`.

## Change and evidence

Added `PowerStoreTest.humanAndAiProposalsForSameEventOverwriteInLastSaveOrderAcrossRestart`. In an isolated Robolectric SQLite store it saves human → AI → human proposals for one committed event, then verifies:

- All three saves keep the same `draftId` and `queueOrder` while incrementing `draftVersion` for changed content.
- Exactly one actionable draft remains, containing the final human save.
- The committed formal event remains unchanged.
- Reopening SQLite preserves the draft identity, order, version and final content.

The production storage behavior already matched this rule; no production code changed. This test exercises the PowerStore actor boundary without a live editor lease. It does not claim Compose editor, full AI workflow, live provider, Android device or physical-device behavior.

## Verification

- Focused Gradle method run completed successfully. Log: `.local/android-test/reports/freeze-20261024/pc-issue-162-same-event-last-save-focused-20261024.log`, SHA-256 `46E826C46EA6EC4601B33C89A77461984667B55AFCF67A3DADFE0358EB3D3246`.
- The full `PowerStoreTest` XML contains the new testcase and reports 90/90 pass, 0 failures/errors/skips. XML SHA-256 `78ED5765B288C7A9E22571125B19F4443C118C29D4E7D8016562A4D68279F681`.
- Full offline Gradle passed 492/492 JVM tests across 66 suites, 0 failures/errors/skips; source-boundary verification and Debug/AndroidTest APK assembly passed; lint reported 0 errors and 58 warnings. Full log SHA-256 `79A07BC8CC8079BBC10ACC08E2AD9F04F3DB7FD9704FB9E4387CB1A1C30C047A`.
- Debug APK SHA-256 `EE7E4850A147AC3B3F21326DCB209B0967F14DE6990D56EE05A2976FEB252BCD`; AndroidTest APK SHA-256 `72EC1D24090FAF68E2AD2D11A1C8931A80726C4C7B08E3C7786D382D0EE1E823`.
- Test commit `f1fe242fab94b3b27e0dfd03931381f708312318` (`test(core-freeze): 验证同事件提案最后保存覆盖`) is pushed.

## Acceptance result

`REC-P0-054` is PASS for the approved single-actionable-draft/last-save-overwrite persistence rule. The original 209-case checklist remains unavailable, so this reconstructed row remains `UNMAPPED`. Structural ledger validation checks accounting only and does not establish product behavior. The overall core freeze remains **HOLD**.

Structural-only validation r27 records 210 active reconstructed rows (P0/P1/P2 144/65/1): 195 PASS, 0 FAIL, 8 BLOCKED, 2 NOT TESTED and 5 SPEC UNRESOLVED. Historical IDs mapped=0 and `product_behavior_tested=false`. Artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-risk-matrix-validated-20261024-r27-pc-issue-047.json` SHA-256 `DD35CF840505A206C3D749254565D4D59A2E4B2968BC4B8AE743905DE3A617B9`; ledger SHA-256 `E6B8A6B8CAB574B0147B80B7537C189851A5D9FE058AF55B5B26C778891F5A4F`. These are structural counts, not product test results; the overall freeze remains **HOLD**.

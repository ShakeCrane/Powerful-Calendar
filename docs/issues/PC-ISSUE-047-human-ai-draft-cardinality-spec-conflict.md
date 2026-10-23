# PC-ISSUE-047 — Human/AI draft cardinality specification conflict

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **SPEC UNRESOLVED / OVERALL FREEZE HOLD**

## Question and finding

`REC-P0-054` asks whether human and AI proposal cards targeting the same event remain independent. The checked-in product and storage contracts prescribe incompatible behavior:

- `docs/PRODUCT_BASELINE_G1-G11.md` says an event may have any number of draft cards carrying the same `eventSeq` (lines 22–24), and that human and AI proposals can coexist as distinct drafts (line 27). Its header identifies it as confirmed product decisions and says it defines intended behavior.
- `docs/CORE_EDITING_V4.md` says each `event_seq` has at most one actionable draft and that a partial unique index enforces this (lines 3–4). Git history dates this contract to `f0d88f9` on 2026-10-22.
- `PowerStore` and the regression documented by `PC-ISSUE-032` follow the single-actionable-draft rule by reusing the same draft ID and queue slot for successive proposals.

The repository does not record an explicit product decision superseding the same-event coexistence requirement. A later implementation/storage contract and a passing implementation regression establish current behavior, but do not by themselves resolve which product behavior is approved. “Independent” also needs to be reconciled with the V4 single actionable slot before a truthful PASS/FAIL can be assigned.

## Change

No product or test source changed. `REC-P0-054` is now **SPEC UNRESOLVED**, with this report linked in the candidate ledger. The evidence records the conflicting source text and current implementation direction; it does not claim a newly executed product behavior test or choose one contract on the user's behalf.

## Verification evidence and limits

- Read-only source review: `docs/PRODUCT_BASELINE_G1-G11.md`, `docs/CORE_EDITING_V4.md`, `docs/CORE_CONTRACT_FREEZE_2026-10-21.md`, and the `PC-ISSUE-032` report.
- Existing implementation regression: `PowerStoreTest.repeatedSameEventProposalReusesOneActionableDraftAcrossRestart`, as reported in `docs/issues/PC-ISSUE-032-one-actionable-draft-per-event.md`. This demonstrates the current V4 storage behavior; it does not resolve product-spec precedence and is not new evidence for coexistence.
- No UI, emulator, physical-device, or new product-behavior test was run for this specification audit.
- Structural-only ledger validation: `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-047.json`, SHA-256 `B3CC485ABB73FFE06963282BEF90C728A9695AFD7A34EA32E1906E8FE3AB9703`; ledger SHA-256 `1F513978790FA3AEDCB8E195ABFEC9A5BD7755F42839A14F4A39FA674CF970C4`. It confirms 209 unique rows, the 126/82/1 priority partition, and 92/0/9/101/7 status counts. It checks structure and accounting only; `product_behavior_tested` is false.

The candidate remains `UNMAPPED` to the unavailable original 209-case checklist. Overall verdict remains **HOLD**.

## Git

This report and synchronized freeze documentation are delivered in a separate documentation-only conventional commit. No application code, test source, business data, UI, design asset, or protected untracked file is changed.

## Remaining decision

The product specification must identify whether same-event human and AI cards may coexist as independently actionable drafts, and whether that requirement supersedes the SQLite v4 single-actionable-draft rule. Until the repository contains an authoritative resolution, `REC-P0-054` stays SPEC UNRESOLVED.

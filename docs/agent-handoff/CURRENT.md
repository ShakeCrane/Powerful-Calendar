PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
BASELINE: f4c0f6c6d9e31c6bfc4d84dfc0937f550a32e92a
CURRENT_TASK: G-04
TASK_STATUS: READY
LAST_COMPLETED_TASK: G-03 (PASS)
NEXT_TASK: G-04
NEXT_ACTION: Execute G-04 only

OWNER_GOAL:
  Core: Owns business behavior, data, transactions, reminders, AI, permissions, and stable feature APIs.
  Skin: Owns normal presentation and requests Core capabilities through the approved APIs.
  Default_skin: 正式出厂只保留唯一默认皮肤（一套未美化的 .pcskin）；Native is the safety recovery path.
  Studio: Create a complete skin from a blank project, including components, bindings, assets, editing, preview, export, and Android runtime.
  Beautification: After separation acceptance, apply Claude design work through the Studio API without changing Core.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
PROTECTED_ASSETS: docs/agent-handoff/WORKTREE_DISPOSITION.md

LATEST_VERIFIED_EVIDENCE:
  - "G-01 PASS: clean independent Worktree at the fetched integration baseline."
  - "G-02 PASS: 29 Worktrees inventoried; protected changes retained; ledger commit f4c0f6c6d9e31c6bfc4d84dfc0937f550a32e92a."
  - "G-03 PASS: UTF-8, task-pointer, history-preservation, scope, and Git diff checks passed; documentation-only commit recorded in this branch."

BLOCKERS: G-04 technical baseline is not yet run; historical SEP parity remains unsigned and cutover stays HOLD.

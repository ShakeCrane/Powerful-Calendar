# PowerCal editing/storage contract — SQLite v4

- Each `event_seq` has one current formal object and at most one actionable draft (`PENDING`,
  `CONFLICT`, or `INVALID`). A partial unique index enforces this invariant. Per owner clarification
  on 2026-10-24, saving a later human or AI proposal for the same event overwrites the existing
  actionable row in place: preserve `draft_id` and `queue_order`, increment `draft_version` only
  when content/operation changes, and let the last successfully persisted save win. This changes
  only the draft; the formal object still requires explicit confirmation and commit.
- Upgrade/legacy-backup normalization deterministically retains the greatest tuple
  `(updated_ms, draft_version, queue_order, draft_id)`. Other rows remain byte-for-byte payload
  history with their identity/order/version/timestamps and become `DISCARDED`; their prior status
  and reason are recorded in `draft_migration_audit`.
- A confirmed draft commit updates the formal snapshot and finalizes every same-event actionable
  draft in the same transaction. Discard undo is refused when the active slot is occupied.
- Formal editing is an `EditSession`: fields live in memory and no replacement draft is allocated
  on open or typing. Existing active drafts resume automatically. A meaningful net difference is
  checkpointed once on explicit exit/switch/`ON_STOP`; failure leaves live memory and the editor
  lease intact. Explicit Save writes directly, stays open, atomically clears active checkpoints,
  and does not create a revision for a semantic no-op.
- Confirmed direct delete writes the next-revision tombstone, retires the identity, cancels schedule
  intents, discards active drafts, and records the commit in one transaction.
- `human_editor` and the existing fenced AI batch form a reciprocal global writer gate. AI calendar
  mutation cannot begin while a human editor lease is live; AI lock rejects human event/draft,
  import, completion, publish, and delete writes. Ordinary AI chat does not acquire this gate.
  Leases heartbeat and may only be recovered after a bounded stale timeout.
- Commit history and existing idempotency keys remain authoritative. This contract adds no rollback
  or versions UI.

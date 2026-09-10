# Security, privacy, model-risk, and abuse considerations

Scope: the Phase 2 probabilistic matching stretch and its human-confirmation workflow. Phase 1's
deterministic controls (source immutability, idempotency, segregation of duties, auditability,
failure isolation, degraded mode) still apply and are documented in
[assumptions.md](assumptions.md) and [decision-log.md](decision-log.md).

## Security

- The probabilistic scorer is read-only with respect to financial state; it can only annotate a
  `PROBABLE_MATCH` and attach evidence. No score path mutates a reconciliation outcome.
- Promotion to reconciled requires two-actor authorization (OPERATOR proposes, APPROVER confirms,
  self-approval rejected) — the same segregation of duties as Phase 1 overrides. Role checks gate the
  confirmation path.
- Every confirm/reject is written to the append-only audit trail (input, rule/model + score +
  per-field contributions, actor, timestamp, before/after state, reason), so a compromised or
  malicious approver still leaves a tamper-evident trail.

## Privacy

- Synthetic data only; no production/customer/employee/PII ever enters the system.
- Similarity scoring uses reference identifiers, amounts, and timestamps — no personal attributes. In
  a real deployment a customer surrogate ID (not raw PII) is the only identity carried, and the
  ground-truth manifest must never reach the runtime (guarded by `GroundTruthIsolationTest`).

## Model risk

- The "model" is deterministic bounded similarity with fixed config weights, not a learned model —
  reproducible, explainable, and auditable by construction; there is no training data, drift, or
  opaque inference.
- Known limitation: precision is bounded (~0.50 on the evaluation seeds) because the scorer cannot
  distinguish a genuine discrepancy from a recoverable one by score alone. This is exactly why
  promotion is human-gated and why the score is decision *support*, never a decision. Measured
  precision/recall is published separately from Phase 1 ([phase2-design.md](phase2-design.md) §9) so
  the stretch never masks the deterministic result.
- Residual false-positive noise (~3 per 2000) is documented in [known-limitations.md](known-limitations.md).

## Abuse considerations

- A malicious operator cannot self-promote (segregation of duties), cannot alter a source event
  (immutability), and cannot make value disappear (unresolved stays in the queue until an audited
  action resolves it).
- Threshold/weight tuning is configuration, not code, so a change to matching aggressiveness is
  reviewable and version-controlled, not a silent code path.
- The deterministic fallback (probabilistic disabled) means the system stays fully usable and safe if
  the probabilistic path is attacked, degraded, or distrusted — it can be switched off without
  affecting Phase 1 correctness (proven byte-identical by the disabled-mode test).

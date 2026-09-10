# Phase 2 design — probabilistic matching (Level 4)

> Status: **IMPLEMENTED (Milestones 15b–15d)**. The reviewed 15a design is retained below and
> annotated where implementation refined it — the partner-component renormalization (§2) and the
> candidacy filter (§1). Final measured numbers are in §9.
> Phase 2 is probabilistic matching only, per MASTER_PROMPT.md §5. It is read-only with respect to
> financial state except through an explicit, audited human confirmation.

## 0. Scope and non-negotiables inherited from Phase 1

- Probabilistic matching is **Level 4** of the matching hierarchy, running only after Level 1
  (exact), Level 2 (composite), and Level 3 (timing/grace window).
- It must **never** silently convert a probable match into a reconciled transaction (§3, §10).
- It must have a **deterministic fallback**: with Phase 2 disabled, Phase 1 output is identical.
- Ground truth stays outside the reconciliation runtime; the scorer must not import
  `evaluation`/ground-truth types (guarded today by `GroundTruthIsolationTest`).

## 1. Input population

Phase 2 scores **only** canonical events left in `matchingState = UNRESOLVED` after Levels 1–3.

Explicitly included:
- Events that reached `ExceptionMaterializer` as unresolved (the current 136 / 135 unresolved
  business events on Seed A / Seed B).

Explicitly **excluded** — never re-scored:
- `EXACT_MATCH` (Level 1), `COMPOSITE_MATCH` (Level 2) — already reconciled deterministically.
- `TIMING_DIFFERENCE_PENDING` (Level 3) — valid pending inside the grace window; it is not an
  exception yet and must not be pulled into probabilistic scoring.
- `DUPLICATE_EVENT` residue that is already correctly resolved (survivor matched, duplicate
  quarantined).

Candidate pairing (deterministic): for each **unresolved Originator** event (authoritative
disbursement intent), the scorer forms candidate pairs against **unresolved LMS and Bank** events
only. An event already consumed by a Level 1/2/3 match is never a candidate. This guarantees Phase 2
can only ever *add* information about the leftover population, never disturb a settled match.

### Candidacy eligibility (candidacy filter, added in 15d)

Before the scoring formula runs, a deterministic candidacy pre-filter gates the population, mirroring
Level 1's 3-way requirement. It does not change the four component functions or weights.

1. **Reversal/status exclusion (applied first).** A candidate leg with `sourceStatus = REVERSED` or a
   populated `reversalReference` is excluded from candidacy entirely (never scored) — a deterministic
   signal already on the canonical record.
2. **Group completeness.** An originator is scored only if, on the remaining eligible legs, it has
   both an LMS leg and a Bank leg that each reach `completenessThreshold`. A single intact leg (e.g.
   an LMS booking with no bank settlement, or whose bank was reversed) is not recoverable — there is
   nothing to reconcile into.

`completenessThreshold` (config, default 0.80) is a **separate** gate from `confirmationThreshold`:
candidacy eligibility and promotion eligibility are different concerns and are tuned independently.
The bar sits above the sequential-ID lexical-adjacency noise floor — a numerically-adjacent but
unrelated leg scores ~0.58 on reference similarity, a real counterpart ~0.93 — so it separates
genuine legs from spurious ones. A small residual (~3 per 2000) remains; see
[known-limitations.md](known-limitations.md).

## 2. Scoring model

Each candidate pair receives a bounded score in `[0.0, 1.0]` from four pure component functions of
stored field values. All weights and bands are **config**, not code.

| Component | Fields compared | Function (deterministic) | Range |
|-----------|-----------------|--------------------------|-------|
| Reference similarity | linked instruction reference, partner loan reference, loan reference | `1 − normalizedLevenshtein(a, b)` | [0,1] |
| Amount proximity | Originator amount vs LMS booked / Bank debit amount | `max(0, 1 − |a−b| / amountBandInr)` | [0,1] |
| Timestamp proximity | source/business timestamps | `max(0, 1 − |Δt| / timeBandHours)` | [0,1] |
| Partner agreement | partner / partner code | `1.0` if equal else `0.0` | {0,1} |

Aggregate score (weighted mean, normalized so it is bounded regardless of weight values):

```
score = (w_ref*s_ref + w_amt*s_amt + w_time*s_time + w_partner*s_partner)
        / (w_ref + w_amt + w_time + w_partner)
```

Why it is bounded and deterministic:
- Every component is a pure function of immutable stored fields; there is **no randomness**, no
  runtime-learned weights, no external model. Weights are fixed config loaded once.
- Numerator ≤ denominator because each `s ∈ [0,1]`, so `score ∈ [0,1]`.
- **Same input + same config ⇒ same score, always** (satisfies §3 reproducibility). Candidate
  ordering and ties are broken deterministically by immutable source record ID, so the selected
  best candidate is stable.

## 3. Threshold policy (config-driven)

Two thresholds define three bands. The values below are **initial defaults to be tuned on Seed A**
and then frozen before Seed B (per the §7 protocol); they are not hard-coded in logic.

Proposed addition to `config/reconciliation.yml` (nested under the existing `reconciliation:` key,
alongside `closePolicy`):

```yaml
reconciliation:
  probabilistic:
    enabled: false            # deterministic fallback default; Phase 1 unaffected
    weights:
      reference: 0.40
      amount: 0.35
      timestamp: 0.15
      partner: 0.10
    amountBandInr: 100.00     # amount closeness decays to 0 beyond this band (approved, see 8.2)
    timeBandHours: 6          # timestamp closeness decays to 0 beyond this band
    surfaceThreshold: 0.50    # below -> NOT surfaced as probable; stays plain UNRESOLVED
    confirmationThreshold: 0.80  # at/above -> PROBABLE_MATCH eligible for human confirmation
    completenessThreshold: 0.80  # per-leg candidacy bar; both LMS and Bank legs must reach it (15d)
```

| Band | Condition | Outcome |
|------|-----------|---------|
| Not worth surfacing | `score < surfaceThreshold` | Stays `UNRESOLVED`; no probable annotation; still in the exception queue exactly as Phase 1 |
| Probable, stays unresolved | `surfaceThreshold ≤ score < confirmationThreshold` | `PROBABLE_MATCH` annotation surfaced for review; **remains unresolved**; not eligible for promotion |
| Probable, confirmation-eligible | `score ≥ confirmationThreshold` | `PROBABLE_MATCH` annotation, flagged **eligible for human confirmation**; still **not reconciled** |

There is **no automatic promotion path at any score**. The design deliberately omits the
"auto-reconcile above a documented threshold" option that MASTER_PROMPT §5 permits. Promotion to a
reconciled state happens **only** through the human confirmation workflow in §5. This was reviewed
and confirmed.

## 4. State model

A probable match produces `matchingState = PROBABLE_MATCH` (the enum value already exists in
`domain/MatchingState.java`) and exposes, on the canonical record / exception, a self-contained
evidence bundle:

- `probableScore` (the aggregate `[0,1]` value)
- `contributingFieldScores` (per-component breakdown: reference / amount / timestamp / partner)
- `matchedCandidateReference` (immutable source record ID of the best candidate)
- `thresholdBand` (which of the three bands, plus the two threshold values in effect)
- `evidence` (human-readable summary of the comparison)

Hard invariant: a `PROBABLE_MATCH` **never** transitions to a reconciled/resolved financial state
automatically — **even above `confirmationThreshold`**. `reconciliationState` stays `UNRESOLVED`
and the exception stays open until the confirmation workflow in §5 runs. The score is decision
*support*, not a decision.

## 5. Human confirmation / promotion workflow

Mirrors the Phase 1 two-actor override (`ExceptionOverrideService`) exactly, including
segregation of duties:

- **Requester**: `OPERATOR` role proposes promoting a `PROBABLE_MATCH` to reconciled.
- **Approver**: `APPROVER` role confirms.
- **SoD rule** (identical to Phase 1): `requester ≠ approver`, and the **exception creator ≠
  approver**. Self-approval is rejected.
- A **reason** is mandatory; blank is rejected.

On confirmation, the authorized action resolves the exception (`ExceptionStatus → RESOLVED`,
`matchingState` promoted, exception removed from the unresolved blocking set) and writes to the
append-only audit trail:

- input (business event ID / exception ID + the candidate reference),
- rule/model = `probabilistic-match-v1` + the score and per-field contributions,
- decision (e.g. `PROMOTED_PROBABLE_TO_RECONCILED`),
- actor (approver), timestamp,
- before/after `ExceptionStateSnapshot`,
- reason,
- and an `overrideHistory` entry cross-linked to the audit entry (same mechanism used in Phase 1).

**Rejection** is also audited (decision = `REJECTED_PROBABLE`), leaving the event `UNRESOLVED`.
This reuses the existing audit + override infrastructure rather than inventing a parallel one.

## 6. Deterministic fallback

Controlled by `reconciliation.probabilistic.enabled` (default `false`).

- **Disabled**: the Level 4 scoring stage is skipped entirely. No record receives `PROBABLE_MATCH`;
  the pipeline’s canonical events, exceptions, close/hold decision, and persisted snapshot are
  **byte-identical to Phase 1 today**. This is the shippable degraded mode (§3).
- **Enabled**: scoring only *adds* annotations and a human-gated promotion path. With **zero
  confirmations**, close/hold blocking records and blocking INR are **still identical** to Phase 1,
  because probable matches do not reduce unresolved INR on their own.
- Therefore the **only** path by which Phase 2 can change any financial total is an explicit,
  audited human confirmation (§5). Confirmed in review: the score by itself never changes blocking
  INR or close/hold status.

## 7. Evaluation plan

A **separate** Phase 2 evaluation, restricted to the post-L1/2/3 unresolved population, kept out of
the Phase 1 scorecard (own section / `reports/phase2/`).

### 7.1 Recoverable-population gate (run first)

The generator is **not** being modified for Phase 2. Before any precision/recall claim, the
evaluation must first report, against ground truth, the **count of genuinely recoverable pairs
actually present in the current unresolved set** — i.e. unresolved events whose true counterpart
also exists in the unresolved population and could in principle be matched by similarity.

- If that recoverable count is large enough to produce meaningful precision/recall, proceed with
  §7.2.
- If it is **too small to be meaningful, STOP and report back** before touching the generator. New
  anomaly classes must **not** be added on implementer judgment; that decision is escalated for
  review. (This resolves review item §8.3.)

### 7.2 Metrics (only if the gate passes)

Against ground truth, computed outside the runtime:
- **Precision** = correct promotions among all pairs proposed at `score ≥ confirmationThreshold`
  (i.e. would a confirmation have been right?).
- **Recall** = recoverable true matches found among all recoverable true matches present in the
  unresolved population.
- Supporting: score distribution, precision/recall vs threshold (sensitivity), count + INR of the
  recoverable subset.

### 7.3 Protocol (identical discipline to Phase 1)

1. **Seed A (tune only)** — choose weights/bands/thresholds; freeze them.
2. **Seed B (fresh, no changes)** — run with frozen config; report precision/recall; compare A vs B.

The scorer stays free of ground-truth imports; `GroundTruthIsolationTest` will be extended to cover
the new Level 4 package.

## 8. Review outcomes

Items flagged in the draft, and their resolution in this approved design.

### Confirmed decisions

1. **No auto-reconcile, ever** — the optional §5 auto-threshold path is dropped entirely; promotion
   is human-only (§3, §5). *Approved.*
2. **Wider amount band than Phase 1 tolerance** — Phase 1 exact matching uses INR 1.00 tolerance;
   probabilistic scoring intentionally tolerates larger amount gaps (`amountBandInr = 100.00`). A
   confirmed probable match can therefore reconcile amounts differing by more than the Phase 1
   tolerance; this is exactly why promotion requires human sign-off. *Approved as-is.*
3. **Recoverable-population dependency** — the generator is **not** modified now. Implement scoring
   against the current unresolved population first, add the §7.1 recoverable-pair count step, and
   **stop and report** if the count is too small for meaningful precision/recall. No new anomaly
   classes without explicit approval. *Approved.*
4. **Close/hold coupling** — a confirmed promotion reduces unresolved INR and can flip HOLD to
   CLOSE, but that financial-state change is triggered **only** by the §5 audited human action,
   never by the score. *Confirmed.*

### Remaining tunable (not blocking) values

- **Threshold values** (`surfaceThreshold = 0.50`, `confirmationThreshold = 0.80`) and **weights**
  (`reference 0.40 / amount 0.35 / timestamp 0.15 / partner 0.10`, `timeBandHours = 6`) are initial
  defaults to be **tuned on Seed A and then frozen before Seed B** (§7.3). They are configuration,
  not code, and are not finalized silently — the tuned values will be reported with the Phase 2
  evaluation for sign-off.

## 9. Implementation outcomes (final, measured)

Frozen config used for both seeds: weights `0.40 / 0.35 / 0.15 / 0.10`, `amountBandInr = 100.00`,
`timeBandHours = 6`, `surfaceThreshold = 0.50`, `confirmationThreshold = 0.80`,
`completenessThreshold = 0.80`. The generator injects a `REFERENCE_MISMATCH` class
(`referenceMismatchRate = 0.03`) so a genuinely recoverable population exists; the recoverable-pair
gate (§7.1) cleared at 42 (Seed A) / 53 (Seed B) true positives.

Precision/recall (positives = `REFERENCE_MISMATCH` recovered to the true same-index counterpart):

| Metric | Seed A (12345, tune) | Seed B (67890, frozen) |
|---|---:|---:|
| Unresolved originators | 178 | 188 |
| Recoverable positives | 42 | 53 |
| Recoverable INR | ₹1,829,591 | ₹2,381,350 |
| True positives | 42 | 53 |
| False positives | 42 | 45 |
| **Precision** | **0.500** | **0.541** |
| **Recall** | **1.000** | **1.000** |

- **Recall 1.0** on both: every reference-mismatch is recovered to its true counterpart.
- **Precision ~0.50** is bounded by the honest residual: the false positives are almost entirely
  genuine `AMOUNT_MISMATCH` / `STATUS_MISMATCH` discrepancies (correct counterpart identified, but the
  case should stay an exception) plus ~3/2000 lexical-adjacency noise records. These are correctly
  surfaced for **human review** — the mandatory confirmation workflow (§5) is exactly what turns this
  precision into a safe control rather than an auto-decision.
- **Threshold sensitivity is flat** (0.50–0.90): every surfaced record scores ≥ 0.9, so the
  confirmation threshold does not separate true recoveries from the discrepancy residual. Improving
  precision further would require weight/feature work (e.g. making status a feature), deliberately
  deferred — the residual is an accepted human-review population, not a defect.

Phase 1 §9 re-verification with the new class present (both seeds): false-match exposure **0**,
exception coverage **1.0**, control-total integrity **true**. Expected shifts only: Seed A exact
matches 1830 → 1788, straight-through 0.922 → 0.901; Seed B exact 1826 → 1773, straight-through
0.923 → 0.8965. The Phase 1 gate holds.

## 10. Production-scale design (documentation-level, not implemented)

This is a design-level sketch of a real deployment; the current build is a deterministic
single-operator case study and intentionally does not implement any of this.

- **Target scale assumption.** ~1–5M disbursement instructions/day across tens of partners, batch
  reconciliation on a fixed cut-off plus intraday micro-batches; peak ingest ~a few thousand rows/sec
  per feed. The current file-based single-process design targets ~thousands of events per batch.
- **Component boundaries.** The existing stage packages (ingestion → normalization → matching →
  exceptions → close/hold → evaluation) become independently deployable services behind an internal
  API/event bus. Ingestion + normalization scale horizontally (stateless per row); matching and
  close/hold are stateful per batch and shard by partner + business-day. The Level 4 scorer and the
  human-confirmation workflow are a separate service, so probabilistic load never affects the
  deterministic path.
- **Storage approach.** Replace the JSON `DurableRunStore` with an RDBMS (PostgreSQL) for canonical
  events, exceptions, audit trail, and decisions, plus object storage for immutable raw source payloads
  (hash-addressed for lineage). Append-only audit table; run snapshots keyed by the same deterministic
  batch fingerprint used today.
- **Consistency model.** Strong consistency within a batch (processed and persisted atomically,
  idempotent by fingerprint); cross-batch and cross-partner eventually consistent. Reconciliation
  decisions are immutable once written; corrections are new versioned events, never mutations
  (source-immutability control carried over from Phase 1).
- **SLOs (targets).** Batch reconciliation completes within the cut-off + 2h grace window; exception
  materialization p99 < 5 min after batch close; close/hold decision available < 10 min after all feeds
  land; probabilistic scoring is best-effort and off the critical path (never blocks the deterministic
  close). Availability 99.9% for the deterministic path, lower for the probabilistic stretch.
- **Cost assumptions.** Cost is dominated by RDBMS + object storage + batch-matching compute;
  probabilistic scoring adds bounded CPU only (no GPUs/model hosting — it is deterministic similarity,
  not ML) and scales roughly linearly with row volume. The human-confirmation queue is the main
  operational (people) cost, not infrastructure.


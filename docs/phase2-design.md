# Phase 2 design — probabilistic matching (Level 4)

> Status: **APPROVED design, not yet implemented**. This document is the reviewed Phase 2 design.
> Implementation (Milestone 15b) is a separate, gated milestone and is not part of this commit.
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

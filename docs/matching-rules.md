# Matching and exception materialization rules

## Level 1 exact-match evidence

An exact match requires all of the following to agree across Originator, LMS, and Bank for one business event:

- stable identifiers and expected relationships
- currency
- amount within the configured INR 1.00 tolerance
- compatible event types
- source status agreement (Originator status equals LMS status, Bank status is `POSTED`)
- clean validation state (`VALID`) on every candidate

A record failing any of these does not exact-match and falls through to Level 2/3 or remains unresolved.

## Exception materialization (fixes exception-coverage-zero)

Reconciliation previously produced unresolved canonical events but nothing turned them into exception records, so the evaluation queue was empty and exception coverage read 0.0000 despite millions in unresolved INR. This was an automatic-gate failure: unresolved value left the queue.

`ExceptionMaterializer` now runs after Levels 1-3 and creates one exception per unresolved business event, classifying by observable evidence only (never ground truth):

- no LMS or no Bank counterpart -> `MISSING_EVENT`
- late-arrival validation beyond resolution -> `TIMING_DIFFERENCE`
- currency or amount disagreement beyond tolerance -> `AMOUNT_MISMATCH`
- status disagreement (LMS status differs, or Bank not `POSTED`) -> `STATUS_MISMATCH`

Matched and pending events are skipped.

## Duplicate-event decision

Decision: matching the surviving row is the correct financial outcome, so the matching engine is not changed for duplicates.

Reasoning: ingestion (Milestone 4) quarantines the duplicate row. The surviving original still has full evidence agreement with LMS and Bank, so exact-matching it is correct. The real gap was twofold:

1. No exception recorded the duplicate. `ExceptionMaterializer` now emits a `DUPLICATE_EVENT` exception for each quarantined duplicate, routed to Engineering / Source Partner per the ownership table, separate from whether the underlying transaction reconciled.
2. Evaluation counted a correctly-resolved duplicate as false-match exposure. That was an evaluation-metric bug, the same shape as the earlier composite double-count bug. `EvaluationService` now treats a matched `DUPLICATE_EVENT` business event as an expected resolution, not false exposure.

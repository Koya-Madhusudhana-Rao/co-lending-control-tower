# Decision Log

## Durable file-based Phase 1 persistence

- Decision: Use JSON files under `data/runs/<batchFingerprint>/` for Phase 1 run persistence.
- Context: The project needs durable canonical records, exceptions, audit entries, and close/hold decisions so totals can be recalculated without rerunning the pipeline. PostgreSQL was scaffolded in Milestone 1 but was not being used by Milestones 2-11.
- Options considered: Full JPA/PostgreSQL persistence estimated at 3-5 days; lightweight file persistence estimated at approximately 0.5-1 day; continued in-memory state.
- Chosen: Lightweight JSON file persistence with one directory per deterministic batch fingerprint.
- Why: It satisfies the contract's reproducible-totals requirement while protecting time for Milestones 12-14. It also makes restart-safe idempotency demonstrable without adding unused infrastructure.
- Trade-offs: JSON files are less suitable for concurrent writers, querying, retention management, and multi-process deployment than a relational database. Atomic file replacement and deterministic fingerprints provide the Phase 1 safety boundary, but this is not a production-scale persistence design.
- Rejected alternative: Full RDBMS persistence was deferred because its additional schema, migration, transaction, and integration-test work is disproportionate for the current case-study timeline. PostgreSQL/JPA dependencies and the unused Docker service were removed rather than left as dead infrastructure.
- Documentation impact: Local setup now requires only JDK 17 and Maven. Docker, PostgreSQL, JPA, and Testcontainers are not prerequisites for Phase 1.

## Generator anomaly injection: physical corruption, not just labels

- Decision: The seeded feed generator must physically corrupt the feed rows it labels as anomalous, not merely record the anomaly in ground truth.
- Context: The generator produced a ground-truth manifest listing missing/mismatched/duplicate/late instructions, but the emitted Originator/LMS/Bank feeds still contained clean, fully-agreeing rows for many of those instructions. The pipeline therefore reconciled them correctly, and evaluation — comparing against ground truth — reported failures that did not reflect any real pipeline defect. The evaluation harness was measuring the generator's dishonesty, not the reconciliation logic.
- Options considered: (a) loosen evaluation to tolerate the mismatch; (b) treat ground truth as advisory only; (c) fix the generator so the physical feed data actually reflects each labelled anomaly.
- Chosen: (c) — the generator now removes/alters/duplicates/delays the actual rows so the feed content matches its own ground-truth labels.
- Why: Ground truth is only a valid oracle if the feeds physically embody the anomalies it claims. Anything else makes every downstream metric unfalsifiable.
- Trade-offs: The generator is more complex and must keep physical mutation and label emission in lock-step; a future change to one must update the other.
- Rejected alternative: Loosening evaluation (a/b) was rejected because it would have hidden the exact class of silent-divergence bug the control tower exists to catch, and would have made the scorecard meaningless.

## Level 1 exact match must check status and timing, not just amount/identity

- Decision: Level 1 exact reconciliation must require source-status agreement and clean validation state, not only identifier/currency/amount agreement.
- Context: Level 1 was matching business events whose amounts and identifiers agreed while their statuses disagreed (e.g. LMS status differing, or Bank not `POSTED`), and it ignored validation/timing state. This produced false matches: genuinely mismatched or late events were reported as reconciled, so their unresolved value never reached the exception queue.
- Options considered: (a) leave Level 1 permissive and rely on later levels to catch status/timing; (b) tighten Level 1 to require status agreement and `VALID` validation state on every candidate before declaring an exact match.
- Chosen: (b) — an exact match now requires identifiers, currency, amount within tolerance, compatible event types, source-status agreement (Originator == LMS, Bank `POSTED`), and `VALID` state on every candidate.
- Why: A false positive at Level 1 is the most dangerous failure mode — it removes an item from scrutiny entirely. Level 1 must be the strictest gate, not the most lenient.
- Trade-offs: Slightly fewer auto-resolved items at Level 1; more events fall through to Level 2/3 or to exceptions. That is the correct direction for a financial control.
- Rejected alternative: Relying on downstream levels (a) was rejected because once Level 1 declares a match the event is treated as resolved and never re-examined, so the defect could not be recovered later.

## Duplicate residue is a resolved-with-exception outcome, not false-match exposure

- Decision: A correctly-resolved duplicate business event is an expected resolution (with its own `DUPLICATE_EVENT` exception), not false-match exposure.
- Context: Ingestion quarantines the duplicate Originator row and the surviving original still agrees with LMS and Bank, so exact-matching the survivor is correct. Two gaps existed: nothing recorded that a duplicate had occurred, and the evaluation metric counted the correctly-matched survivor as false-match exposure — the same double-counting shape as an earlier composite bug.
- Options considered: (a) change the matcher to refuse to match any event that had a duplicate; (b) leave evaluation counting it as false exposure; (c) keep matching the survivor, emit a `DUPLICATE_EVENT` exception for the quarantined row, and teach evaluation to treat a matched duplicate as an expected resolution.
- Chosen: (c).
- Why: The financially correct outcome is that the transaction reconciles once and the duplication is separately visible and owned (Engineering / Source Partner). Distinguishing "duplicate residue" from "false match" keeps the scorecard honest.
- Trade-offs: Evaluation must special-case matched `DUPLICATE_EVENT` events; the classification logic is slightly more nuanced.
- Rejected alternative: Refusing to match the survivor (a) was rejected because it would manufacture an unresolved exposure that does not exist and understate straight-through resolution; leaving the miscount (b) was rejected because it inflated false-match exposure with correctly-handled cases.

## Close/hold must read the materialized exception queue, counted once per business event

- Decision: The close/hold decision must be driven by the materialized exception queue, counting each blocking business event once, restricted to Originator exposure and excluding `DUPLICATE_EVENT`.
- Context: The pipeline computed close/hold from `batch.detections()` (an empty upstream list) rather than the exceptions produced by `ExceptionMaterializer`, so material unresolved value did not block closure. A naive fix then triple-counted the same business event across Originator, LMS, and Bank, and counted resolved duplicates as blocking, inflating the blocking total.
- Options considered: (a) keep reading `detections()`; (b) sum blocking value across all three sources; (c) drive close/hold from the materialized queue, count blocking exposure from Originator canonical events only, and exclude `DUPLICATE_EVENT` from blocking.
- Chosen: (c).
- Why: The batch total is defined from Originator (authoritative disbursement intent), so blocking exposure must be measured on the same basis to be comparable; counting the same event three times would both distort the hold formula and mislead Finance. Resolved duplicates are not unresolved exposure.
- Trade-offs: Close/hold now depends on materialization running first and on correct source attribution; the ordering is enforced in `Phase1PipelineService`.
- Rejected alternative: Reading `detections()` (a) left the control silently disabled; cross-source summation (b) produced a blocking figure that did not reconcile against the Originator-based batch total.
- Verified outcome: Seed A (12345) produces a HOLD decision with blocking value INR 6,051,643 across 136 blocking references and 158 persisted exceptions, cross-checked against the evaluation harness.

## Interface: CLI trigger over REST API

- Decision: satisfy the case study's user-journey requirement (page 4) with a CLI trigger (the opt-in `demo` profile runner) rather than a REST API.
- Context: an early preference (and the original stack note) leaned toward REST endpoints. On confirming the actual PDF requirement, the user journey — generate a dataset, run the pipeline, produce a decision and reports — is fully satisfied by a scripted/CLI trigger; the PDF does not mandate REST.
- Chosen: a one-command CLI entry point plus generated reports (CSV/JSON/quality report), no REST layer.
- Why: deliberate scope discipline. A REST API would add controllers, serialization, error handling, and integration tests the requirement does not need, spending time better used on correctness, evaluation, and documentation (Correctness > simplicity > UI/API surface).
- Trade-offs: no live HTTP interface for external callers; a future integration would add REST in front of the existing services (the stage packages are already service-shaped).
- This is a considered scope choice confirmed against the actual PDF, not an oversight.

## What I would build next (beyond the recorded trade-offs)

Distinct from the trade-offs above (which explain choices already made), these are the next things I would build given more time:

1. RDBMS persistence (PostgreSQL) replacing the JSON run store, unlocking querying, retention, and concurrent multi-operator workflows (the storage design in `docs/phase2-design.md` §10).
2. Precision improvement for probabilistic matching by making status a scoring feature and adding a mutual-consistency signal — carefully, so it does not reclassify the genuine `AMOUNT_MISMATCH` residual (`docs/known-limitations.md`).
3. A REST API + minimal operator UI over the existing service-shaped stages for the exception queue and the human-confirmation workflow.
4. Blocking-reference readability: map raw source locations to instruction IDs in the close/hold output (`docs/known-limitations.md`).
5. Richer generator anomaly classes (orphan reversal, schema drift, control-total mismatch) to widen evaluation coverage, gated the same way `REFERENCE_MISMATCH` was.
6. Observability and the staged-rollout tooling described in the architecture Production path.
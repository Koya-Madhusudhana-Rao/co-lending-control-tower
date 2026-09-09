# Known limitations

These are accepted Phase 1 limitations, documented rather than fixed now.

## Concurrent writes / locking on `data/runs/`

- Current design assumes a single-operator, single-process batch workflow.
- `DurableRunStore` writes one directory per deterministic batch fingerprint and does not take file locks or coordinate concurrent writers.
- This is acceptable for Phase 1 because batches are processed sequentially by one operator, not concurrently by multiple processes.
- Multi-writer locking is a documented future improvement, not a Phase 1 requirement.

## Corrupted persisted-JSON recovery

- Current behavior: if a persisted run file under `data/runs/<batchFingerprint>/` is corrupted or unreadable, `DurableRunStore` throws `IllegalStateException`, which propagates out of `Phase1PipelineService.process(...)`.
- So today a corrupted persisted run fails loudly (crash on that batch) rather than degrading gracefully or rebuilding from source.
- It does not silently return wrong totals, and other batches are unaffected.
- Graceful recovery (detect corruption, quarantine the bad run directory, and reprocess from source) is a documented future improvement, not required now.

## Blocking references surface as raw source locations, not instruction IDs

- Current behavior: a HOLD decision's blocking references are raw source locations (e.g. `originator.csv#line=1`) rather than the instruction IDs (e.g. `INSTR-000001`) that appear elsewhere in the evaluation output.
- These references are accurate and traceable back to the exact source row, so this is a readability limitation, not a correctness defect.
- Mapping blocking references to human-readable instruction IDs for the close/hold output is a documented future polish item, not a Phase 1 requirement.

## Probabilistic candidacy: small residual false-positive rate

- The Phase 2 candidacy filter leaves a small residual false-positive rate (~3 records per ~2000) driven by sequential-ID lexical adjacency coinciding with a near-amount match, which lets a spurious bank leg clear the completeness threshold.
- A human approver would reject these on inspection; they are not eliminated because the alternative fix (mutual amount-consistency between legs) would risk miscategorizing the genuine `AMOUNT_MISMATCH` discrepancy residual, a worse trade-off than a handful of noise records.



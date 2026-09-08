# Decision Log

## Durable file-based Phase 1 persistence

- Decision: Use JSON files under `data/runs/<batchFingerprint>/` for Phase 1 run persistence.
- Context: The project needs durable canonical records, exceptions, audit entries, and close/hold decisions so totals can be recalculated without rerunning the pipeline. PostgreSQL was scaffolded in Milestone 1 but was not being used by Milestones 2-11.
- Options considered: Full JPA/PostgreSQL persistence estimated at 3-5 days; lightweight file persistence estimated at approximately 0.5-1 day; continued in-memory state.
- Chosen: Lightweight JSON file persistence with one directory per deterministic batch fingerprint.
- Why: It satisfies the contract's reproducible-totals requirement while protecting time for Milestones 12-14. It also makes restart-safe idempotency demonstrable without adding unused infrastructure.
- Trade-offs: JSON files are less suitable for concurrent writers, querying, retention management, and multi-process deployment than a relational database. Atomic file replacement and deterministic fingerprints provide the Phase 1 safety boundary, but this is not a production-scale persistence design.
- Rejected alternative: Full RDBMS persistence was deferred because its additional schema, migration, transaction, and integration-test work is disproportionate for the current case-study timeline. PostgreSQL/JPA dependencies and the unused Docker service were removed rather than left as dead infrastructure.
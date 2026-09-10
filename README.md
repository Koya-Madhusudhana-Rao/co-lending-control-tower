# Co-lending Control Tower

A synthetic co-lending reconciliation and control system built for the Vivriti Next case study.

## Overview

This project implements a deterministic reconciliation control tower for disbursements flowing across:
- Originator instructions
- LMS bookings
- Bank settlement feeds

The system records evidence, detects mismatches, preserves lineage, and allows exception-driven operational review without forcing unresolved differences to disappear.

## Stack

- Java 17
- Spring Boot 3
- Maven
- JUnit 5
- Durable JSON run files under `data/runs/`

## Prerequisites

- JDK 17
- Maven

No Docker or database is required for the current Phase 1 implementation. Runtime state is persisted as durable JSON files under `data/runs/<batchFingerprint>/`, and `data/` is ignored by Git.

## Run the app

```bash
mvn spring-boot:run
```

This starts the Spring context only; it does not run the pipeline.

## Run the Phase 1 demo (one command)

The demo generates two fixed seeds, runs the full pipeline end-to-end
(generate → ingest → normalize → reconcile → materialize exceptions → close/hold → persist),
and prints the featured seed's close/hold decision plus the Seed A vs Seed B scorecard comparison.
It is opt-in via the `demo` profile and self-terminates.

```bash
mvn -q spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=generate --seed 12345"
```

The featured seed defaults to `12345` (Seed A) if `--seed` is omitted. Durable run artifacts are
written under `data/runs/<batchFingerprint>/` and a human-readable summary under `data/demo/`.

## Test

```bash
mvn test
```


## Project status

Both phases are complete. **Phase 1** (deterministic reconciliation: ingestion, canonical model, exact/composite/timing matching, exception queue, close/hold, evaluation) passed its full gate checklist. **Phase 2** (Level 4 probabilistic matching with a candidacy filter, a two-actor human confirmation workflow, and separately-measured precision/recall) is implemented and documented in `docs/phase2-design.md`. PostgreSQL/JPA infrastructure was intentionally removed after the persistence decision in `docs/decision-log.md`; runtime state persists as durable JSON under `data/runs/`.

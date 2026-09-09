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

## Test

```bash
mvn test
```

## Project status

This repository is in the deterministic Phase 1 implementation stage. PostgreSQL/JPA infrastructure was intentionally removed after the persistence decision in `docs/decision-log.md`.

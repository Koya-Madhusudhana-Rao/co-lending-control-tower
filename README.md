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
- PostgreSQL (Docker Compose)
- JUnit 5
- Testcontainers

## Prerequisites

- JDK 17
- Maven
- Docker Desktop running locally

## Local database

```bash
docker compose up -d
```

Then verify:

```bash
docker compose ps
```

## Run the app

```bash
mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Project status

This repository is in the Phase 1 foundation stage.

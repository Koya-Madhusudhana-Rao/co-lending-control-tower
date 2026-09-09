# Project contract

Read this file as the authoritative build contract for this repo.

This is a Java 17 + Spring Boot + Maven project for a synthetic co-lending control tower. Phase 1 uses durable JSON run files under `data/runs/`; Docker, PostgreSQL, JPA, and Testcontainers are not active prerequisites. Follow the milestone plan in the project brief and do not skip Phase 1 before validation.

Core rules:
- Build incrementally with one milestone at a time.
- Keep a clean repo: no generated data or build output committed.
- Do not silently change financial behavior or hide uncertainty.
- Use configuration for thresholds and rule values.
- Keep the deterministic Phase 1 logic separate from optional later probabilistic matching.

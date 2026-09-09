# Demo script (12 minutes)

This is the timed walkthrough for the Phase 1 (and Phase 2 design) demo. Timings follow the case
study's required breakdown. Commands are run from the repository root with only JDK 17 and Maven
installed. Where a single command demonstrates a segment, it is shown inline.

> Honest framing to state on camera: during this build, the evaluation harness caught **three real
> integration bugs before submission** — a generator that labelled anomalies without physically
> corrupting the feeds, an empty exception queue (materialization was missing), and a close/hold
> control that read the wrong list and so never blocked. Each was found, fixed, and locked behind a
> test. That the scorecard exposed them is a strength of the design, not something to gloss over.

## 0:00–1:00 — Problem, scope, architecture, assumptions

- Co-lending disbursements flow across Originator instructions, LMS bookings, and Bank settlements;
  they diverge, and unresolved value must never be silently written off.
- Show the component/data-flow diagram in [architecture.md](architecture.md).
- State the key assumptions: Asia/Kolkata timezone, 2-hour grace window, INR 1.00 amount tolerance,
  Originator as authoritative for disbursement intent. Reference [assumptions.md](assumptions.md).

## 1:00–2:00 — Generate a fresh dataset and show data quality

- Explain the seeded generator: it deterministically produces the three feeds **and** a ground-truth
  manifest, physically injecting missing rows, amount/status drift, duplicates, and late arrivals.
- Note the anomaly rate (~6%) and that ground truth is used only for later scoring, never by matching.

## 2:00–3:00 — Ingest three feeds, including malformed and late input

- Walk through ingestion as the trust boundary: schema validation, malformed-row rejection, and
  duplicate quarantine. Nothing downstream sees unvalidated input.

## 3:00–4:15 — Exact and composite deterministic reconciliation

- Level 1 exact match requires identifiers, currency, amount within tolerance, event type,
  **source-status agreement**, and `VALID` state on every candidate — reference
  [matching-rules.md](matching-rules.md).
- Level 2/3 composite + timing handles what does not exact-match.

## 4:15–5:15 — Timing difference and unresolved exception

- Show a late-but-within-window item held as `TIMING_DIFFERENCE_PENDING`, and an item past the window
  materialized into the exception queue with an owner and priority.

## 5:15–6:15 — Refusal of an unsafe match and exception ownership

- Show that a status-mismatched event is **not** exact-matched (the Level 1 tightening), and that its
  unresolved value reaches the queue rather than being reported as reconciled.
- Show the ownership routing from [assumptions.md](assumptions.md) (e.g. amount mismatch → Finance /
  Lending Ops; duplicate → Engineering / Source Partner).

## 6:15–7:15 — Audit, idempotent rerun, and the close/hold result

This is the headline segment. Run the one-command demo exactly as the README documents it:

```bash
mvn -q spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=generate --seed 12345"
```

Show the printed decision block for Seed A:

```
Full-pipeline close/hold decision (generate -> ingest -> normalize -> reconcile -> materialize -> close/hold -> persist):
  decision            = HOLD
  thresholdInr        = 10000
  blockingInr         = 6051643
  blockingRefs        = 136
  exceptionsPersisted = 158
```

- Explain: the batch is **held** because unresolved Originator exposure (INR 6,051,643) exceeds the
  configured threshold — see [close-policy.md](close-policy.md).
- Idempotent rerun: run the same command again and show the run is served from the persisted snapshot
  under `data/runs/<fingerprint>/` without reprocessing — totals reproduce exactly.

## 7:15–8:00 — Phase 1 metrics and limitations

- Show the Seed A vs Seed B scorecard comparison the demo prints (exact/composite match counts,
  exception coverage 1.0000, straight-through rate, control-total integrity) — reference
  [evaluation-scorecard.md](evaluation-scorecard.md).
- State the honest limitation: blocking references currently surface as raw source locations
  (e.g. `originator.csv#line=1`) rather than instruction IDs — real and traceable, but a readability
  polish item, see [known-limitations.md](known-limitations.md).

## 8:00–10:30 — Phase 2 design / implementation

- Walk the Phase 2 direction: RDBMS persistence, probabilistic matching (`PROBABLE`), and richer
  operational tooling — building on the boundaries already established in Phase 1.

## 10:30–12:00 — Degraded mode and production next step

- Explain fail-closed behaviour (corrupted persisted run crashes that batch loudly rather than
  returning wrong totals) and the production path: concurrent-safe persistence, retention, and
  multi-operator workflow.

## One-line reset between takes

```bash
# from a blank slate, exactly as a fresh evaluator would
Remove-Item -Recurse -Force target, data -ErrorAction SilentlyContinue
mvn -q spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=generate --seed 12345"
```

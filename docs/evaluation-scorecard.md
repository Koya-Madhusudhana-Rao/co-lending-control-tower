# Evaluation scorecard

This report replaces prior Seed A / Seed B interpretations. The first report used a generator that labeled anomalies without physically mutating feed files. The second report used corrected feed mutations but exposed a Level 1 matching defect: exact reconciliation did not require status agreement or clean validation state. The metrics below were recomputed after fixing both issues.

## Seed comparison

| Metric | Seed A (`12345`) | Seed B (`67890`) |
| --- | ---: | ---: |
| Business events | 2000 | 2000 |
| Exact-match count | 1830 | 1826 |
| Exact-match INR | 81265142.00 | 81062336.00 |
| Composite-match count | 14 | 20 |
| Composite-match INR | 661047.00 | 866427.00 |
| False-match exposure count | 19 | 18 |
| False-match exposure INR | 803456.00 | 790829.00 |
| Exception coverage rate | 0.0000 | 0.0000 |
| Straight-through rate | 0.9220 | 0.9230 |
| Control-total integrity | true | true |
| Unresolved INR | 6051643.00 | 6084661.00 |
| Unresolved count | 136 | 135 |

## Material differences

- Seed B produced 4 fewer exact-matched business events than Seed A.
- Seed B produced 6 more composite-matched business events than Seed A.
- Seed A produced 1 more false-match exposure event than Seed B.
- Seed A straight-through rate was 0.9220; Seed B straight-through rate was 0.9230.
- Seed B unresolved INR was higher by INR 33018.00.

## Failed cases

False-match exposure is now limited to duplicate-event cases where ingestion quarantines the second duplicate row but the first valid row for the same business event still exact-matches. Missing-event, amount-mismatch, status-mismatch, and timing-difference records are no longer exact-matched by Level 1. Composite-match records resolved by Level 2 are counted as true composite matches, not false matches.

## Remaining evaluation caveat

Exception coverage is 0.0000 for both seeds because the current runtime does not yet derive exception detections directly from unmatched generated feed evidence. The exception queue is deterministic and tested, but this scorecard run did not inject evaluation-derived detections into runtime state.

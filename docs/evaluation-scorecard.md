# Evaluation scorecard

This report replaces the prior Seed A / Seed B interpretation because the earlier generator only labeled anomalies in ground truth and did not physically mutate feed files. The metrics below were recomputed after fixing the generator to inject missing, duplicate, amount mismatch, status mismatch, timing difference, and composite-match records into the feeds themselves.

## Seed comparison

| Metric | Seed A (`12345`) | Seed B (`67890`) |
| --- | ---: | ---: |
| Business events | 2000 | 2000 |
| Exact-match count | 1946 | 1935 |
| Exact-match INR | 86341306.00 | 85890036.00 |
| Composite-match count | 14 | 20 |
| Composite-match INR | 661047.00 | 866427.00 |
| False-match exposure count | 78 | 76 |
| False-match exposure INR | 3372377.00 | 3331696.00 |
| Exception coverage rate | 0.0000 | 0.0000 |
| Straight-through rate | 0.9800 | 0.9775 |
| Control-total integrity | true | true |
| Unresolved INR | 1832646.00 | 2040657.00 |
| Unresolved count | 40 | 45 |

## Material differences

- Seed B produced 11 fewer exact-matched business events than Seed A.
- Seed B produced 6 more composite-matched business events than Seed A.
- Seed A produced 2 more false-match exposure events than Seed B.
- Seed A straight-through rate was 0.9800; Seed B straight-through rate was 0.9775.
- Seed B unresolved INR was higher by INR 208011.00.

## Failed cases

False-match exposure is now meaningful because ground-truth anomalies are physically present in the feeds. The current deterministic runtime still exact-matches some generated anomaly classes, especially status/timing/duplicate cases where Level 1 does not yet inspect status or quarantine lineage as matching blockers.

## Remaining evaluation caveat

Exception coverage is 0.0000 for both seeds because the current runtime does not yet derive exception detections directly from unmatched generated feed evidence. The exception queue is deterministic and tested, but this scorecard run did not inject evaluation-derived detections into runtime state.

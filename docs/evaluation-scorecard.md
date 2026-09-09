# Evaluation scorecard

This report replaces prior Seed A / Seed B interpretations. Earlier reports used a generator that labeled anomalies without mutating feed files, then a corrected generator that exposed a Level 1 matching defect, then a run where unresolved value never entered the exception queue. The metrics below were recomputed after physical anomaly injection, Level 1 status/validation gating, and exception materialization for unresolved and duplicate residue.

## Seed comparison

| Metric | Seed A (`12345`) | Seed B (`67890`) |
| --- | ---: | ---: |
| Business events | 2000 | 2000 |
| Exact-match count | 1830 | 1826 |
| Exact-match INR | 81265142.00 | 81062336.00 |
| Composite-match count | 14 | 20 |
| Composite-match INR | 661047.00 | 866427.00 |
| False-match exposure count | 0 | 0 |
| False-match exposure INR | 0.00 | 0.00 |
| Exception coverage rate | 1.0000 | 1.0000 |
| Straight-through rate | 0.9220 | 0.9230 |
| Control-total integrity | true | true |
| Unresolved INR | 6051643.00 | 6084661.00 |
| Unresolved count | 136 | 135 |

## Material differences

- Seed B produced 4 fewer exact-matched business events than Seed A.
- Seed B produced 6 more composite-matched business events than Seed A.
- Both seeds now show zero false-match exposure.
- Both seeds show full exception coverage of unresolved INR.
- Seed A straight-through rate was 0.9220; Seed B straight-through rate was 0.9230.
- Seed B unresolved INR was higher by INR 33018.00.

## Notes

- False-match exposure is zero because Level 1 requires status agreement and clean validation state, and evaluation counts a correctly-resolved duplicate (survivor matched, duplicate quarantined and recorded as its own `DUPLICATE_EVENT` exception) as an expected resolution rather than false exposure.
- Exception coverage is 1.0000 because `ExceptionMaterializer` creates one exception per unresolved business event after Levels 1-3, so unresolved value is fully represented in the queue rather than disappearing.
- Unresolved counts include injected anomaly classes and reversed-bank settlements, which do not clear Level 1 because the bank status is not `POSTED`; these are surfaced as exceptions, not silently matched.


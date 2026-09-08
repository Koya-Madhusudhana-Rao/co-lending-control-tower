# Close / hold policy

## Deterministic hold formula

`HOLD if unresolvedINR > min(₹10,000, 0.005 × batchTotalINR)`

Equivalent expression:

`HOLD if unresolvedINR > 10000` when `0.005 × batchTotalINR >= 10000`

`HOLD if unresolvedINR > 0.005 × batchTotalINR` when `0.005 × batchTotalINR < 10000`

This is the exact policy used to block closure when unresolved financial exposure remains material.

## Policy intent

- The unresolved queue must remain visible and owned.
- Pending items that are inside the grace window are not treated as unresolved.
- Material unresolved values are held and escalate to Finance or the designated owner.
- Close is only allowed when all unmatched or mismatched INR exposure is either resolved, explicitly pending under policy, or absent.

## Configurable thresholds

The values below are configurable via the reconciliation config and must not be hard-coded in business logic:

- `absoluteThresholdInr: 10000`
- `relativeThresholdRate: 0.005`
- `holdFormula: "unresolvedINR > min(10000, 0.005 * batchTotalINR)"`

## Why this default is safe

This formula is conservative: it prevents a batch from being closed while unresolved exposure is greater than the lesser of a fixed INR floor and a small batch-relative threshold. It preserves the financial safety control requirement that no unresolved value is silently written off or balanced away.

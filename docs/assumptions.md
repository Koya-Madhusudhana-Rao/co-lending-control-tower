# Assumptions and operating policy

## Default timezone and grace window

- Default timezone: Asia/Kolkata (IST)
- Timing difference policy: a valid event is considered pending when it arrives after the reconciliation cut-off but within a configured grace window of 2 continuous business hours.
- Definition of the 2-hour window: measured as continuous clock hours from the cut-off timestamp in IST, regardless of day boundary. If the cut-off is at 17:30 IST, the window ends at 19:30 IST on the same day. It does not automatically roll to the next business day. This is intentional: the policy is a fixed time-window, not a calendar-day bucket, so the same late-arrival rule applies consistently across batches and avoids ambiguity around overnight carry-over.
- Why this is safe: it is deterministic, auditable, and operationally simple. The same batch cannot silently inherit a larger or smaller window based on calendar rollover, and it avoids hidden business-day logic that could distort exception ownership.

## Authoritative source rules

- Originator instructions are authoritative for intended disbursement instructions.
- LMS bookings are authoritative for internal loan booking status once booked.
- Bank settlement records are authoritative for settlement movement and reversal evidence.
- If source data conflicts, the reconciliation state remains unresolved until a deterministic rule or human override is applied.

## Amount tolerance

- Default amount tolerance: INR 1.00 absolute difference.
- Values within INR 1.00 are treated as acceptable rounding or normalization drift.
- Values above INR 1.00 remain material mismatches unless a documented rule explicitly permits an exception.

## Reversal and split rules

- Reversals are tracked via the reversalReference field where present.
- Split relationships are preserved using the parent/reference relationship captured in the canonical event model.
- A reversal does not automatically nullify a previously matched disbursement; it remains visible as a separate financial event with its own reconciliation decision.

## Late-arrival rules

- A record arriving after cut-off but within the configured grace window remains in PENDING / TIMING_DIFFERENCE_PENDING state.
- A record arriving after the grace window expires becomes unresolved and enters the exception queue with the relevant owner and priority.

## Finality and business status rules

- PENDING is used only for valid timing-difference items inside the configured grace window.
- MATCHED indicates deterministic evidence-based agreement under the matching hierarchy.
- PROBABLE is reserved for Phase 2 logic only and never auto-resolves a transaction in Phase 1.
- UNRESOLVED is used when evidence is insufficient or contradictory.
- RESOLVED and CLOSED require explicit rule-based closure or approved human action with audit entry.

## Ownership model

- Missing or malformed partner event: Partner Ops / Integration Support
- Duplicate or idempotency issue: Engineering / Source Partner
- Amount mismatch: Finance / Lending Ops
- Status mismatch: Operations, with Engineering support if needed
- Orphan reversal: Finance + Engineering
- Schema contract breach: Integration Engineering
- Control-total mismatch: Finance; close remains blocked

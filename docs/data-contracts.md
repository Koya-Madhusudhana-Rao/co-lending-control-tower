# Data contracts

## Originator feed contract

Required fields:
- instructionId
- loanReference
- partner
- instructionDateTime
- amount
- currency
- status
- batch
- receivedTime

## LMS feed contract

Required fields:
- bookingId
- internalLoanId
- partnerLoanReference
- bookingDateTime
- bookedAmount
- currency
- status
- batch

## Bank / settlement feed contract

Required fields:
- transactionReference
- linkedInstructionReference
- valueDateTime
- debitAmount
- status
- reversalReference
- batch

## Canonical event model

The canonical model retains source immutability and lineage fields such as:
- sourceSystem
- sourcePartner
- batchId
- immutableSourceRecordId
- businessEventId
- correlationId
- loanId
- customerSurrogateId
- partnerLoanReference
- eventType
- businessDate
- sourceTimestamp
- receivedTimestamp
- reconciliationCutOff
- amount
- currency
- financialComponents
- sourceStatus
- canonicalStatus
- parentReference
- reversalReference
- rawSourceLocation
- payloadHash
- ingestionState
- validationState
- matchingState
- reconciliationState
- exceptionState

## State definitions

- IngestionState: RECEIVED, VALIDATED, QUARANTINED, REJECTED, ARCHIVED
- ValidationState: VALID, INVALID, DUPLICATE, MISSING_REQUIRED_FIELD, MALFORMED_SCHEMA, LATE_ARRIVAL
- MatchingState: UNMATCHED, EXACT_MATCH, COMPOSITE_MATCH, TIMING_DIFFERENCE_PENDING, PROBABLE_MATCH, UNRESOLVED
- ReconciliationState: PENDING, MATCHED, UNRESOLVED, HOLD, CLOSED
- ExceptionState: OPEN, IN_REVIEW, PENDING_OWNER_ACTION, RESOLVED, ESCALATED, CLOSED

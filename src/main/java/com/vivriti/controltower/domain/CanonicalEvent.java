package com.vivriti.controltower.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CanonicalEvent {
    private SourceSystem sourceSystem;
    private String sourcePartner;
    private String batchId;
    private String immutableSourceRecordId;
    private String businessEventId;
    private String correlationId;
    private String loanId;
    private String customerSurrogateId;
    private String partnerLoanReference;
    private String eventType;
    private LocalDate businessDate;
    private LocalDateTime sourceTimestamp;
    private LocalDateTime receivedTimestamp;
    private LocalDateTime reconciliationCutOff;
    private BigDecimal amount;
    private String currency;
    private String financialComponents;
    private String sourceStatus;
    private String canonicalStatus;
    private String parentReference;
    private String reversalReference;
    private String rawSourceLocation;
    private String payloadHash;
    private IngestionState ingestionState;
    private ValidationState validationState;
    private MatchingState matchingState;
    private ReconciliationState reconciliationState;
    private ExceptionState exceptionState;
    private ProbableMatchEvidence probableMatchEvidence;

    public CanonicalEvent() {
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(SourceSystem sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourcePartner() {
        return sourcePartner;
    }

    public void setSourcePartner(String sourcePartner) {
        this.sourcePartner = sourcePartner;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getImmutableSourceRecordId() {
        return immutableSourceRecordId;
    }

    public void setImmutableSourceRecordId(String immutableSourceRecordId) {
        this.immutableSourceRecordId = immutableSourceRecordId;
    }

    public String getBusinessEventId() {
        return businessEventId;
    }

    public void setBusinessEventId(String businessEventId) {
        this.businessEventId = businessEventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getCustomerSurrogateId() {
        return customerSurrogateId;
    }

    public void setCustomerSurrogateId(String customerSurrogateId) {
        this.customerSurrogateId = customerSurrogateId;
    }

    public String getPartnerLoanReference() {
        return partnerLoanReference;
    }

    public void setPartnerLoanReference(String partnerLoanReference) {
        this.partnerLoanReference = partnerLoanReference;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public LocalDateTime getSourceTimestamp() {
        return sourceTimestamp;
    }

    public void setSourceTimestamp(LocalDateTime sourceTimestamp) {
        this.sourceTimestamp = sourceTimestamp;
    }

    public LocalDateTime getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(LocalDateTime receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    public LocalDateTime getReconciliationCutOff() {
        return reconciliationCutOff;
    }

    public void setReconciliationCutOff(LocalDateTime reconciliationCutOff) {
        this.reconciliationCutOff = reconciliationCutOff;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFinancialComponents() {
        return financialComponents;
    }

    public void setFinancialComponents(String financialComponents) {
        this.financialComponents = financialComponents;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus) {
        this.sourceStatus = sourceStatus;
    }

    public String getCanonicalStatus() {
        return canonicalStatus;
    }

    public void setCanonicalStatus(String canonicalStatus) {
        this.canonicalStatus = canonicalStatus;
    }

    public String getParentReference() {
        return parentReference;
    }

    public void setParentReference(String parentReference) {
        this.parentReference = parentReference;
    }

    public String getReversalReference() {
        return reversalReference;
    }

    public void setReversalReference(String reversalReference) {
        this.reversalReference = reversalReference;
    }

    public String getRawSourceLocation() {
        return rawSourceLocation;
    }

    public void setRawSourceLocation(String rawSourceLocation) {
        this.rawSourceLocation = rawSourceLocation;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public IngestionState getIngestionState() {
        return ingestionState;
    }

    public void setIngestionState(IngestionState ingestionState) {
        this.ingestionState = ingestionState;
    }

    public ValidationState getValidationState() {
        return validationState;
    }

    public void setValidationState(ValidationState validationState) {
        this.validationState = validationState;
    }

    public MatchingState getMatchingState() {
        return matchingState;
    }

    public void setMatchingState(MatchingState matchingState) {
        this.matchingState = matchingState;
    }

    public ReconciliationState getReconciliationState() {
        return reconciliationState;
    }

    public void setReconciliationState(ReconciliationState reconciliationState) {
        this.reconciliationState = reconciliationState;
    }

    public ExceptionState getExceptionState() {
        return exceptionState;
    }

    public void setExceptionState(ExceptionState exceptionState) {
        this.exceptionState = exceptionState;
    }

    public ProbableMatchEvidence getProbableMatchEvidence() {
        return probableMatchEvidence;
    }

    public void setProbableMatchEvidence(ProbableMatchEvidence probableMatchEvidence) {
        this.probableMatchEvidence = probableMatchEvidence;
    }
}

package com.vivriti.controltower.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BankSettlementTransaction {
    private String transactionReference;
    private String linkedInstructionReference;
    private LocalDateTime valueDateTime;
    private BigDecimal debitAmount;
    private String status;
    private String reversalReference;
    private String batch;

    public BankSettlementTransaction() {
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getLinkedInstructionReference() {
        return linkedInstructionReference;
    }

    public void setLinkedInstructionReference(String linkedInstructionReference) {
        this.linkedInstructionReference = linkedInstructionReference;
    }

    public LocalDateTime getValueDateTime() {
        return valueDateTime;
    }

    public void setValueDateTime(LocalDateTime valueDateTime) {
        this.valueDateTime = valueDateTime;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReversalReference() {
        return reversalReference;
    }

    public void setReversalReference(String reversalReference) {
        this.reversalReference = reversalReference;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }
}

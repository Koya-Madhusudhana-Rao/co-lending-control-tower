package com.vivriti.controltower.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OriginatorInstruction {
    private String instructionId;
    private String loanReference;
    private String partner;
    private LocalDateTime instructionDateTime;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String batch;
    private LocalDateTime receivedTime;

    public OriginatorInstruction() {
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getLoanReference() {
        return loanReference;
    }

    public void setLoanReference(String loanReference) {
        this.loanReference = loanReference;
    }

    public String getPartner() {
        return partner;
    }

    public void setPartner(String partner) {
        this.partner = partner;
    }

    public LocalDateTime getInstructionDateTime() {
        return instructionDateTime;
    }

    public void setInstructionDateTime(LocalDateTime instructionDateTime) {
        this.instructionDateTime = instructionDateTime;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(LocalDateTime receivedTime) {
        this.receivedTime = receivedTime;
    }
}

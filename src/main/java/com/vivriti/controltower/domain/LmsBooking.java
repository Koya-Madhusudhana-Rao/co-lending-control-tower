package com.vivriti.controltower.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LmsBooking {
    private String bookingId;
    private String internalLoanId;
    private String partnerLoanReference;
    private LocalDateTime bookingDateTime;
    private BigDecimal bookedAmount;
    private String currency;
    private String status;
    private String batch;

    public LmsBooking() {
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getInternalLoanId() {
        return internalLoanId;
    }

    public void setInternalLoanId(String internalLoanId) {
        this.internalLoanId = internalLoanId;
    }

    public String getPartnerLoanReference() {
        return partnerLoanReference;
    }

    public void setPartnerLoanReference(String partnerLoanReference) {
        this.partnerLoanReference = partnerLoanReference;
    }

    public LocalDateTime getBookingDateTime() {
        return bookingDateTime;
    }

    public void setBookingDateTime(LocalDateTime bookingDateTime) {
        this.bookingDateTime = bookingDateTime;
    }

    public BigDecimal getBookedAmount() {
        return bookedAmount;
    }

    public void setBookedAmount(BigDecimal bookedAmount) {
        this.bookedAmount = bookedAmount;
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
}

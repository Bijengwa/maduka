package com.maduka.rentmanager.data.model;

public class PaymentRecord {
    private String paymentId;
    private String tenantUid;
    private String shopId;
    private long amount;
    private int monthsCovered;
    private long paymentDate;
    private String paymentMethod;      // "mpesa" | "bank" | "cash" | "cheque"
    private String receiptReference;
    private String notes;
    private String recordedByUid;
    private String recordedByName;
    private PaymentStatus status = PaymentStatus.PENDING;
    private RejectionReason rejectionReason;
    private String rejectionNote;
    private String confirmedByUid;
    private String confirmedByName;
    // snapshot taken at record time, used to roll back the tenant on reject
    private long previousLastPaymentDate;
    private long previousDueDate;
    private int previousMonthsCovered;
    private long createdAt;
    private long updatedAt;

    public PaymentRecord() {}

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getTenantUid() { return tenantUid; }
    public void setTenantUid(String tenantUid) { this.tenantUid = tenantUid; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public int getMonthsCovered() { return monthsCovered; }
    public void setMonthsCovered(int monthsCovered) { this.monthsCovered = monthsCovered; }
    public long getPaymentDate() { return paymentDate; }
    public void setPaymentDate(long paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReceiptReference() { return receiptReference; }
    public void setReceiptReference(String receiptReference) { this.receiptReference = receiptReference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecordedByUid() { return recordedByUid; }
    public void setRecordedByUid(String recordedByUid) { this.recordedByUid = recordedByUid; }
    public String getRecordedByName() { return recordedByName; }
    public void setRecordedByName(String recordedByName) { this.recordedByName = recordedByName; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(RejectionReason rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRejectionNote() { return rejectionNote; }
    public void setRejectionNote(String rejectionNote) { this.rejectionNote = rejectionNote; }
    public String getConfirmedByUid() { return confirmedByUid; }
    public void setConfirmedByUid(String confirmedByUid) { this.confirmedByUid = confirmedByUid; }
    public String getConfirmedByName() { return confirmedByName; }
    public void setConfirmedByName(String confirmedByName) { this.confirmedByName = confirmedByName; }
    public long getPreviousLastPaymentDate() { return previousLastPaymentDate; }
    public void setPreviousLastPaymentDate(long previousLastPaymentDate) { this.previousLastPaymentDate = previousLastPaymentDate; }
    public long getPreviousDueDate() { return previousDueDate; }
    public void setPreviousDueDate(long previousDueDate) { this.previousDueDate = previousDueDate; }
    public int getPreviousMonthsCovered() { return previousMonthsCovered; }
    public void setPreviousMonthsCovered(int previousMonthsCovered) { this.previousMonthsCovered = previousMonthsCovered; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** True only when a different user than the recorder is trying to act. Two-person rule. */
    public boolean canBeActionedBy(String uid) {
        return uid != null && !uid.equals(recordedByUid);
    }
}

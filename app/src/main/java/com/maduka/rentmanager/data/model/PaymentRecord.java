package com.maduka.rentmanager.data.model;

/** A rent payment recorded by the Super Admin. There is no approval workflow: once written, a
 * payment is immediately a valid recorded payment - see the product-rules amendment that removed
 * the old PENDING/CONFIRMED/REJECTED two-person confirm/reject flow. */
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
    // The phone number the payment was PAID TO (the receiving number) - never the tenant's own
    // phone number, which lives on Tenant.phone.
    private String paymentPhoneNumber;
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
    public String getPaymentPhoneNumber() { return paymentPhoneNumber; }
    public void setPaymentPhoneNumber(String paymentPhoneNumber) { this.paymentPhoneNumber = paymentPhoneNumber; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

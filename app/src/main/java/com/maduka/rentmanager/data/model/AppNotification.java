package com.maduka.rentmanager.data.model;

public class AppNotification {
    public static final String TYPE_OVERDUE = "overdue";
    public static final String TYPE_DUE_TODAY = "due_today";
    public static final String TYPE_DUE_SOON = "due_soon";
    public static final String TYPE_PAYMENT_CONFIRMED = "payment_confirmed";
    public static final String TYPE_PAYMENT_REJECTED = "payment_rejected";
    public static final String TYPE_RECEIPT_AVAILABLE = "receipt_available";
    public static final String TYPE_SYSTEM = "system";

    private String notificationId;
    private String userUid;
    private String type;
    private String title;
    private String message;
    private String relatedTenantUid;
    private String relatedPaymentId;
    private boolean read;
    private long createdAt;

    public AppNotification() {}

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getUserUid() { return userUid; }
    public void setUserUid(String userUid) { this.userUid = userUid; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRelatedTenantUid() { return relatedTenantUid; }
    public void setRelatedTenantUid(String relatedTenantUid) { this.relatedTenantUid = relatedTenantUid; }
    public String getRelatedPaymentId() { return relatedPaymentId; }
    public void setRelatedPaymentId(String relatedPaymentId) { this.relatedPaymentId = relatedPaymentId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

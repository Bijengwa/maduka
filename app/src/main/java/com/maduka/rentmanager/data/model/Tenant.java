package com.maduka.rentmanager.data.model;

public class Tenant {
    private String uid;
    private String name;
    private String phone;
    private String email;          // optional
    private String authEmail;      // Firebase Auth login email (always set)
    private String shopId;         // "A1".."A10"
    private long monthlyRent;
    private long moveInDate;       // epoch millis
    private PresenceStatus presenceStatus = PresenceStatus.YUPO;
    private long lastPresenceCheckAt;
    private long lastPaymentDate;  // epoch millis
    private long dueDate;          // epoch millis
    private int monthsCovered;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String notes;
    private String registeredByUid;
    private long createdAt;
    private long updatedAt;

    public Tenant() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAuthEmail() { return authEmail; }
    public void setAuthEmail(String authEmail) { this.authEmail = authEmail; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(long monthlyRent) { this.monthlyRent = monthlyRent; }
    public long getMoveInDate() { return moveInDate; }
    public void setMoveInDate(long moveInDate) { this.moveInDate = moveInDate; }
    public PresenceStatus getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(PresenceStatus presenceStatus) { this.presenceStatus = presenceStatus; }
    public long getLastPresenceCheckAt() { return lastPresenceCheckAt; }
    public void setLastPresenceCheckAt(long lastPresenceCheckAt) { this.lastPresenceCheckAt = lastPresenceCheckAt; }
    public long getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(long lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }
    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }
    public int getMonthsCovered() { return monthsCovered; }
    public void setMonthsCovered(int monthsCovered) { this.monthsCovered = monthsCovered; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRegisteredByUid() { return registeredByUid; }
    public void setRegisteredByUid(String registeredByUid) { this.registeredByUid = registeredByUid; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

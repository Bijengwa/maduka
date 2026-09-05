package com.maduka.rentmanager.data.model;

public class Shop {
    private String shopId;      // "A1".."A10"
    private long monthlyRent;   // 0 when empty
    private boolean occupied;
    private String tenantUid;   // null when empty
    private long createdAt;
    private long updatedAt;

    public Shop() {}

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public long getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(long monthlyRent) { this.monthlyRent = monthlyRent; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }
    public String getTenantUid() { return tenantUid; }
    public void setTenantUid(String tenantUid) { this.tenantUid = tenantUid; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

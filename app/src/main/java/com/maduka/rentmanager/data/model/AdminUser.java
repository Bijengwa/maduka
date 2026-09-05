package com.maduka.rentmanager.data.model;

import java.util.ArrayList;
import java.util.List;

public class AdminUser {
    private String uid;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String authEmail;
    private List<String> shopsAssigned = new ArrayList<>(); // subset of A1..A10
    private PresenceStatus status = PresenceStatus.YUPO;    // Yupo = active account, Hayupo = disabled
    private long lastLogin;
    private long createdAt;
    private long updatedAt;

    public AdminUser() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAuthEmail() { return authEmail; }
    public void setAuthEmail(String authEmail) { this.authEmail = authEmail; }
    public List<String> getShopsAssigned() { return shopsAssigned; }
    public void setShopsAssigned(List<String> shopsAssigned) { this.shopsAssigned = shopsAssigned; }
    public PresenceStatus getStatus() { return status; }
    public void setStatus(PresenceStatus status) { this.status = status; }
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

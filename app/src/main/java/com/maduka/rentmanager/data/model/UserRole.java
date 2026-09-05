package com.maduka.rentmanager.data.model;

public enum UserRole {
    SUPER_ADMIN("super_admins"), ADMIN("admins"), TENANT("tenants");

    public final String node;
    UserRole(String node) { this.node = node; }
}

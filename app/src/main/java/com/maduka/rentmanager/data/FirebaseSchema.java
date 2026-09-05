package com.maduka.rentmanager.data;

public final class FirebaseSchema {
    private FirebaseSchema() {}

    public static final String SUPER_ADMINS = "super_admins";
    public static final String ADMINS = "admins";
    public static final String TENANTS = "tenants";
    public static final String SHOPS = "shops";
    public static final String PAYMENTS = "payments";
    public static final String NOTIFICATIONS = "notifications";
    public static final String LOGIN_INDEX = "login_index"; // login_index/{role}/{sanitizedKey} -> authEmail, world-readable
}

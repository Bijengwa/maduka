package com.maduka.rentmanager.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "maduka_prefs";
    private static final String KEY_REMEMBER = "remember_me";
    private static final String KEY_ROLE = "remembered_role";
    private static final String KEY_IDENTIFIER = "remembered_identifier";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_SIGN_IN_COUNT = "sign_in_count";

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Prefs get(Context context) { return new Prefs(context); }

    public boolean isRememberMe() { return sp.getBoolean(KEY_REMEMBER, false); }

    public void setRememberMe(boolean remember, String role, String identifier) {
        SharedPreferences.Editor e = sp.edit().putBoolean(KEY_REMEMBER, remember);
        if (remember) { e.putString(KEY_ROLE, role).putString(KEY_IDENTIFIER, identifier); }
        else { e.remove(KEY_ROLE).remove(KEY_IDENTIFIER); }
        e.apply();
    }

    public String rememberedRole() { return sp.getString(KEY_ROLE, null); }
    public String rememberedIdentifier() { return sp.getString(KEY_IDENTIFIER, null); }

    public String language() { return sp.getString(KEY_LANGUAGE, "en"); }
    public void setLanguage(String language) { sp.edit().putString(KEY_LANGUAGE, language).apply(); }

    public long signInCount() { return sp.getLong(KEY_SIGN_IN_COUNT, 0); }
    public long bumpSignInCount() {
        long next = signInCount() + 1;
        sp.edit().putLong(KEY_SIGN_IN_COUNT, next).apply();
        return next;
    }
}

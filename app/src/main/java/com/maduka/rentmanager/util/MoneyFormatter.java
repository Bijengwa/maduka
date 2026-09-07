package com.maduka.rentmanager.util;

import java.util.Locale;

/** Compact display for large TZS amounts on the dashboard's summary cards (a full
 * comma-grouped number breaks a small card's layout) - the full amount stays one tap away,
 * never destroyed. */
public final class MoneyFormatter {
    private MoneyFormatter() {}

    public static String compact(long amount) {
        long abs = Math.abs(amount);
        if (abs >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", amount / 1_000_000.0);
        }
        if (abs >= 1_000) {
            return String.format(Locale.US, "%dK", Math.round(amount / 1000.0));
        }
        return String.valueOf(amount);
    }

    public static String full(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }
}

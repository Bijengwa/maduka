package com.maduka.rentmanager.util;

public final class LoginKeyUtil {
    private LoginKeyUtil() {}

    /**
     * Firebase RTDB keys forbid '.', '#', '$', '/', '[', ']'; '@' is also
     * stripped for key readability even though it's RTDB-legal. Also used to
     * normalize phone numbers (strip spaces/dashes) and usernames/emails
     * (lowercase) so the same identifier always resolves to the same
     * login_index key regardless of how the user typed it.
     */
    /** Firebase Auth needs an email, but people here have none: derive a hidden one from the phone. */
    public static String authEmailFor(String phone) {
        return phone + "@maduka.app";
    }

    public static String sanitize(String rawIdentifier) {
        if (rawIdentifier == null) return "";
        String trimmed = rawIdentifier.trim().toLowerCase(java.util.Locale.US);
        boolean looksLikePhone = trimmed.replaceAll("[^0-9]", "").length() >= 7
                && trimmed.replaceAll("[0-9 +()\\-]", "").isEmpty();
        if (looksLikePhone) {
            String digits = trimmed.replaceAll("[^0-9]", "");
            if (digits.length() == 9) return "255" + digits;
            if (digits.length() == 10 && digits.startsWith("0")) return "255" + digits.substring(1);
            return digits;
        }
        return trimmed.replaceAll("[.#$\\[\\]/@]", "_");
    }
}

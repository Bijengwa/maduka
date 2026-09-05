package com.maduka.rentmanager.util;

public final class LoginKeyUtil {
    private LoginKeyUtil() {}

    /**
     * Firebase RTDB keys forbid '.', '#', '$', '/', '[', ']'. Also used to
     * normalize phone numbers (strip spaces/dashes) and usernames/emails
     * (lowercase) so the same identifier always resolves to the same
     * login_index key regardless of how the user typed it.
     */
    public static String sanitize(String rawIdentifier) {
        if (rawIdentifier == null) return "";
        String trimmed = rawIdentifier.trim().toLowerCase(java.util.Locale.US);
        boolean looksLikePhone = trimmed.replaceAll("[^0-9]", "").length() >= 7
                && trimmed.replaceAll("[0-9 +()\\-]", "").isEmpty();
        if (looksLikePhone) {
            return trimmed.replaceAll("[^0-9]", "");
        }
        return trimmed.replaceAll("[.#$\\[\\]/@]", "_");
    }
}

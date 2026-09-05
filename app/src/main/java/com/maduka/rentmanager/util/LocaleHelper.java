package com.maduka.rentmanager.util;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context wrap(Context base, String languageTag) {
        Locale locale = new Locale(languageTag);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}

package com.jituileet.tvmouse;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

/** Handles system-following and user-selected Chinese/English UI language. */
public final class LanguageManager {
    private static final String PREF = "language";
    private static final String KEY = "mode";
    public static final String SYSTEM = "system";
    public static final String ZH = "zh";
    public static final String EN = "en";

    private LanguageManager() {}

    public static String getMode(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, SYSTEM);
    }

    public static void setMode(Context context, String mode) {
        if (!SYSTEM.equals(mode) && !ZH.equals(mode) && !EN.equals(mode)) mode = SYSTEM;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply();
    }

    public static Context wrap(Context base) {
        String mode = getMode(base);
        if (SYSTEM.equals(mode)) return base;
        Locale locale = EN.equals(mode) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            return base.createConfigurationContext(config);
        }
        // Android 4.4 and earlier.
        config.locale = locale;
        base.getResources().updateConfiguration(config, base.getResources().getDisplayMetrics());
        return base;
    }

    public static String displayName(Context context, String mode) {
        if (SYSTEM.equals(mode)) return context.getString(R.string.language_follow_system);
        if (ZH.equals(mode)) return context.getString(R.string.language_chinese);
        return context.getString(R.string.language_english);
    }
}

package com.hu.sightseek.helper;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.appcompat.app.AlertDialog;

import com.hu.sightseek.R;

import java.util.Locale;

public final class LocaleHelper {
    private static final String KEY_VERSION = "locale_version";

    private LocaleHelper() {}

    public static Context setLocale(Context ctx) {
        String lang = getSavedLanguage(ctx);

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Resources res = ctx.getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);

        return ctx.createConfigurationContext(config);
    }

    public static void showLanguageDialog(Activity activity) {
        String[] names = activity.getResources().getStringArray(R.array.language_names);
        String[] codes = activity.getResources().getStringArray(R.array.language_codes);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.profile_chooselanguage);

        builder.setItems(names, (dialog, which) -> {
            saveLanguage(activity, codes[which]);
            bumpLocaleVersion(activity);
            activity.recreate();
        });

        builder.show();
    }

    public static void saveLanguage(Context ctx, String lang) {
        ctx.getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putString("app_lang", lang)
                .apply();
    }

    public static String getSavedLanguage(Context ctx) {
        return ctx.getSharedPreferences("settings", MODE_PRIVATE)
                .getString("app_lang", "en");
    }

    private static void bumpLocaleVersion(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int v = prefs.getInt(KEY_VERSION, 0);
        prefs.edit().putInt(KEY_VERSION, v + 1).apply();
    }

    public static boolean localeVersionChanged(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int saved = prefs.getInt(KEY_VERSION, 0);
        int current = prefs.getInt("current_version_runtime", -1);

        if (current != saved) {
            prefs.edit().putInt("current_version_runtime", saved).apply();
            return true;
        }
        return false;
    }
}

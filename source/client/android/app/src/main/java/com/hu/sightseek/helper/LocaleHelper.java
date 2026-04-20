package com.hu.sightseek.helper;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.appcompat.app.AlertDialog;

import com.hu.sightseek.R;
import com.hu.sightseek.activity.ProfileActivity;

import java.util.Locale;

public final class LocaleHelper {
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
            LocaleHelper.saveLanguage(activity, codes[which]);
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
}

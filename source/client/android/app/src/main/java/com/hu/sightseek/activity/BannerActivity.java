package com.hu.sightseek.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.hu.sightseek.R;

import org.osmdroid.config.Configuration;

public class BannerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banner);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Register button
        Button registerButton = findViewById(R.id.banner_registerbtn);
        registerButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        // Login button
        Button loginButton = findViewById(R.id.banner_loginbtn);
        loginButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });

        // Offline button
        Button offlineButton = findViewById(R.id.banner_backbtn);
        offlineButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, RecordActivity.class); // TODO: Change this later to MainActivity
            startActivity(intent);
        });
    }
}
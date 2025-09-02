package com.hu.sightseek;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Patterns;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Login button
        Button loginButton = findViewById(R.id.login_loginbtn);
        loginButton.setOnClickListener(view -> {
            EditText emailEditText = findViewById(R.id.login_edittext_email);
            String email = emailEditText.getText().toString();

            EditText passwordEditText = findViewById(R.id.login_edittext_pw);
            String password = passwordEditText.getText().toString();

            Animation shakeAnim = AnimationUtils.loadAnimation(this, R.anim.invalid_input_shake);

            // Client-side verifications
            // Email verifications
            if(email.isBlank()) {
                emailEditText.startAnimation(shakeAnim);
                return;
            }
            else if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.startAnimation(shakeAnim);
                return;
            }

            // Password verifications
            if(password.length() < 8) {
                passwordEditText.startAnimation(shakeAnim);
                return;
            }

            // Intent intent = new Intent(this, MainActivity.class);
            // startActivity(intent);
        });

        // Register button
        Button registerButton = findViewById(R.id.login_registerbtn);
        registerButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}
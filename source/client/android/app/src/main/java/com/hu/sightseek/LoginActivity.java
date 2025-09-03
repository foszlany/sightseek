package com.hu.sightseek;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Patterns;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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

            TextView errorTextView = findViewById(R.id.login_error);
            Animation shakeAnim = AnimationUtils.loadAnimation(this, R.anim.invalid_input_shake);

            // Client-side verifications
            // Email verifications
            if(email.isBlank()) {
                errorTextView.setText(R.string.register_error_username_empty);
                errorTextView.setVisibility(VISIBLE);
                emailEditText.startAnimation(shakeAnim);
                return;
            }
            else if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorTextView.setText(R.string.register_error_email_invalidformat);
                errorTextView.setVisibility(VISIBLE);
                emailEditText.startAnimation(shakeAnim);
                return;
            }

            // Password verifications
            if(password.isBlank()) {
                errorTextView.setText(R.string.register_error_password_1empty);
                errorTextView.setVisibility(VISIBLE);
                passwordEditText.startAnimation(shakeAnim);
                return;
            }
            if(password.length() < 8) {
                errorTextView.setText(R.string.register_error_password_1short);
                errorTextView.setVisibility(VISIBLE);
                passwordEditText.startAnimation(shakeAnim);
                return;
            }

            errorTextView.setVisibility(INVISIBLE);

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
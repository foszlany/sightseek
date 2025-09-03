package com.hu.sightseek;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Patterns;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.config.Configuration;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Register button
        Button registerButton = findViewById(R.id.register_registerbtn);
        registerButton.setOnClickListener(view -> {

            EditText emailEditText = findViewById(R.id.register_edittext_email);
            String email = emailEditText.getText().toString();

            EditText usernameEditText = findViewById(R.id.register_edittext_username);
            String username = usernameEditText.getText().toString();

            EditText password1EditText = findViewById(R.id.register_edittext_pw1);
            String password1 = password1EditText.getText().toString();

            EditText password2EditText = findViewById(R.id.register_edittext_pw2);
            String password2 = password2EditText.getText().toString();

            Animation shakeAnim = AnimationUtils.loadAnimation(this, R.anim.invalid_input_shake);

            // Client-side verifications
            // Username verification
            if(username.isBlank()) {
                usernameEditText.startAnimation(shakeAnim);
                return;
            }
            else if(username.length() < 3) {
                usernameEditText.startAnimation(shakeAnim);
                return;
            }

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
            if(password1.length() < 8) {
                password1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(password2.length() < 8) {
                password2EditText.startAnimation(shakeAnim);
                return;
            }
            else if(!password1.equals(password2)) {
                password1EditText.startAnimation(shakeAnim);
                password2EditText.startAnimation(shakeAnim);
                return;
            }

            // Intent intent = new Intent(this, MainActivity.class);
            // startActivity(intent);
        });

        // Login button
        Button loginButton = findViewById(R.id.register_loginbtn);
        loginButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });
    }
}
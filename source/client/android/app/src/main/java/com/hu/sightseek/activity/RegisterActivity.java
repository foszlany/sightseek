package com.hu.sightseek.activity;

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

import com.hu.sightseek.R;

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

            TextView errorTextView = findViewById(R.id.register_error);
            Animation shakeAnim = AnimationUtils.loadAnimation(this, R.anim.invalid_input_shake);

            // Client-side verifications
            // Username verification
            if(username.isBlank()) {
                errorTextView.setText(R.string.register_error_username_empty);
                errorTextView.setVisibility(VISIBLE);
                usernameEditText.startAnimation(shakeAnim);
                return;
            }
            else if(username.length() < 3) {
                errorTextView.setText(R.string.register_error_username_short);
                errorTextView.setVisibility(VISIBLE);
                usernameEditText.startAnimation(shakeAnim);
                return;
            }

            // Email verifications
            if(email.isBlank()) {
                errorTextView.setText(R.string.register_error_email_empty);
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
            if(password1.isBlank()) {
                errorTextView.setText(R.string.register_error_password_1empty);
                errorTextView.setVisibility(VISIBLE);
                password1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(password1.length() < 8) {
                errorTextView.setText(R.string.register_error_password_1short);
                errorTextView.setVisibility(VISIBLE);
                password1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(password2.isBlank()) {
                errorTextView.setText(R.string.register_error_password_2empty);
                errorTextView.setVisibility(VISIBLE);
                password1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(password2.length() < 8) {
                errorTextView.setText(R.string.register_error_password_2short);
                errorTextView.setVisibility(VISIBLE);
                password2EditText.startAnimation(shakeAnim);
                return;
            }
            else if(!password1.equals(password2)) {
                errorTextView.setText(R.string.register_error_password_doesntmatch);
                errorTextView.setVisibility(VISIBLE);
                password1EditText.startAnimation(shakeAnim);
                password2EditText.startAnimation(shakeAnim);
                return;
            }

            errorTextView.setVisibility(INVISIBLE);

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
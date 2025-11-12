package com.hu.sightseek.activity;

import static android.view.View.VISIBLE;
import static com.hu.sightseek.utils.SightseekFirebaseUtils.updateCellsInFirebase;
import static com.hu.sightseek.utils.SightseekGenericUtils.STRAVA_CLIENT_ID;
import static com.hu.sightseek.utils.SightseekSpatialUtils.getVisitedCells;
import static com.hu.sightseek.utils.SightseekGenericUtils.hideKeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Check if user is logged in
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if(user == null) {
            runOnUiThread(() -> {
                startActivity(new Intent(this, BannerActivity.class));
                finish();
            });
            return;
        }

        // Add Menu
        Toolbar toolbar = findViewById(R.id.profile_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        // Change password
        Button passwordButton = findViewById(R.id.profile_changepasswordbtn);
        passwordButton.setOnClickListener(v -> {
            hideKeyboard(ProfileActivity.this);

            EditText currentPasswordEditText = findViewById(R.id.profile_oldpassword);
            String currentPassword = currentPasswordEditText.getText().toString();

            EditText newPassword1EditText = findViewById(R.id.profile_newpassword1);
            String newPassword1 = newPassword1EditText.getText().toString();

            EditText newPassword2EditText = findViewById(R.id.profile_newpassword2);
            String newPassword2 = newPassword2EditText.getText().toString();

            TextView errorTextView = findViewById(R.id.profile_passworderror);
            Animation shakeAnim = AnimationUtils.loadAnimation(this, R.anim.invalid_input_shake);
            errorTextView.setTextColor(getColor(R.color.red));
            errorTextView.setVisibility(VISIBLE);

            // Password verifications
            if(currentPassword.isBlank()) {
                errorTextView.setText(R.string.profile_error_password_oldempty);
                currentPasswordEditText.startAnimation(shakeAnim);
                return;
            }
            if(currentPassword.length() < 8) {
                errorTextView.setText(R.string.profile_error_password_oldempty);
                currentPasswordEditText.startAnimation(shakeAnim);
                return;
            }
            if(newPassword1.isBlank()) {
                errorTextView.setText(R.string.register_error_password_1empty);
                newPassword1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(newPassword1.length() < 8) {
                errorTextView.setText(R.string.register_error_password_1short);
                newPassword1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(newPassword2.isBlank()) {
                errorTextView.setText(R.string.register_error_password_2empty);
                newPassword1EditText.startAnimation(shakeAnim);
                return;
            }
            else if(newPassword2.length() < 8) {
                errorTextView.setText(R.string.register_error_password_2short);
                newPassword2EditText.startAnimation(shakeAnim);
                return;
            }
            else if(!newPassword1.equals(newPassword2)) {
                errorTextView.setText(R.string.register_error_password_doesntmatch);
                newPassword1EditText.startAnimation(shakeAnim);
                newPassword2EditText.startAnimation(shakeAnim);
                return;
            }

            AuthCredential credentials = EmailAuthProvider.getCredential(Objects.requireNonNull(user.getEmail()), currentPassword);

            user.reauthenticate(credentials)
                    .addOnCompleteListener(reauthTask -> {
                        if(reauthTask.isSuccessful()) {
                            user.updatePassword(newPassword1)
                                    .addOnCompleteListener(passwordChangeTask -> {
                                        if(passwordChangeTask.isSuccessful()) {
                                            errorTextView.setTextColor(getColor(R.color.green));
                                            errorTextView.setText(R.string.profile_passwordchangesuccess);
                                        }
                                        else {
                                            errorTextView.setText(R.string.register_error_unknown);
                                        }
                                    });
                        }
                        else {
                            errorTextView.setText(R.string.profile_error_password_incorrectcurrent);
                            currentPasswordEditText.startAnimation(shakeAnim);
                        }
                    });
        });

        // Strava
        Button stravaButton = findViewById(R.id.profile_strava);
        stravaButton.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize")
                    .buildUpon()
                    .appendQueryParameter("client_id", STRAVA_CLIENT_ID)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", "sightseek://strava-auth")
                    .appendQueryParameter("approval_prompt", "auto")
                    .appendQueryParameter("scope", "read,activity:read_all")
                    .build();

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        // Unlink Strava
        Button unlinkButton = findViewById(R.id.profile_unlinkbtn);
        unlinkButton.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to unlink your account? This will delete all activities that were imported!")
                    .setPositiveButton("Yes", (d, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                            if(prefs.contains("StravaLatestImportDate")) {
                                prefs.edit().remove("StravaLatestImportDate").apply();
                            }

                            String uid = Objects.requireNonNull(mAuth.getUid());
                            DocumentReference userDocument = FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(uid);

                            userDocument.get().addOnSuccessListener(documentSnapshot -> {
                                if(documentSnapshot.contains("stravaId")) {
                                    Long stravaIdHolder = documentSnapshot.getLong("stravaId");
                                    long stravaId = stravaIdHolder == null ? -1 : stravaIdHolder;

                                    if(stravaId == -1) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "You don't have anything linked.", Toast.LENGTH_LONG).show();
                                        });
                                    }
                                    else {
                                        LocalDatabaseDAO dao2 = new LocalDatabaseDAO(this);
                                        ArrayList<GeoPoint> points = dao2.getAllImportedPoints();
                                        dao2.deleteImportedActivities();
                                        dao2.close();

                                        HashMap<String, Integer> cells = getVisitedCells(points);
                                        updateCellsInFirebase(mAuth, cells, true);

                                        userDocument.update("stravaId", -1);

                                        DocumentReference stravaIdDoc = FirebaseFirestore.getInstance()
                                                .collection("strava_ids")
                                                .document(String.valueOf(stravaId));

                                        stravaIdDoc.delete();

                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "Successfully unlinked.", Toast.LENGTH_LONG).show();
                                        });
                                    }
                                }
                                else {
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "You don't have anything linked"., Toast.LENGTH_LONG).show();
                                    });
                                }
                            });
                        });
                    })
                    .setNegativeButton("No", (d, which) -> d.dismiss())
                    .setCancelable(true)
                    .create();

            dialog.show();
        });

        // Logout
        Button logoutButton = findViewById(R.id.profile_logout);
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            finish();
        });
    }

    // Create top menubar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_top, menu);
        return true;
    }

    // Top menubar actions
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Statistics
        if(id == R.id.topmenu_statistics) {
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
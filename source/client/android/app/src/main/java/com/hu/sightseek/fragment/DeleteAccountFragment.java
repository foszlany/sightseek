package com.hu.sightseek.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hu.sightseek.R;
import com.hu.sightseek.activity.MainActivity;

import java.util.Objects;

public class DeleteAccountFragment extends DialogFragment {
    private FirebaseAuth auth;
    private FirebaseUser user;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = inflater.getContext();
        View view = inflater.inflate(R.layout.fragment_delete_account, container, false);

        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        if(user == null) {
            dismiss();
            return view;
        }

        // Delete button
        Button deleteButton = view.findViewById(R.id.deleteaccountpopup_selectbtn);
        deleteButton.setOnClickListener(v -> {
            EditText passwordEditText = view.findViewById(R.id.deleteaccountpopup_password);
            String password = passwordEditText.getText().toString();

            if(password.isBlank() || password.length() < 8) {
                Toast.makeText(ctx, "Incorrect password.", Toast.LENGTH_LONG).show();
                return;
            }

            AuthCredential credentials = EmailAuthProvider.getCredential(Objects.requireNonNull(user.getEmail()), password);

            // Reauth
            user.reauthenticate(credentials).addOnCompleteListener(reauthTask -> {
                if(reauthTask.isSuccessful()) {
                    // Delete
                    user.delete().addOnCompleteListener(deleteTask -> {
                        if(deleteTask.isSuccessful()) {
                            Intent intent = new Intent(ctx, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);

                            deleteAccountRemains();

                            Toast.makeText(ctx, "Your account has been deleted.", Toast.LENGTH_LONG).show();
                            dismiss();
                        }
                        else {
                            Toast.makeText(ctx, "An error occurred while trying to delete your account.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                else {
                    Toast.makeText(ctx, "Incorrect password.", Toast.LENGTH_LONG).show();
                }
            });
        });

        return view;
    }

    private void deleteAccountRemains() {
        // TODO
    }
}

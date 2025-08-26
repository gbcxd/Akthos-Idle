package com.example.akthosidle.ui.auth; // Adjust package name as needed

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.akthosidle.MainActivity; // Assuming this is your main game activity
import com.example.akthosidle.R;
import com.example.akthosidle.ui.auth.LoginFragment; // For the link to login
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateAccountFragment extends Fragment {

    private TextInputLayout tilCharacterName;
    private EditText etCharacterName;
    private TextInputLayout tilEmail;
    private EditText etEmail;
    private TextInputLayout tilPassword;
    private EditText etPassword;
    private Button btnCreateAccount;
    private ProgressBar progressBar;
    private TextView tvLoginLink;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public CreateAccountFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_create_account, container, false);

        tilCharacterName = view.findViewById(R.id.tilCharacterName);
        etCharacterName = view.findViewById(R.id.etCharacterName);
        tilEmail = view.findViewById(R.id.tilEmail);
        etEmail = view.findViewById(R.id.etEmail);
        tilPassword = view.findViewById(R.id.tilPassword);
        etPassword = view.findViewById(R.id.etPassword);
        btnCreateAccount = view.findViewById(R.id.btnCreateAccount);
        progressBar = view.findViewById(R.id.progressBar);
        tvLoginLink = view.findViewById(R.id.tvLoginLink);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String characterName = etCharacterName.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (validateInput(characterName, email, password)) {
                    createAccount(characterName, email, password);
                }
            }
        });

        tvLoginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to LoginActivity
                Intent intent = new Intent(getActivity(), LoginFragment.class);
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish(); // Optional: Finish CreateAccountActivity
                }
            }
        });
    }

    private boolean validateInput(String characterName, String email, String password) {
        boolean isValid = true;
        if (characterName.isEmpty()) {
            tilCharacterName.setError("Character name is required");
            isValid = false;
        } else {
            tilCharacterName.setError(null);
        }

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) { // Firebase default minimum is 6 characters
            tilPassword.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            tilPassword.setError(null);
        }
        return isValid;
    }

    private void createAccount(final String characterName, final String email, String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnCreateAccount.setEnabled(false);
        tvLoginLink.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d("CreateAccountFragment", "createUserWithEmail:success");
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                saveCharacterNameToFirestore(firebaseUser.getUid(), characterName, email);
                            } else {
                                // This case should ideally not happen if task.isSuccessful is true
                                progressBar.setVisibility(View.GONE);
                                btnCreateAccount.setEnabled(true);
                                tvLoginLink.setEnabled(true);
                                Toast.makeText(getContext(), "Authentication succeeded but user is null.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            btnCreateAccount.setEnabled(true);
                            tvLoginLink.setEnabled(true);
                            Log.w("CreateAccountFragment", "createUserWithEmail:failure", task.getException());
                            Toast.makeText(getContext(), "Authentication failed: " +
                                            (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveCharacterNameToFirestore(String userId, String characterName, String email) {
        Map<String, Object> characterData = new HashMap<>();
        characterData.put("userId", userId);
        characterData.put("characterName", characterName);
        characterData.put("email", email); // Storing email can be useful for lookups
        characterData.put("createdAt", Timestamp.now()); // Optional: timestamp

        db.collection("playerCharacters").document(userId) // Using UID as document ID
                .set(characterData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("CreateAccountFragment", "Character data saved to Firestore for UID: " + userId);
                        progressBar.setVisibility(View.GONE);
                        btnCreateAccount.setEnabled(true);
                        tvLoginLink.setEnabled(true);
                        Toast.makeText(getContext(), "Account created.", Toast.LENGTH_SHORT).show();

                        // Navigate to the main part of your app
                        Intent intent = new Intent(getActivity(), MainActivity.class);
                        // Clear back stack and start new task for MainActivity
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        if (getActivity() != null) {
                            getActivity().finish(); // Finish CreateAccountActivity
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("CreateAccountFragment", "Error saving character data to Firestore", e);
                        progressBar.setVisibility(View.GONE);
                        btnCreateAccount.setEnabled(true);
                        tvLoginLink.setEnabled(true);
                        // Auth succeeded, but Firestore save failed.
                        // Inform user. You might also consider deleting the auth user if character data is critical.
                        Toast.makeText(getContext(), "Account created, but failed to save character data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}


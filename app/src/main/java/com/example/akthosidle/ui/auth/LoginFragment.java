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

import com.example.akthosidle.MainActivity;
import com.example.akthosidle.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private TextInputLayout tilEmailLogin;
    private EditText etEmailLogin;
    private TextInputLayout tilPasswordLogin;
    private EditText etPasswordLogin;
    private Button btnLogin;
    private ProgressBar progressBarLogin;
    private TextView tvCreateAccountLink;

    private FirebaseAuth mAuth;

    public LoginFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        tilEmailLogin = view.findViewById(R.id.tilEmailLogin);
        etEmailLogin = view.findViewById(R.id.etEmailLogin);
        tilPasswordLogin = view.findViewById(R.id.tilPasswordLogin);
        etPasswordLogin = view.findViewById(R.id.etPasswordLogin);
        btnLogin = view.findViewById(R.id.btnLogin);
        progressBarLogin = view.findViewById(R.id.progressBarLogin);
        tvCreateAccountLink = view.findViewById(R.id.tvCreateAccountLink);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmailLogin.getText().toString().trim();
                String password = etPasswordLogin.getText().toString().trim();

                if (validateInput(email, password)) {
                    loginUser(email, password);
                }
            }
        });

        tvCreateAccountLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to your CreateAccountActivity
                Intent intent = new Intent(getActivity(), CreateAccountFragment.class);
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish(); // Optional: finish LoginActivity
                }
            }
        });
    }

    private boolean validateInput(String email, String password) {
        boolean isValid = true;
        if (email.isEmpty()) {
            tilEmailLogin.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmailLogin.setError("Enter a valid email address");
            isValid = false;
        } else {
            tilEmailLogin.setError(null);
        }

        if (password.isEmpty()) {
            tilPasswordLogin.setError("Password is required");
            isValid = false;
        } else {
            tilPasswordLogin.setError(null);
        }
        return isValid;
    }

    private void loginUser(String email, String password) {
        progressBarLogin.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        tvCreateAccountLink.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBarLogin.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        tvCreateAccountLink.setEnabled(true);

                        if (task.isSuccessful()) {
                            Log.d("LoginFragment", "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser(); // Good practice to check user object
                            Toast.makeText(getContext(), "Login successful.", Toast.LENGTH_SHORT).show();

                            // Navigate to the main part of your app
                            Intent intent = new Intent(getActivity(), MainActivity.class);
                            // Clear back stack and start new task for MainActivity
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            if (getActivity() != null) {
                                getActivity().finish(); // Finish LoginActivity
                            }
                        } else {
                            Log.w("LoginFragment", "signInWithEmail:failure", task.getException());
                            Toast.makeText(getContext(), "Authentication failed: " +
                                            (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}

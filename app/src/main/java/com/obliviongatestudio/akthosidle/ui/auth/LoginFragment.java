package com.obliviongatestudio.akthosidle.ui.auth;

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
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.obliviongatestudio.akthosidle.MainActivity;
import com.obliviongatestudio.akthosidle.R;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class LoginFragment extends Fragment {

    private TextInputLayout tilEmailLogin, tilPasswordLogin;
    private EditText etEmailLogin, etPasswordLogin;
    private Button btnLogin;
    private ProgressBar progressBarLogin;
    private TextView tvCreateAccountLink;

    private FirebaseAuth mAuth;

    public LoginFragment() { /* required empty */ }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        tilEmailLogin     = view.findViewById(R.id.tilEmailLogin);
        etEmailLogin      = view.findViewById(R.id.etEmailLogin);
        tilPasswordLogin  = view.findViewById(R.id.tilPasswordLogin);
        etPasswordLogin   = view.findViewById(R.id.etPasswordLogin);
        btnLogin          = view.findViewById(R.id.btnLogin);
        progressBarLogin  = view.findViewById(R.id.progressBarLogin);
        tvCreateAccountLink = view.findViewById(R.id.tvCreateAccountLink);

        if (progressBarLogin != null) progressBarLogin.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnLogin.setOnClickListener(v -> {
            String email = safeText(etEmailLogin).toLowerCase();
            String password = safeText(etPasswordLogin);

            if (validateInput(email, password)) {
                loginUser(email, password);
            }
        });

        tvCreateAccountLink.setOnClickListener(v -> {
            // Navigate to CreateAccountFragment via NavController
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_login_to_create);
        });
    }

    private boolean validateInput(String email, String password) {
        boolean ok = true;

        if (email.isEmpty()) {
            tilEmailLogin.setError("Email is required");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmailLogin.setError("Enter a valid email address");
            ok = false;
        } else {
            tilEmailLogin.setError(null);
        }

        if (password.isEmpty()) {
            tilPasswordLogin.setError("Password is required");
            ok = false;
        } else {
            tilPasswordLogin.setError(null);
        }

        return ok;
    }

    private void loginUser(String email, String password) {
        setUiBusy(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), (Task<AuthResult> task) -> {
                    if (!isAdded()) return; // Fragment detached; avoid crashes
                    setUiBusy(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(requireContext(), "Login successful.", Toast.LENGTH_SHORT).show();

                        // Go to the main app and clear auth from back stack
                        Intent intent = new Intent(requireContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    } else {
                        Exception e = task.getException();
                        Log.w("LoginFragment", "signInWithEmail:failure", e);
                        Toast.makeText(requireContext(),
                                "Authentication failed: " + (e != null ? e.getMessage() : "Unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- helpers ---
    private void setUiBusy(boolean busy) {
        if (!isAdded()) return;
        progressBarLogin.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!busy);
        tvCreateAccountLink.setEnabled(!busy);
    }

    private static String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}

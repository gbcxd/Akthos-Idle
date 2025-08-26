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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import com.obliviongatestudio.akthosidle.MainActivity;
import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.repo.GameRepository;

public class LoginFragment extends Fragment {

    private TextInputLayout tilEmailLogin, tilPasswordLogin;
    private EditText etEmailLogin, etPasswordLogin;
    private Button btnLogin;
    private View btnGoogle; // can be SignInButton or a normal Button
    private ProgressBar progressBarLogin;
    private TextView tvCreateAccountLink;

    private FirebaseAuth mAuth;

    // Google sign-in
    private GoogleSignInClient googleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    public LoginFragment() { /* required empty */ }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        // --- Google sign-in config (uses default_web_client_id from google-services.json) ---
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(requireContext(), gso);

        // Handle result of the Google Sign-In Intent
        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        } else {
                            setUiBusy(false);
                            toast("Google sign-in failed.");
                        }
                    } catch (ApiException e) {
                        setUiBusy(false);
                        toast("Google sign-in error: " + e.getStatusCode());
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        tilEmailLogin      = view.findViewById(R.id.tilEmailLogin);
        etEmailLogin       = view.findViewById(R.id.etEmailLogin);
        tilPasswordLogin   = view.findViewById(R.id.tilPasswordLogin);
        etPasswordLogin    = view.findViewById(R.id.etPasswordLogin);
        btnLogin           = view.findViewById(R.id.btnLogin);
        btnGoogle          = view.findViewById(R.id.btnGoogle);      // <- add this in XML
        progressBarLogin   = view.findViewById(R.id.progressBarLogin);
        tvCreateAccountLink= view.findViewById(R.id.tvCreateAccountLink);

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

        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                setUiBusy(true);
                googleLauncher.launch(googleClient.getSignInIntent());
            });
        }

        tvCreateAccountLink.setOnClickListener(v -> {
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_login_to_create);
        });
    }

    // ---------------- Email / Password ----------------
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
                    if (!isAdded()) return;
                    setUiBusy(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(requireContext(), "Login successful.", Toast.LENGTH_SHORT).show();
                        goToMainAndSync();
                    } else {
                        Exception e = task.getException();
                        Log.w("LoginFragment", "signInWithEmail:failure", e);
                        Toast.makeText(requireContext(),
                                "Authentication failed: " + (e != null ? e.getMessage() : "Unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------- Google → Firebase ----------------
    private void firebaseAuthWithGoogle(String idToken) {
        setUiBusy(true);
        AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(cred)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (!isAdded()) return;
                    setUiBusy(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(requireContext(), "Signed in with Google.", Toast.LENGTH_SHORT).show();
                        goToMainAndSync();
                    } else {
                        Log.w("LoginFragment", "Google sign-in credential failed", task.getException());
                        Toast.makeText(requireContext(),
                                "Google sign-in failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // After any successful auth, pull cloud save (if newer) then go to Main
    private void goToMainAndSync() {
        try {
            GameRepository repo = new GameRepository(requireContext().getApplicationContext());
            repo.loadDefinitions();
            // Pull remote if it's newer than local, then start Main
            repo.loadFromCloudIfNewer(updated -> {
                repo.startCloudSync();
                startActivity(new Intent(requireContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                requireActivity().finish();
            });
        } catch (Throwable t) {
            // Fallback: just go to main
            startActivity(new Intent(requireContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            requireActivity().finish();
        }
    }

    // ---------------- helpers ----------------
    private void setUiBusy(boolean busy) {
        if (!isAdded()) return;
        if (progressBarLogin != null) progressBarLogin.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!busy);
        if (tvCreateAccountLink != null) tvCreateAccountLink.setEnabled(!busy);
        if (btnGoogle != null) btnGoogle.setEnabled(!busy);
    }

    private static String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String m) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}

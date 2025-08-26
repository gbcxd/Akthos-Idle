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

import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.obliviongatestudio.akthosidle.MainActivity;
import com.obliviongatestudio.akthosidle.R;

import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CreateAccountFragment extends Fragment {

    private TextInputLayout tilCharacterName, tilEmail, tilPassword;
    private EditText etCharacterName, etEmail, etPassword;
    private Button btnCreateAccount;
    private ProgressBar progressBar;
    private TextView tvLoginLink;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public CreateAccountFragment() { /* required */ }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_account, container, false);

        tilCharacterName = view.findViewById(R.id.tilCharacterName);
        etCharacterName  = view.findViewById(R.id.etCharacterName);
        tilEmail         = view.findViewById(R.id.tilEmail);
        etEmail          = view.findViewById(R.id.etEmail);
        tilPassword      = view.findViewById(R.id.tilPassword);
        etPassword       = view.findViewById(R.id.etPassword);
        btnCreateAccount = view.findViewById(R.id.btnCreateAccount);
        progressBar      = view.findViewById(R.id.progressBar);
        tvLoginLink      = view.findViewById(R.id.tvLoginLink);

        // Start with the spinner hidden
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnCreateAccount.setOnClickListener(v -> {
            String characterName = safeText(etCharacterName);
            String email = safeText(etEmail).toLowerCase();
            String password = safeText(etPassword);

            if (validateInput(characterName, email, password)) {
                createAccount(characterName, email, password);
            }
        });

        tvLoginLink.setOnClickListener(v -> {
            // Navigate to LoginFragment via NavController (no Intents for fragments)
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigate(R.id.action_create_to_login);
        });
    }

    private boolean validateInput(String characterName, String email, String password) {
        boolean ok = true;

        if (characterName.isEmpty()) {
            tilCharacterName.setError("Character name is required");
            ok = false;
        } else if (characterName.length() < 3) {
            tilCharacterName.setError("Character name must be at least 3 characters");
            ok = false;
        } else {
            tilCharacterName.setError(null);
        }

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            ok = false;
        } else {
            tilEmail.setError(null);
        }

        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            ok = false;
        } else if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        return ok;
    }

    private void createAccount(final String characterName, final String email, String password) {
        setUiBusy(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), (Task<AuthResult> task) -> {
                    if (!isAdded()) return; // fragment detached; bail
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveCharacterToFirestore(firebaseUser.getUid(), characterName, email);
                        } else {
                            Log.w("CreateAccount", "Auth success but user null");
                            setUiBusy(false);
                            toast("Authentication succeeded but user is null.");
                        }
                    } else {
                        setUiBusy(false);
                        Exception e = task.getException();
                        Log.w("CreateAccount", "createUserWithEmail:failure", e);
                        toast("Authentication failed: " + (e != null ? e.getMessage() : "Unknown error"));
                    }
                });
    }

    private void saveCharacterToFirestore(String uid, String characterName, String email) {
        Map<String, Object> data = defaultCharacter(uid, characterName, email);

        FirebaseFirestore.getInstance()
                .collection("playerCharacters").document(uid)
                .set(data, SetOptions.merge())   // merge = idempotent on retries
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Account created.", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(requireContext(), MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    requireActivity().finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Saved auth but failed character: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private Map<String, Object> defaultCharacter(String uid, String name, String email) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", uid);
        m.put("characterName", name);
        m.put("email", email);
        m.put("createdAt", FieldValue.serverTimestamp());

        // currencies
        Map<String, Object> currencies = new HashMap<>();
        currencies.put("silver", 0L);
        currencies.put("gold",   0L);
        currencies.put("slayer", 0L);
        m.put("currencies", currencies);

        // core stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("attack", 1);
        stats.put("strength", 1);
        stats.put("defense", 1);
        stats.put("archery", 1);
        stats.put("health", 10);
        stats.put("critRate", 5);     // %
        stats.put("critDamage", 150); // %
        m.put("stats", stats);

        // skills (example set — add the ones you use)
        Map<String, Object> skills = new HashMap<>();
        skills.put("ATTACK", skill(1, 0));
        skills.put("STRENGTH", skill(1, 0));
        skills.put("DEFENSE", skill(1, 0));
        skills.put("ARCHERY", skill(1, 0));
        skills.put("FISHING", skill(1, 0));
        skills.put("MINING",  skill(1, 0));
        skills.put("COOKING", skill(1, 0));
        // ... add the rest you track
        m.put("skills", skills);

        // equipment slots
        Map<String, Object> equipment = new HashMap<>();
        equipment.put("helmet", null);
        equipment.put("cape", null);
        equipment.put("mainWeapon", null);
        equipment.put("secondHand", null);
        equipment.put("armor", null);
        equipment.put("gloves", null);
        equipment.put("pants", null);
        equipment.put("boots", null);
        equipment.put("necklace", null);
        equipment.put("ring", null);
        equipment.put("tool", null);
        equipment.put("blessing", null);
        m.put("equipment", equipment);

        // start with empty inventory list
        m.put("inventory", new ArrayList<Map<String, Object>>());

        return m;
    }
    private Map<String, Object> skill(int level, long xp) {
        Map<String, Object> s = new HashMap<>();
        s.put("level", level);
        s.put("xp", xp);
        return s;
    }

    // --- helpers ---

    private void setUiBusy(boolean busy) {
        if (!isAdded()) return;
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnCreateAccount.setEnabled(!busy);
        tvLoginLink.setEnabled(!busy);
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    private static String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}

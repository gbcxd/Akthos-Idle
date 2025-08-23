package com.example.akthosidle.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.akthosidle.R;

import org.json.JSONObject;

/** Real settings screen: backup/export/import/reset + placeholders. */
public class SettingsFragment extends PreferenceFragmentCompat {

    // Mirror repo’s keys to manipulate save data directly
    private static final String SP_NAME    = "akthos_idle_save";
    private static final String KEY_PLAYER = "player_json";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.prefs_settings, rootKey);

        // Manual Save (no-op if your repo autosaves; can be wired to repo.save() via activity)
        findPreference("manual_save").setOnPreferenceClickListener(p -> {
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference("export_save").setOnPreferenceClickListener(p -> { exportSave(); return true; });
        findPreference("import_save").setOnPreferenceClickListener(p -> { importSave(); return true; });
        findPreference("reset_progress").setOnPreferenceClickListener(p -> { resetProgress(); return true; });
    }

    private void exportSave() {
        SharedPreferences sp = requireContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_PLAYER, "{}");
        String pretty = json;
        try { pretty = new JSONObject(json).toString(2); } catch (Throwable ignored) {}

        new AlertDialog.Builder(requireContext())
                .setTitle("Export Save")
                .setMessage(pretty)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Akthos Idle Save", json));
                        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void importSave() {
        final EditText input = new EditText(requireContext());
        input.setHint("{ paste save JSON here }");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setPadding(32, 24, 32, 24);

        new AlertDialog.Builder(requireContext())
                .setTitle("Import Save")
                .setView(input)
                .setPositiveButton("Import", (d, w) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (raw.isEmpty()) { Toast.makeText(requireContext(), "No data", Toast.LENGTH_SHORT).show(); return; }
                    try { new JSONObject(raw); } catch (Throwable t) {
                        Toast.makeText(requireContext(), "Invalid JSON", Toast.LENGTH_SHORT).show(); return;
                    }
                    SharedPreferences sp = requireContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
                    sp.edit().putString(KEY_PLAYER, raw).apply();
                    Toast.makeText(requireContext(), "Imported. Reload app to apply.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetProgress() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Reset Progress")
                .setMessage("Erase current character and inventory?")
                .setPositiveButton("Erase", (d, w) -> {
                    SharedPreferences sp = requireContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
                    sp.edit().remove(KEY_PLAYER).apply();
                    Toast.makeText(requireContext(), "Progress reset. Reload app to apply.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

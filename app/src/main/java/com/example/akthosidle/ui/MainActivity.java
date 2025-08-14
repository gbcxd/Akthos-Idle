package com.example.akthosidle.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.akthosidle.R;
import com.example.akthosidle.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding b;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.bottomNav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if (id == R.id.nav_character) f = new CharacterFragment();
            else if (id == R.id.nav_inventory) f = new InventoryFragment();
            else f = new BattleFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, f).commit();
            return true;
        });
        b.bottomNav.setSelectedItemId(R.id.nav_character);
    }
}

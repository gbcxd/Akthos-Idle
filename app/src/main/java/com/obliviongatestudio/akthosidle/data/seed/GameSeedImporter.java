package com.obliviongatestudio.akthosidle.data.seed;

import android.content.Context;
import android.util.Log;

import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.domain.model.Monster;

import java.util.List;

public final class GameSeedImporter {
    private static final String TAG = "GameSeedImporter";
    private static final String DIR = "game"; // assets/game

    private GameSeedImporter() {}

    /** Load latest monsters/items/etc. from assets and seed into repository. */
    public static void importAll(Context ctx, GameRepository repo) {
        try {
            // --- Monsters ---
            String monstersFile = AssetGameLoader.findLatestVersionFile(ctx, DIR, "monsters");
            if (monstersFile != null) {
                String monstersJson = AssetGameLoader.readAsset(ctx, DIR + "/" + monstersFile);
                List<GameParsers.MonsterDto> dtos = GameParsers.parseMonstersDto(monstersJson);
                List<Monster> monsters = GameParsers.toDomainMonsters(dtos);
                if (!monsters.isEmpty()) {
                    repo.seedMonsters(monsters);      // <-- push into your repo
                    Log.i(TAG, "Seeded monsters: " + monsters.size());
                }
            } else {
                Log.w(TAG, "No monsters.vN.json found under assets/game");
            }

            // Items and actions
            repo.loadItemsFromAssets();
            repo.loadActionsFromAssets();

        } catch (Exception e) {
            Log.e(TAG, "Seeding failed", e);
        }
    }
}

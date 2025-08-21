package com.example.akthosidle.data.seed;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Patterns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssetGameLoader {
    private AssetGameLoader() {}

    private static final Pattern VERSIONED =
            Pattern.compile("^(?<base>[a-z_]+)\\.v(?<ver>\\d+)\\.json$"); // e.g., items.v1.json

    /** Returns the filename (not path) of the highest-version match for the given base, or null. */
    public static String findLatestVersionFile(Context ctx, String dir, String baseName) throws IOException {
        AssetManager am = ctx.getAssets();
        String[] files = am.list(dir);
        int bestVer = -1;
        String bestFile = null;
        if (files == null) return null;
        for (String f : files) {
            Matcher m = VERSIONED.matcher(f);
            if (!m.matches()) continue;
            if (!m.group("base").equals(baseName)) continue;
            int v = Integer.parseInt(m.group("ver"));
            if (v > bestVer) { bestVer = v; bestFile = f; }
        }
        return bestFile; // e.g., items.v1.json
    }

    /** Read an asset as UTF-8 string. */
    public static String readAsset(Context ctx, String path) throws IOException {
        try (InputStream is = ctx.getAssets().open(path);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}

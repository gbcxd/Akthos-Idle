package com.obliviongatestudio.akthosidle.data.loaders;

import android.content.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonLoader {
    private final Context ctx;
    public JsonLoader(Context ctx) { this.ctx = ctx; }
    public String fromAssets(String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path)) {
            byte[] buf = new byte[is.available()];
            //noinspection ResultOfMethodCallIgnored
            is.read(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }
}

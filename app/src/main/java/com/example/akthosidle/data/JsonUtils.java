package com.example.akthosidle.data;

import android.content.Context;
import java.io.*;
public class JsonUtils {
    public static String readAsset(Context ctx, String name) throws IOException {
        try (InputStream is = ctx.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}

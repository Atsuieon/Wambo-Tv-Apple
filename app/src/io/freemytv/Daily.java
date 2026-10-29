package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The picture of the day (Bing's, in Full HD, no key): behind the home screen and in the screensaver whenever none of
 * your own photos is there (Prefs.dailyPicture). Fetched once a day into the app's folder, with its caption (what and
 * where it is, without the photographer's credit); the previous day's file is deleted.
 */
final class Daily {
    private Daily() {}

    private static final String META = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=pl-PL";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean fetching;
    private static volatile long failedAt;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("daily", Context.MODE_PRIVATE);
    }

    /** Today's picture (or the last one fetched), or null. */
    static File file(Context c) {
        String name = sp(c).getString("file", "");
        if (name.isEmpty()) return null;
        File f = new File(c.getFilesDir(), name);
        return f.isFile() ? f : null;
    }

    static String caption(Context c) {
        return sp(c).getString("caption", "");
    }

    /** On in Home Settings and a picture on disk. */
    static boolean on(Context c) {
        return Prefs.dailyPicture() && file(c) != null;
    }

    /** Fetches today's picture if it isn't here yet (at most every half hour after a failure); `ready` runs after. */
    static void refresh(Context c, Runnable ready) {
        if (!Prefs.dailyPicture() || fetching) return;
        String today = HomeData.today();
        if (today.equals(sp(c).getString("day", "")) && file(c) != null) return;
        long now = System.currentTimeMillis();
        if (failedAt != 0 && now >= failedAt && now - failedAt < 30 * 60_000L) return;
        fetching = true;
        Context app = c.getApplicationContext();
        worker.execute(() -> {
            try {
                JSONObject image = new JSONObject(new String(get(META), StandardCharsets.UTF_8))
                        .getJSONArray("images").getJSONObject(0);
                String base = image.getString("urlbase");
                String name = "daily-" + image.optString("startdate", today.replace("-", "")) + ".jpg";
                File dir = app.getFilesDir(), out = new File(dir, name), part = new File(dir, "." + name);
                if (!out.isFile()) {
                    byte[] jpeg = get("https://www.bing.com" + base + "_1920x1080.jpg");
                    try (OutputStream o = new FileOutputStream(part)) {
                        o.write(jpeg);
                    }
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(part.getPath(), bounds);
                    if (bounds.outWidth < 1280 || !part.renameTo(out)) {
                        part.delete();
                        throw new IOException("not a picture (" + bounds.outWidth + " px)");
                    }
                }
                // "Lake Bled, Slovenia (© Photographer/Agency)": the place, without the credit.
                String caption = image.optString("copyright", image.optString("title", "")).replaceAll("\\s*\\(©[^)]*\\)\\s*$", "").trim();
                String previous = sp(app).getString("file", "");
                sp(app).edit().putString("file", name).putString("caption", caption).putString("day", today).apply();
                if (!previous.isEmpty() && !previous.equals(name)) new File(dir, previous).delete();
                failedAt = 0;
                if (ready != null) main.post(ready);
            } catch (Exception e) {
                failedAt = System.currentTimeMillis();
                AppLog.w("daily picture: " + e.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "FreeMyTV");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (out.size() > 12 << 20) throw new IOException("too big");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}

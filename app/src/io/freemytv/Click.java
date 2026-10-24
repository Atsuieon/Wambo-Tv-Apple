package io.freemytv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Soft sound for the home screen (moving focus and opening). The app synthesizes it itself (a soft water drop, the
 * owner's pick; a deeper one for opening), writes it once as a WAV in the cache and plays it quietly through
 * SoundPool. It only plays on the home screen, never inside apps.
 */
final class Click {
    private static final int RATE = 48_000;
    private SoundPool pool;
    private int move, open;

    Click(Context c) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            move = pool.load(wav(c, "drop-move.wav", 500f, 1600f, 0.020f, 0.015f, 0.070f), 1);
            open = pool.load(wav(c, "drop-open.wav", 380f, 1200f, 0.025f, 0.022f, 0.100f), 1);
        } catch (IOException | RuntimeException e) {
            AppLog.w("click sound unavailable: " + e.getMessage());
            release();
        }
    }

    void move() {
        if (pool != null && Prefs.sounds()) pool.play(move, 0.18f, 0.18f, 1, 0, 1f);
    }

    void open() {
        if (pool != null && Prefs.sounds()) pool.play(open, 0.22f, 0.22f, 1, 0, 1f);
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }

    /**
     * A water drop: a sine whose pitch rises from `from` to `to` Hz (time constant `rise`), 1 ms attack (no pop) and
     * exponential decay.
     */
    private static String wav(Context c, String name, float from, float to, float rise, float decay, float seconds)
            throws IOException {
        File f = new File(c.getCacheDir(), name);
        if (f.exists() && f.length() > 44) return f.getPath();
        int n = Math.round(RATE * seconds);
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes("US-ASCII")).putInt(36 + n * 2).put("WAVE".getBytes("US-ASCII"));
        b.put("fmt ".getBytes("US-ASCII")).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes("US-ASCII")).putInt(n * 2);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double freq = from + (to - from) * (1 - Math.exp(-t / rise));
            phase += 2 * Math.PI * freq / RATE;
            double env = Math.min(1, t / 0.001) * Math.exp(-t / decay);
            b.putShort((short) Math.round(Math.sin(phase) * env * 0.7 * Short.MAX_VALUE));
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b.array());
        }
        return f.getPath();
    }
}

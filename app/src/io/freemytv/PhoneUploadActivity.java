package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/**
 * "Photos from your phone" (a top button on the home screen): a QR code with the address of PhoneUpload's page. The
 * server runs only while this screen is on, and the screen closes itself after 15 quiet minutes. Back returns to the
 * home screen, which imports what arrived.
 */
public class PhoneUploadActivity extends Activity implements PhoneUpload.Listener {
    private static final long QUIET_MS = 15 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PhoneUpload upload;
    private UploadView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new UploadView(this);
        setContentView(view);
    }

    @Override protected void onStart() {
        super.onStart();
        upload = new PhoneUpload(this, this);
        String url = upload.start();
        view.show(url);
        handler.postDelayed(quiet, 60_000);
    }

    @Override protected void onStop() {
        super.onStop();
        handler.removeCallbacks(quiet);
        if (upload != null) upload.stop();
        upload = null;
        finish();   // a new token each time it opens
    }

    private final Runnable quiet = new Runnable() {
        @Override public void run() {
            if (upload == null) return;
            if (System.currentTimeMillis() - upload.lastActivity() > QUIET_MS) {
                finish();
                return;
            }
            handler.postDelayed(this, 60_000);
        }
    };

    @Override public void received(int count) {
        view.received(count);
    }

    private static final class UploadView extends View {
        private static final int BG = 0xFF111113, WHITE = 0xFFF5F5F7, DIM = 0xFF98989D, GREEN = 0xFF30D158;
        private String url;
        private int count;
        private Bitmap qr;
        private final Paint qrPaint = new Paint();
        private final RectF card = new RectF();
        private final Rect dst = new Rect();
        private float k;
        private Paint title, address, counter;
        private TextPaint body;

        UploadView(Context c) {
            super(c);
            setBackgroundColor(BG);
            qrPaint.setFilterBitmap(false);   // sharp modules
        }

        void show(String url) {
            this.url = url;
            qr = url == null ? null : bitmap(Qr.encode(url));
            invalidate();
        }

        void received(int n) {
            count = n;
            invalidate();
        }

        private static Bitmap bitmap(boolean[][] m) {
            if (m == null) return null;
            int quiet = 4, s = m.length + 2 * quiet;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            b.eraseColor(0xFFFFFFFF);
            for (int y = 0; y < m.length; y++) {
                for (int x = 0; x < m.length; x++) if (m[y][x]) b.setPixel(x + quiet, y + quiet, 0xFF000000);
            }
            return b;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            k = w / 1920f;
            title = Ui.text(Ui.displaySemibold, 72 * k, WHITE);
            address = Ui.text(Ui.medium, 34 * k, WHITE);
            counter = Ui.text(Ui.semibold, 38 * k, GREEN);
            body = new TextPaint(Ui.text(Ui.regular, 34 * k, DIM));
        }

        @Override protected void onDraw(Canvas c) {
            if (title == null) return;
            float left = 140 * k, top = 250 * k, width = 900 * k;
            c.drawText(Ui.tr("Photos from your phone"), left, top, title);
            float y = top + 60 * k;
            if (url == null || qr == null) {
                paragraph(c, Ui.tr("No network: connect to Wi-Fi first, then open this screen again."), left, y, width);
                return;
            }
            y = paragraph(c, Ui.tr("Scan the code with your phone's camera, or open this address in its browser:"), left, y, width);
            y += 56 * k;
            c.drawText(url, left, y, address);
            y += 40 * k;
            y = paragraph(c, Ui.tr("Your phone must be on the same Wi-Fi network."), left, y, width);
            y += 70 * k;
            if (count > 0) {
                c.drawText(Ui.trf("Photos received: {n}", "n", count), left, y, counter);
                y += 30 * k;
            }
            paragraph(c, Ui.tr("When you're done, press Back: the photos show up on the home screen."), left, y, width);

            float side = 560 * k, pad = 28 * k, cx = 1920 * k - 140 * k - side, cy = (getHeight() - side) / 2f;
            card.set(cx - pad, cy - pad, cx + side + pad, cy + side + pad);
            qrPaint.setColor(0xFFFFFFFF);
            c.drawRoundRect(card, 36 * k, 36 * k, qrPaint);
            dst.set(Math.round(cx), Math.round(cy), Math.round(cx + side), Math.round(cy + side));
            c.drawBitmap(qr, null, dst, qrPaint);
        }

        /** Wrapped text from y (its top); returns where it ends. */
        private float paragraph(Canvas c, String text, float x, float y, float width) {
            StaticLayout l = new StaticLayout(text, body, Math.round(width), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0, false);
            c.save();
            c.translate(x, y);
            l.draw(c);
            c.restore();
            return y + l.getHeight();
        }
    }
}

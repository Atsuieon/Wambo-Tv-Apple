package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Guest Wi-Fi (a top button on the home screen): a QR code a phone's camera joins the network with. The network's name
 * shows, its password doesn't. Both are typed once on the TV (OK here) and stay in the app's settings, never in the code.
 */
public class WifiActivity extends Activity {
    private WifiView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new WifiView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.update();
        if (Prefs.guestSsid().isEmpty()) askNetwork();   // nothing to show yet
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            askNetwork();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    /** The network's name and password, typed on the TV. */
    private void askNetwork() {
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText ssid = new EditText(this);
        ssid.setHint(Ui.tr("Network name"));
        ssid.setSingleLine(true);
        ssid.setText(Prefs.guestSsid());
        EditText password = new EditText(this);
        password.setHint(Ui.tr("Password (empty for an open network)"));
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText(Prefs.guestPassword());
        box.addView(ssid);
        box.addView(password);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Ui.tr("Guest Wi-Fi"))
                .setView(box)
                .setPositiveButton(Ui.tr("Save"), (d, which) -> {
                    Prefs.setGuestWifi(ssid.getText().toString().trim(), password.getText().toString());
                    view.update();
                })
                .setNegativeButton(Ui.tr("Cancel"), null)
                .show();
    }

    /** The text a phone's camera joins a network with (WIFI:…;;), special characters escaped. */
    static String wifiCode(String ssid, String password) {
        String type = password.isEmpty() ? "nopass" : "WPA";
        StringBuilder sb = new StringBuilder("WIFI:T:").append(type).append(";S:").append(escape(ssid)).append(';');
        if (!password.isEmpty()) sb.append("P:").append(escape(password)).append(';');
        return sb.append(';').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') sb.append('\\');
            sb.append(ch);
        }
        return sb.toString();
    }

    private static final class WifiView extends View {
        private static final int BG = 0xFF111113, WHITE = 0xFFF5F5F7, DIM = 0xFF98989D;
        private String ssid = "";
        private boolean hasPassword;
        private Bitmap qr;
        private final Paint qrPaint = new Paint();
        private final RectF card = new RectF();
        private final Rect dst = new Rect();
        private float k;
        private Paint title, network, hint;
        private TextPaint body;

        WifiView(Context c) {
            super(c);
            setBackgroundColor(BG);
            qrPaint.setFilterBitmap(false);
        }

        void update() {
            ssid = Prefs.guestSsid();
            hasPassword = !Prefs.guestPassword().isEmpty();
            qr = ssid.isEmpty() ? null : bitmap(Qr.encode(wifiCode(ssid, Prefs.guestPassword())));
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
            network = Ui.text(Ui.semibold, 44 * k, WHITE);
            hint = Ui.text(Ui.regular, 26 * k, DIM);
            body = new TextPaint(Ui.text(Ui.regular, 34 * k, DIM));
        }

        @Override protected void onDraw(Canvas c) {
            if (title == null) return;
            float left = 140 * k, top = 250 * k, width = 900 * k;
            c.drawText(Ui.tr("Guest Wi-Fi"), left, top, title);
            float y = top + 60 * k;
            if (ssid.isEmpty() || qr == null) {
                paragraph(c, Ui.tr("Press OK and type the network's name and password."), left, y, width);
                return;
            }
            y = paragraph(c, Ui.tr("Point your phone's camera at the code to join the network."), left, y, width);
            y += 90 * k;
            c.drawText(ssid, left, y, network);
            y += 60 * k;
            c.drawText(hasPassword ? Ui.tr("Password: hidden") : Ui.tr("Open network, no password"), left, y, body);
            c.drawText(Ui.tr("OK changes the network or the password."), left, getHeight() - 60 * k, hint);

            float side = 560 * k, pad = 28 * k, cx = 1920 * k - 140 * k - side, cy = (getHeight() - side) / 2f;
            card.set(cx - pad, cy - pad, cx + side + pad, cy + side + pad);
            qrPaint.setColor(0xFFFFFFFF);
            c.drawRoundRect(card, 36 * k, 36 * k, qrPaint);
            dst.set(Math.round(cx), Math.round(cy), Math.round(cx + side), Math.round(cy + side));
            c.drawBitmap(qr, null, dst, qrPaint);
        }

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

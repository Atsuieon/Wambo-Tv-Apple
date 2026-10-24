package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * My Photos (Home Settings > My Photos > OK): your photos as thumbnails, each with where it shows (home screen,
 * screensaver). Above them, the background's blur, how often it changes and "Photos from your phone". OK on a photo
 * opens it large with its options: on the home screen, in the screensaver, only this one on the home screen, remove.
 */
public class PhotosActivity extends Activity {
    private PhotosView view;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new PhotosView(this);
        setContentView(view);
        view.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        view.reload();
        Photos.importInbox(this, view::reload);   // what a phone or a computer just sent
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        view.release();
    }

    private static final class PhotosView extends View {
        private static final int BG = 0xFF111113, WHITE = 0xFFF5F5F7, DIM = 0xFF98989D, INK = 0xFF1C1C1E;
        private static final int BLUE = 0xFF0A84FF, GREEN = 0xFF30D158, RED = 0xFFFF453A, OFF = 0x26FFFFFF;
        private static final int COLUMNS = 5, PILLS = 3, DETAIL_ROWS = 4;
        private static final float TW = 320, TH = 180, GAP = 36, PITCH = 266, GRID_TOP = 360, LEFT = 88;

        private final Activity activity;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private final Map<String, Bitmap> thumbs = new HashMap<>();
        private List<File> files = new ArrayList<>();
        private Set<String> offHome, offSaver;
        private boolean inGrid, detail;
        private int pill, index, row, scroll;
        private Bitmap preview;
        private String previewName;
        private float k;
        private Paint title, pillText, pillTextOn, hint, badge, rowText, rowTextOn, rowValue, rowValueOn, empty, count;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix matrix = new Matrix();

        PhotosView(Activity a) {
            super(a);
            activity = a;
            setFocusable(true);
            setFocusableInTouchMode(true);
            setBackgroundColor(BG);
        }

        void release() {
            worker.shutdownNow();
        }

        /** The photos and their settings again (after an import, a removal or a change). */
        void reload() {
            files = new ArrayList<>(Photos.files(getContext()));
            offHome = Prefs.photosOff(Prefs.PHOTO_HOME);
            offSaver = Prefs.photosOff(Prefs.PHOTO_SAVER);
            if (files.isEmpty()) {
                inGrid = false;
                detail = false;
            }
            index = Math.max(0, Math.min(index, files.size() - 1));
            for (File f : files) loadThumb(f);
            if (detail) loadPreview();
            invalidate();
        }

        private void loadThumb(File f) {
            String name = f.getName();
            synchronized (thumbs) {
                if (thumbs.containsKey(name)) return;
                thumbs.put(name, null);   // on its way
            }
            worker.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;   // 2112 × 1188 → 528 × 297, for a 320 × 180 thumbnail (346 when focused)
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b == null) return;
                main.post(() -> {
                    synchronized (thumbs) {
                        thumbs.put(name, b);
                    }
                    invalidate();
                });
            });
        }

        private void loadPreview() {
            if (files.isEmpty()) return;
            File f = files.get(index);
            if (f.getName().equals(previewName)) return;
            previewName = f.getName();
            worker.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;   // 1056 × 594: the preview's size
                Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
                main.post(() -> {
                    if (!f.getName().equals(previewName)) return;
                    preview = b;
                    invalidate();
                });
            });
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            k = w / 1920f;
            title = Ui.text(Ui.displaySemibold, 64 * k, WHITE);
            pillText = Ui.text(Ui.medium, 28 * k, WHITE);
            pillTextOn = Ui.text(Ui.semibold, 28 * k, INK);
            hint = Ui.text(Ui.regular, 24 * k, DIM);
            badge = Ui.text(Ui.semibold, 20 * k, WHITE);
            rowText = Ui.text(Ui.medium, 32 * k, WHITE);
            rowTextOn = Ui.text(Ui.semibold, 32 * k, INK);
            rowValue = Ui.text(Ui.regular, 32 * k, DIM);
            rowValueOn = Ui.text(Ui.regular, 32 * k, 0xFF6E6E73);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowValueOn.setTextAlign(Paint.Align.RIGHT);
            empty = Ui.text(Ui.regular, 32 * k, DIM);
            count = Ui.text(Ui.regular, 28 * k, DIM);
        }

        // MARK: - Drawing

        @Override protected void onDraw(Canvas c) {
            if (title == null) return;
            if (detail && !files.isEmpty()) drawDetail(c);
            else drawGrid(c);
        }

        private String[] pillLabels() {
            return new String[] {
                Ui.trf("Background blur: {n}%", "n", Prefs.photoBlur()),
                Ui.trf("New background: {when}", "when", interval(Prefs.photoInterval())),
                Ui.tr("Photos from your phone")};
        }

        private static String interval(int minutes) {
            if (minutes >= 1440) return Ui.tr("once a day");
            if (minutes == 60) return Ui.tr("every hour");
            if (minutes > 60) return Ui.trf("every {n} hours", "n", minutes / 60);
            return Ui.trf("every {n} min", "n", minutes);
        }

        private void drawGrid(Canvas c) {
            c.drawText(Ui.tr("My Photos"), LEFT * k, 150 * k, title);
            if (!files.isEmpty()) {
                String n = Ui.plural("plural|one:{n} photo|other:{n} photos", files.size());
                c.drawText(n, LEFT * k + title.measureText(Ui.tr("My Photos")) + 28 * k, 150 * k, count);
            }
            // The settings, as pills
            String[] labels = pillLabels();
            float x = LEFT * k, top = 210 * k, h = 64 * k;
            for (int i = 0; i < PILLS; i++) {
                boolean on = !inGrid && pill == i;
                float w = pillText.measureText(labels[i]) + 56 * k;
                r.set(x, top, x + w, top + h);
                fill.setColor(on ? WHITE : 0x1FFFFFFF);
                c.drawRoundRect(r, h / 2, h / 2, fill);
                c.drawText(labels[i], x + 28 * k, r.centerY() + Ui.halfCap(pillText), on ? pillTextOn : pillText);
                x += w + 20 * k;
            }
            if (files.isEmpty()) {
                c.drawText(Ui.tr("No photos yet: add them from your phone with the button above."), LEFT * k, (GRID_TOP + 90) * k, empty);
                return;
            }
            int rows = (files.size() + COLUMNS - 1) / COLUMNS;
            int focusRow = index / COLUMNS;
            if (inGrid) scroll = Math.max(0, Math.min(focusRow - 1, rows - 2));
            c.save();
            c.clipRect(0, (GRID_TOP - 24) * k, getWidth(), getHeight() - 70 * k);
            for (int i = scroll * COLUMNS; i < files.size(); i++) {
                int col = i % COLUMNS, rw = i / COLUMNS - scroll;
                float tx = (LEFT + col * (TW + GAP)) * k, ty = (GRID_TOP + rw * PITCH) * k;
                if (ty > getHeight()) break;
                drawThumb(c, i, tx, ty, inGrid && i == index);
            }
            c.restore();
            c.drawText(inGrid ? Ui.tr("OK opens the photo's options.") : Ui.tr("OK changes the setting; down goes to the photos."),
                    LEFT * k, getHeight() - 36 * k, hint);
        }

        private void drawThumb(Canvas c, int i, float x, float y, boolean focused) {
            File f = files.get(i);
            float grow = focused ? 0.08f : 0, w = TW * k, h = TH * k;
            r.set(x - w * grow / 2, y - h * grow / 2, x + w * (1 + grow / 2), y + h * (1 + grow / 2));
            float radius = 16 * k;
            if (focused) {
                fill.setColor(WHITE);
                c.drawRoundRect(r.left - 5 * k, r.top - 5 * k, r.right + 5 * k, r.bottom + 5 * k, radius + 5 * k, radius + 5 * k, fill);
            }
            Bitmap b;
            synchronized (thumbs) {
                b = thumbs.get(f.getName());
            }
            if (b == null) {
                fill.setColor(0xFF2C2C2E);
                c.drawRoundRect(r, radius, radius, fill);
            } else {
                drawRounded(c, b, r, radius);
            }
            // Where it shows
            float by = y + h + 18 * k, bh = 34 * k, bx = x;
            bx = drawBadge(c, Ui.tr("Home Screen"), !offHome.contains(f.getName()), BLUE, bx, by, bh);
            drawBadge(c, Ui.tr("Screensaver"), !offSaver.contains(f.getName()), GREEN, bx + 10 * k, by, bh);
        }

        private float drawBadge(Canvas c, String text, boolean on, int color, float x, float y, float h) {
            float w = badge.measureText(text) + 24 * k;
            r.set(x, y, x + w, y + h);
            fill.setColor(on ? color : OFF);
            c.drawRoundRect(r, h / 2, h / 2, fill);
            badge.setColor(on ? 0xFFFFFFFF : 0x80FFFFFF);
            c.drawText(text, x + 12 * k, r.centerY() + Ui.halfCap(badge), badge);
            badge.setColor(WHITE);
            return r.right;
        }

        private void drawRounded(Canvas c, Bitmap b, RectF box, float radius) {
            BitmapShader shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(box.width() / b.getWidth(), box.height() / b.getHeight());
            matrix.setScale(s, s);
            matrix.postTranslate(box.left + (box.width() - b.getWidth() * s) / 2, box.top + (box.height() - b.getHeight() * s) / 2);
            shader.setLocalMatrix(matrix);
            image.setShader(shader);
            c.drawRoundRect(box, radius, radius, image);
            image.setShader(null);
        }

        private void drawDetail(Canvas c) {
            File f = files.get(index);
            c.drawText(Ui.trf("Photo {i} of {n}", "i", index + 1, "n", files.size()), LEFT * k, 150 * k, title);
            r.set(LEFT * k, 210 * k, (LEFT + 1120) * k, (210 + 630) * k);
            if (preview != null && f.getName().equals(previewName)) {
                drawRounded(c, preview, r, 24 * k);
            } else {
                fill.setColor(0xFF2C2C2E);
                c.drawRoundRect(r, 24 * k, 24 * k, fill);
            }
            String[] labels = {Ui.tr("On the home screen"), Ui.tr("In the screensaver"),
                    Ui.tr("Only this photo as the background"), Ui.tr("Remove this photo")};
            String[] values = {onOff(!offHome.contains(f.getName())), onOff(!offSaver.contains(f.getName())), "", ""};
            float left = 1270 * k, right = 1832 * k, top = 210 * k, h = 92 * k;
            for (int i = 0; i < DETAIL_ROWS; i++) {
                r.set(left, top + i * (h + 12 * k), right, top + i * (h + 12 * k) + h);
                boolean on = row == i;
                if (on) {
                    fill.setColor(WHITE);
                    c.drawRoundRect(r, 18 * k, 18 * k, fill);
                }
                Paint label = on ? rowTextOn : rowText;
                int keep = label.getColor();
                if (i == 3) label.setColor(on ? RED : 0xFFFF6961);
                String text = android.text.TextUtils.ellipsize(labels[i], new android.text.TextPaint(label),
                        r.width() - (values[i].isEmpty() ? 60 : 180) * k, android.text.TextUtils.TruncateAt.END).toString();
                c.drawText(text, r.left + 30 * k, r.centerY() + Ui.halfCap(label), label);
                label.setColor(keep);
                if (!values[i].isEmpty()) c.drawText(values[i], r.right - 30 * k, r.centerY() + Ui.halfCap(label), on ? rowValueOn : rowValue);
            }
            c.drawText(Ui.tr("Left and right change the photo; Back returns to all of them."), LEFT * k, getHeight() - 36 * k, hint);
        }

        private static String onOff(boolean on) {
            return on ? Ui.tr("On") : Ui.tr("Off");
        }

        // MARK: - Keys

        @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP: move(0, -1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    ok();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (detail) {
                        detail = false;
                        invalidate();
                        return true;
                    }
                    return super.onKeyDown(keyCode, e);
                default:
                    return super.onKeyDown(keyCode, e);
            }
        }

        private void move(int dx, int dy) {
            if (detail) {
                if (dy != 0) row = Math.max(0, Math.min(DETAIL_ROWS - 1, row + dy));
                if (dx != 0 && !files.isEmpty()) {
                    index = (index + dx + files.size()) % files.size();
                    loadPreview();
                }
            } else if (!inGrid) {
                if (dx != 0) pill = Math.max(0, Math.min(PILLS - 1, pill + dx));
                if (dy > 0 && !files.isEmpty()) inGrid = true;
            } else {
                int next = index + dx + dy * COLUMNS;
                if (dy < 0 && index < COLUMNS) {
                    inGrid = false;
                } else if (dx != 0 && (index % COLUMNS + dx < 0 || index % COLUMNS + dx >= COLUMNS)) {
                    return;   // the row's edge
                } else if (next >= 0 && next < files.size()) {
                    index = next;
                } else if (dy > 0 && index / COLUMNS < (files.size() - 1) / COLUMNS) {
                    index = files.size() - 1;   // down to a shorter last row
                }
            }
            invalidate();
        }

        private void ok() {
            if (detail) {
                detailAction();
            } else if (!inGrid) {
                switch (pill) {
                    case 0: Prefs.setPhotoBlur(next(Prefs.BLURS, Prefs.photoBlur())); break;
                    case 1: Prefs.setPhotoInterval(next(Prefs.INTERVALS, Prefs.photoInterval())); break;
                    default:
                        activity.startActivity(new Intent().setComponent(new ComponentName(activity, PhoneUploadActivity.class)));
                        return;
                }
            } else if (!files.isEmpty()) {
                detail = true;
                row = 0;
                loadPreview();
            }
            invalidate();
        }

        private static int next(int[] values, int current) {
            for (int v : values) if (v > current) return v;
            return values[0];
        }

        private void detailAction() {
            File f = files.get(index);
            String name = f.getName();
            switch (row) {
                case 0: Prefs.setPhotoOff(Prefs.PHOTO_HOME, name, !offHome.contains(name)); break;
                case 1: Prefs.setPhotoOff(Prefs.PHOTO_SAVER, name, !offSaver.contains(name)); break;
                case 2: {
                    List<String> all = new ArrayList<>();
                    for (File g : files) all.add(g.getName());
                    Prefs.setOnlyHomePhoto(all, name);
                    break;
                }
                default:
                    askRemove(f);
                    return;
            }
            reload();
        }

        private void askRemove(File f) {
            AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(Ui.plural("plural|one:Remove {n} photo?|other:Remove {n} photos?", 1))
                    .setPositiveButton(Ui.tr("Remove"), (d, which) -> {
                        Photos.remove(getContext(), f);
                        synchronized (thumbs) {
                            thumbs.remove(f.getName());
                        }
                        previewName = null;
                        preview = null;
                        reload();
                        if (files.isEmpty()) detail = false;
                        else loadPreview();
                        invalidate();
                    })
                    .setNegativeButton(Ui.tr("Cancel"), null)
                    .show();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();   // one stray OK keeps it
        }
    }
}

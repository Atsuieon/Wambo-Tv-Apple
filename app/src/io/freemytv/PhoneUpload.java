package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Photos from a phone, over the home Wi-Fi: a small web server that runs only while PhoneUploadActivity is on screen.
 * The phone opens http://<TV>:<port>/<token> (the QR code), picks photos, and each one is written to Photos' private
 * inbox, where the home screen imports it the next time it opens. The token is random for each opening, every other
 * address answers 404, and nothing can be read back: the server only takes photos in.
 */
final class PhoneUpload {
    interface Listener {
        /** On the main thread, after each photo that arrived whole. */
        void received(int count);
    }

    private static final int FIRST_PORT = 8642, LAST_PORT = 8652;
    private static final long MAX_BYTES = 40L << 20;   // a phone photo is 2 to 15 MB
    private static final String[] KINDS = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"};
    private static final String TOKEN_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";

    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String token;
    private final AtomicInteger received = new AtomicInteger(), seq = new AtomicInteger();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile ServerSocket server;
    private volatile long lastActivity = System.currentTimeMillis();

    PhoneUpload(Context c, Listener listener) {
        this.app = c.getApplicationContext();
        this.listener = listener;
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(TOKEN_CHARS.charAt(r.nextInt(TOKEN_CHARS.length())));
        token = sb.toString();
    }

    /** Starts listening; the address for the phone, or null without a network (or a free port). */
    String start() {
        String ip = address();
        if (ip == null) return null;
        for (int port = FIRST_PORT; port <= LAST_PORT; port++) {
            try {
                ServerSocket s = new ServerSocket(port);
                server = s;
                Thread t = new Thread(() -> accept(s), "phone-upload");
                t.setDaemon(true);
                t.start();
                AppLog.w("phone upload listening on port " + port);
                return "http://" + ip + ":" + port + "/" + token;
            } catch (IOException e) {
                // taken: the next one
            }
        }
        return null;
    }

    void stop() {
        ServerSocket s = server;
        server = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    /** When the phone last sent anything (the screen closes itself after a long quiet spell). */
    long lastActivity() {
        return lastActivity;
    }

    private void accept(ServerSocket s) {
        while (server == s) {
            try {
                Socket socket = s.accept();
                socket.setSoTimeout(30_000);
                pool.execute(() -> serve(socket));
            } catch (IOException | java.util.concurrent.RejectedExecutionException e) {
                if (server != s) return;
            }
        }
    }

    // MARK: - HTTP (one request per connection)

    private void serve(Socket socket) {
        try (Socket so = socket) {
            InputStream in = so.getInputStream();
            OutputStream out = new BufferedOutputStream(so.getOutputStream());
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] request = lines[0].split(" ");
            if (request.length < 2) {
                respond(out, 400, "text/plain", "Bad request");
                return;
            }
            long length = -1;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0 && lines[i].substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    try {
                        length = Long.parseLong(lines[i].substring(colon + 1).trim());
                    } catch (NumberFormatException e) {
                        length = -1;
                    }
                }
            }
            String method = request[0], target = request[1], query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                query = target.substring(q + 1);
                target = target.substring(0, q);
            }
            String base = "/" + token;
            if (method.equals("GET") && (target.equals(base) || target.equals(base + "/"))) {
                lastActivity = System.currentTimeMillis();
                respond(out, 200, "text/html; charset=utf-8", page());
            } else if ((method.equals("PUT") || method.equals("POST")) && target.equals(base + "/up")) {
                lastActivity = System.currentTimeMillis();
                upload(in, out, param(query, "name"), length);
            } else {
                respond(out, 404, "text/plain", "Not found");
            }
        } catch (IOException e) {
            AppLog.w("phone upload: " + e.getMessage());
        }
    }

    /** The request line and headers, up to the blank line; null if the phone hung up or sent too much. */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (b.size() < 16_384) {
            int c = in.read();
            if (c < 0) return null;
            b.write(c);
            if (c == '\n' && (state == 1 || state == 3)) state++;
            else state = c == '\r' ? (state == 2 ? 3 : 1) : 0;
            if (state == 4) return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private static String param(String query, String name) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                } catch (IOException | IllegalArgumentException e) {
                    return "";
                }
            }
        }
        return "";
    }

    private void upload(InputStream in, OutputStream out, String name, long length) throws IOException {
        String kind = kind(name);
        if (kind == null) {
            respond(out, 415, "application/json", error(Ui.tr("Only photos (JPEG, PNG, WebP or HEIC).")));
            return;
        }
        if (length <= 0 || length > MAX_BYTES) {
            respond(out, 413, "application/json", error(Ui.tr("This photo is too big.")));
            return;
        }
        if (received.get() >= Photos.MAX) {
            respond(out, 409, "application/json", error(Ui.tr("That's enough photos for now.")));
            return;
        }
        File inbox = Photos.privateInbox(app);
        if (!inbox.isDirectory() && !inbox.mkdirs()) throw new IOException("can't create " + inbox);
        // In send order (Photos imports them by name); written under a hidden name until all of it came.
        String stamp = String.format(Locale.ROOT, "phone-%013d-%03d", System.currentTimeMillis(), seq.incrementAndGet() % 1000);
        File part = new File(inbox, "." + stamp + kind), done = new File(inbox, stamp + kind);
        long left = length;
        try (OutputStream f = new FileOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                f.write(buf, 0, n);
                left -= n;
                lastActivity = System.currentTimeMillis();
            }
        }
        if (left > 0 || !part.renameTo(done)) {
            part.delete();
            respond(out, 400, "application/json", error(Ui.tr("The photo didn't arrive whole. Try again.")));
            return;
        }
        int count = received.incrementAndGet();
        main.post(() -> listener.received(count));
        respond(out, 200, "application/json", "{\"ok\":true}");
    }

    private static String kind(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String k : KINDS) if (n.endsWith(k)) return k;
        return null;
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message).toString();
        } catch (org.json.JSONException e) {
            return "{\"ok\":false}";
        }
    }

    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    // MARK: - The page the phone sees

    private static String js(String s) {
        return JSONObject.quote(s);
    }

    private String page() {
        return "<!doctype html><html lang=\"" + Ui.locale().getLanguage() + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Free My TV</title><style>"
                + "body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#111113;color:#f5f5f7;"
                + "display:flex;min-height:100vh;align-items:center;justify-content:center}"
                + "main{width:100%;max-width:420px;padding:24px;box-sizing:border-box;text-align:center}"
                + "h1{font-size:26px;margin:0 0 8px}p{color:#98989d;font-size:16px;line-height:1.4}"
                + "label{display:block;margin:28px 0 16px;padding:18px;border-radius:16px;background:#0a84ff;color:#fff;"
                + "font-size:19px;font-weight:600;cursor:pointer}input{display:none}"
                + "#bar{height:8px;border-radius:4px;background:#2c2c2e;overflow:hidden;margin:16px 0}"
                + "#fill{height:100%;width:0;background:#30d158;transition:width .2s}#msg{color:#f5f5f7;min-height:24px}"
                + "</style></head><body><main>"
                + "<h1>" + html(Ui.tr("Send photos to the TV")) + "</h1>"
                + "<p>" + html(Ui.tr("They show on the home screen and in the screensaver.")) + "</p>"
                + "<label>" + html(Ui.tr("Choose photos"))
                + "<input id=\"f\" type=\"file\" accept=\"image/*\" multiple></label>"
                + "<div id=\"bar\"><div id=\"fill\"></div></div><div id=\"msg\"></div>"
                + "<p>" + html(Ui.tr("This page stops working once the screen on the TV is closed.")) + "</p>"
                + "</main><script>"
                + "var T={sending:" + js(Ui.tr("Sending {done} of {total}…")) + ",sent:" + js(Ui.tr("Sent: {n}. You can choose more."))
                + ",failed:" + js(Ui.tr("Couldn't send {name}.")) + "};"
                + "var f=document.getElementById('f'),msg=document.getElementById('msg'),fill=document.getElementById('fill'),sent=0;"
                + "f.onchange=function(){var files=[].slice.call(f.files),i=0,total=files.length;"
                + "function next(){if(i>=total){msg.textContent=T.sent.replace('{n}',sent);f.value='';return;}"
                + "var file=files[i];msg.textContent=T.sending.replace('{done}',i+1).replace('{total}',total);"
                + "var x=new XMLHttpRequest();x.open('PUT','" + "/" + token + "/up?name='+encodeURIComponent(file.name||'photo.jpg'));"
                + "x.upload.onprogress=function(e){if(e.lengthComputable)fill.style.width=((i+e.loaded/e.total)/total*100)+'%';};"
                + "x.onload=function(){var ok=false,err='';try{var r=JSON.parse(x.responseText);ok=r.ok;err=r.error||'';}catch(e){}"
                + "if(ok)sent++;else{msg.textContent=err||T.failed.replace('{name}',file.name);}"
                + "i++;fill.style.width=(i/total*100)+'%';setTimeout(next,ok?0:1500);};"
                + "x.onerror=function(){msg.textContent=T.failed.replace('{name}',file.name);i++;setTimeout(next,1500);};"
                + "x.send(file);}next();};"
                + "</script></body></html>";
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // MARK: - The TV's address on the home network

    /** The TV's IPv4 address on Wi-Fi or Ethernet (private ranges first), or null without a network. */
    static String address() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    if (a.isSiteLocalAddress()) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
        } catch (IOException | RuntimeException e) {
            AppLog.w("no network address: " + e.getMessage());
        }
        return fallback;
    }
}

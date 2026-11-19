package com.wevent.mobilestream;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, ip, streams, readers, inbound, outbound, cpu, ram, thermal, engine, updateStatus;
    private Runnable pollTask;
    private int navy = 0xFF0B1220, panel = 0xFF17243D, gold = 0xFFC8A45D;
    private boolean started = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        askNotificationPermission();
        startServer();
        startPolling();
        handler.postDelayed(this::checkForUpdates, 2500);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(24));
        page.setBackgroundColor(navy);
        scroll.addView(page);

        TextView title = text("MOBILE STREAM SERVER", 23, 0xFFFFFFFF, true);
        page.addView(title);
        TextView subtitle = text("PRO STREAMING CONTROL  •  ANDROID V5.0", 11, gold, true);
        page.addView(subtitle);

        status = text("STARTING SERVER…", 17, gold, true);
        status.setPadding(0, dp(24), 0, dp(8));
        page.addView(status);

        engine = text("MediaMTX engine: checking…", 13, 0xFFB8C4D8, false);
        page.addView(engine);

        ip = text("Network IP: detecting…", 15, 0xFFFFFFFF, true);
        ip.setPadding(0, dp(14), 0, dp(12));
        page.addView(ip);

        page.addView(section("STREAMING SERVICES"));
        page.addView(metricCard("RTMP  •  TCP 1935", "Ready for publishing and reading"));
        page.addView(metricCard("SRT  •  UDP 8890", "Ready for publishing and reading"));

        page.addView(section("LIVE STATISTICS"));
        streams = addMetric(page, "ACTIVE STREAMS", "0");
        readers = addMetric(page, "CONNECTED READERS", "0");
        inbound = addMetric(page, "INBOUND BITRATE", "0.00 Mbps");
        outbound = addMetric(page, "OUTBOUND BITRATE", "0.00 Mbps");

        page.addView(section("PHONE MONITORING"));
        cpu = addMetric(page, "CPU", "Not available");
        ram = addMetric(page, "RAM", "Not available");
        thermal = addMetric(page, "THERMAL STATUS", "Not available");

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(18), 0, 0);
        Button stop = button("STOP SERVER", gold, navy);
        Button web = button("OPEN WEB DASHBOARD", 0xFF33435F, 0xFFFFFFFF);
        buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        webLp.leftMargin = dp(8);
        buttons.addView(web, webLp);
        page.addView(buttons);

        Button start = button("START / RESTART SERVER", 0xFF1F8A5B, 0xFFFFFFFF);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(50));
        startLp.topMargin = dp(10);
        page.addView(start, startLp);

        TextView note = text("Web dashboard: http://PHONE-IP:8080\nRTMP: rtmp://PHONE-IP:1935/live/camera1\nSRT: srt://PHONE-IP:8890?streamid=publish:live/camera1", 12, 0xFF9EACC2, false);
        note.setPadding(0, dp(18), 0, 0);
        page.addView(note);

        updateStatus = text("Update channel: checking…", 12, 0xFF9EACC2, false);
        updateStatus.setPadding(0, dp(16), 0, dp(4));
        page.addView(updateStatus);
        Button updates = button("CHECK FOR UPDATES", 0xFF33435F, 0xFFFFFFFF);
        LinearLayout.LayoutParams updatesLp = new LinearLayout.LayoutParams(-1, dp(48));
        updatesLp.topMargin = dp(6);
        page.addView(updates, updatesLp);
        updates.setOnClickListener(v -> checkForUpdates());

        stop.setOnClickListener(v -> stopServer());
        start.setOnClickListener(v -> startServer());
        web.setOnClickListener(v -> openWeb());
        setContentView(scroll);
    }

    private TextView section(String s) {
        TextView t = text(s, 13, gold, true);
        t.setPadding(0, dp(22), 0, dp(8));
        return t;
    }

    private View metricCard(String title, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackgroundColor(panel);
        TextView a = text(title, 16, 0xFFFFFFFF, true);
        TextView b = text(detail, 12, 0xFF9EACC2, false);
        box.addView(a); box.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private TextView addMetric(LinearLayout parent, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackgroundColor(panel);
        TextView l = text(label, 11, 0xFF9EACC2, true);
        TextView v = text(value, 19, 0xFFFFFFFF, true);
        box.addView(l); box.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        parent.addView(box, lp);
        return v;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
    }

    private void startServer() {
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        started = true;
        status.setText("STARTING…");
    }

    private void stopServer() {
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_STOP);
        startService(i);
        started = false;
        status.setText("SERVER STOPPED");
    }

    private void openWeb() {
        String address = "http://127.0.0.1:8080";
        try {
            JSONObject data = new JSONObject(fetch("http://127.0.0.1:8080/api/status"));
            String detected = data.optString("ip", "");
            if (!detected.isEmpty()) address = "http://" + detected + ":8080";
        } catch (Exception ignored) {}
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
    }


    private void checkForUpdates() {
        String repo = BuildConfig.UPDATE_REPO == null ? "" : BuildConfig.UPDATE_REPO.trim();
        if (repo.isEmpty() || repo.contains("YOUR_GITHUB")) {
            updateStatus.setText("Update check: configure MSS_UPDATE_REPO in app/build.gradle");
            return;
        }
        updateStatus.setText("Checking latest release…");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
                c = (HttpURLConnection)url.openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "MobileStreamServer");
                String body;
                try (java.io.InputStream in = c.getInputStream();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] b = new byte[4096]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    body = out.toString("UTF-8");
                }
                JSONObject release = new JSONObject(body);
                String tag = release.optString("tag_name", "");
                String html = release.optString("html_url", "https://github.com/" + repo + "/releases");
                String latest = tag.replaceFirst("^[vV]", "");
                String current = BuildConfig.VERSION_NAME.replaceFirst("^[vV]", "");
                boolean newer = compareVersions(latest, current) > 0;
                runOnUiThread(() -> {
                    updateStatus.setText(newer ? "Update available: " + tag : "Application is up to date (" + BuildConfig.VERSION_NAME + ")");
                    if (newer) {
                        new AlertDialog.Builder(this)
                            .setTitle("Mobile Stream Server update")
                            .setMessage("Version " + tag + " is available. Open the release page to download it. Android will ask you to confirm installation.")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Open release", (d, w) ->
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(html))))
                            .show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateStatus.setText("Update check failed: " + e.getMessage()));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private int compareVersions(String a, String b) {
        try {
            String[] aa = a.split("[.-]");
            String[] bb = b.split("[.-]");
            int count = Math.max(aa.length, bb.length);
            for (int i = 0; i < count; i++) {
                int av = i < aa.length ? Integer.parseInt(aa[i]) : 0;
                int bv = i < bb.length ? Integer.parseInt(bb[i]) : 0;
                if (av != bv) return Integer.compare(av, bv);
            }
        } catch (Exception ignored) {
            return a.compareToIgnoreCase(b);
        }
        return 0;
    }

    private void startPolling() {
        pollTask = new Runnable() {
            @Override public void run() {
                new Thread(() -> {
                    try {
                        JSONObject data = new JSONObject(fetch("http://127.0.0.1:8080/api/status"));
                        runOnUiThread(() -> showStatus(data));
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            status.setText(started ? "STARTING / CHECKING ENGINE" : "SERVER OFFLINE");
                            engine.setText("Waiting for MediaMTX and local dashboard…");
                        });
                    }
                }).start();
                handler.postDelayed(this, 2500);
            }
        };
        handler.post(pollTask);
    }

    private void showStatus(JSONObject d) {
        boolean online = d.optBoolean("online", false);
        status.setText(online ? "● SERVER ONLINE" : "● SERVER OFFLINE");
        status.setTextColor(online ? 0xFF39D98A : 0xFFFF6464);
        engine.setText(d.optString("engine", "MediaMTX"));
        ip.setText("Network IP: " + d.optString("ip", "Not detected") + "   •   Web :8080");
        streams.setText(String.valueOf(d.optInt("itemCount", 0)));
        readers.setText(String.valueOf(d.optInt("readers", 0)));
        inbound.setText(String.format(java.util.Locale.US, "%.2f Mbps", d.optDouble("inboundMbps", 0)));
        outbound.setText(String.format(java.util.Locale.US, "%.2f Mbps", d.optDouble("outboundMbps", 0)));
        JSONObject sys = d.optJSONObject("system");
        if (sys != null) {
            cpu.setText(sys.isNull("cpuPercent") ? "Not available" : sys.optDouble("cpuPercent") + "%");
            ram.setText(sys.optString("ramText", "Not available"));
            thermal.setText(sys.optString("thermalStatus", "Not available"));
        }
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(1800); c.setReadTimeout(1800);
        try {
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

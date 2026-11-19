package com.wevent.mobilestream;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalWebServer {
    private final Context context;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread thread;
    private long previousIn = -1, previousOut = -1, previousAt = 0;
    private double inMbps = 0, outMbps = 0;
    private long previousCpuIdle = -1, previousCpuTotal = -1;
    private final long startedAt = SystemClock.elapsedRealtime();

    public LocalWebServer(Context c) { context = c.getApplicationContext(); }

    public void start() throws Exception {
        if (running) return;
        serverSocket = new ServerSocket(8080, 32, java.net.InetAddress.getByName("0.0.0.0"));
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(3000);
                    new Thread(() -> handle(client), "MSS-Web-Client").start();
                } catch (Exception e) {
                    if (running) android.util.Log.e("MSS-Web", "Accept failed", e);
                }
            }
        }, "MSS-WebServer");
        thread.start();
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            String first = reader.readLine();
            if (first == null) return;
            String[] parts = first.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}

            if (path.equals("/") || path.equals("/dashboard.html")) {
                byte[] body;
                try (InputStream in = context.getAssets().open("dashboard.html")) {
                    body = readAll(in);
                }
                respond(s.getOutputStream(), "200 OK", "text/html; charset=utf-8", body);
            } else if (path.startsWith("/api/status")) {
                JSONObject data = makeStatus();
                respond(s.getOutputStream(), "200 OK", "application/json; charset=utf-8", data.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                respond(s.getOutputStream(), "404 Not Found", "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            android.util.Log.e("MSS-Web", "Request failed", e);
        }
    }

    private void respond(OutputStream out, String status, String type, byte[] body) throws Exception {
        String header = "HTTP/1.1 " + status + "\r\n" +
            "Content-Type: " + type + "\r\n" +
            "Content-Length: " + body.length + "\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    private String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(1200); c.setReadTimeout(1200);
        try (InputStream in = c.getInputStream()) { return new String(readAll(in), StandardCharsets.UTF_8); }
        finally { c.disconnect(); }
    }

    private JSONObject makeStatus() {
        JSONObject result = new JSONObject();
        try {
            String api = get("http://127.0.0.1:9997/v3/paths/list");
            String metrics = get("http://127.0.0.1:9998/metrics");
            JSONObject paths = new JSONObject(api);
            JSONArray items = paths.optJSONArray("items");
            if (items == null) items = new JSONArray();

            long inBytes = metricSum(metrics, "paths_inbound_bytes");
            long outBytes = metricSum(metrics, "paths_outbound_bytes");
            long now = SystemClock.elapsedRealtime();
            if (previousAt > 0 && now > previousAt) {
                double seconds = (now - previousAt) / 1000.0;
                inMbps = Math.max(0, (inBytes - previousIn) * 8.0 / seconds / 1_000_000.0);
                outMbps = Math.max(0, (outBytes - previousOut) * 8.0 / seconds / 1_000_000.0);
            }
            previousIn = inBytes; previousOut = outBytes; previousAt = now;

            int readers = 0;
            JSONArray enriched = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                JSONArray rs = item.optJSONArray("readers");
                int count = rs == null ? 0 : rs.length();
                readers += count;
                JSONObject row = new JSONObject();
                row.put("name", item.optString("name", ""));
                row.put("ready", item.optBoolean("ready", false));
                row.put("readerCount", count);
                enriched.put(row);
            }

            result.put("online", true);
            result.put("engine", "MediaMTX Android ARM64");
            result.put("ip", getLocalIp());
            result.put("itemCount", items.length());
            result.put("readers", readers);
            result.put("inboundMbps", inMbps);
            result.put("outboundMbps", outMbps);
            result.put("rtmpConnections", metricCount(metrics, "rtmp_conns"));
            result.put("srtConnections", metricCount(metrics, "srt_conns"));
            result.put("items", enriched);
            result.put("uptimeSeconds", (now - startedAt) / 1000);
            result.put("system", systemInfo());
        } catch (Exception e) {
            try {
                result.put("online", false);
                result.put("engine", "MediaMTX not responding");
                result.put("error", e.getMessage() == null ? "Connection unavailable" : e.getMessage());
                result.put("ip", getLocalIp());
                result.put("items", new JSONArray());
            } catch (Exception ignored) {}
        }
        return result;
    }

    private long metricSum(String body, String metric) {
        long sum = 0;
        Pattern p = Pattern.compile("^" + Pattern.quote(metric) + "(?:\\{[^}]*\\})?\\s+([0-9.eE+-]+)\\s*$", Pattern.MULTILINE);
        Matcher m = p.matcher(body);
        while (m.find()) try { sum += (long)Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        return sum;
    }

    private int metricCount(String body, String metric) {
        Pattern p = Pattern.compile("^" + Pattern.quote(metric) + "(?:\\{[^}]*\\})?\\s+([0-9.eE+-]+)\\s*$", Pattern.MULTILINE);
        Matcher m = p.matcher(body);
        int sum = 0;
        while (m.find()) try { sum += (int)Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        return sum;
    }

    private JSONObject systemInfo() throws Exception {
        JSONObject sys = new JSONObject();
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        sys.put("ramUsedMb", used / 1048576);
        sys.put("ramTotalMb", mi.totalMem / 1048576);
        sys.put("ramText", (used / 1048576) + " / " + (mi.totalMem / 1048576) + " MB");
        Double cpu = readCpuPercent();
        if (cpu == null) sys.put("cpuPercent", JSONObject.NULL);
        else sys.put("cpuPercent", cpu);
        if (Build.VERSION.SDK_INT >= 29) {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            int t = pm.getCurrentThermalStatus();
            String[] names = {"NORMAL","LIGHT","MODERATE","SEVERE","CRITICAL","EMERGENCY","SHUTDOWN"};
            sys.put("thermalStatus", t >= 0 && t < names.length ? names[t] : "UNKNOWN");
        } else sys.put("thermalStatus", "Not supported");
        return sys;
    }


    private Double readCpuPercent() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            String[] parts = line.trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < parts.length; i++) total += Long.parseLong(parts[i]);
            long idle = Long.parseLong(parts[4]);
            if (parts.length > 5) idle += Long.parseLong(parts[5]);
            Double result = null;
            if (previousCpuTotal >= 0 && total > previousCpuTotal) {
                long totalDelta = total - previousCpuTotal;
                long idleDelta = idle - previousCpuIdle;
                result = Math.max(0, Math.min(100, 100.0 * (totalDelta - idleDelta) / totalDelta));
            }
            previousCpuTotal = total;
            previousCpuIdle = idle;
            return result == null ? null : Math.round(result * 10.0) / 10.0;
        } catch (Exception e) {
            return null;
        }
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallback = "";
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase();
                for (java.net.InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String ip = address.getHostAddress();
                        if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("eth")) return ip;
                        if (fallback.isEmpty()) fallback = ip;
                    }
                }
            }
            return fallback.isEmpty() ? "127.0.0.1" : fallback;
        } catch (Exception e) { return "127.0.0.1"; }
    }
}

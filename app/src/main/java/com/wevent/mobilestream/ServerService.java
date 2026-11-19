package com.wevent.mobilestream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ServerService extends Service {
    public static final String ACTION_START = "com.wevent.mobilestream.START";
    public static final String ACTION_STOP = "com.wevent.mobilestream.STOP";
    private static final String CHANNEL = "mss_server";
    private Process engine;
    private LocalWebServer web;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean stopping = false;
    private File logFile;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MobileStreamServer:Streaming");
        wakeLock.setReferenceCounted(false);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopEverything();
            return START_NOT_STICKY;
        }
        startAsForeground();
        stopping = false;
        try {
            if (!wakeLock.isHeld()) wakeLock.acquire();
        } catch (Exception ignored) {}
        startWeb();
        startEngine();
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, 5000);
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification n = buildNotification("Starting RTMP + SRT services");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, n);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Streaming server", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps the RTMP and SRT server running");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        b.setContentTitle("Mobile Stream Server")
         .setContentText(text)
         .setSmallIcon(android.R.drawable.presence_video_online)
         .setOngoing(true);
        return b.build();
    }

    private void updateNotification(String text) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7, buildNotification(text));
    }

    private void startWeb() {
        try {
            if (web == null) {
                web = new LocalWebServer(this);
                web.start();
            }
        } catch (Exception e) {
            updateNotification("Web dashboard error: " + e.getMessage());
        }
    }

    private void startEngine() {
        if (engine != null && engine.isAlive()) return;
        try {
            // Android extracts this executable from jniLibs into the app's nativeLibraryDir.
            // Unlike filesDir, nativeLibraryDir is intended for executable native binaries.
            File bin = new File(getApplicationInfo().nativeLibraryDir, "libmediamtx.so");
            if (!bin.isFile()) {
                throw new IllegalStateException("MediaMTX binary missing from nativeLibraryDir: " + bin.getAbsolutePath());
            }
            if (!bin.canExecute()) {
                throw new IllegalStateException("MediaMTX binary is not executable: " + bin.getAbsolutePath());
            }

            File config = new File(getFilesDir(), "mediamtx.yml");
            String yaml =
                "logLevel: info\n" +
                "logDestinations: [stdout]\n" +
                "rtsp: false\nrtmp: true\nhls: false\nwebrtc: false\nsrt: true\nmoq: false\n" +
                "rtmpAddress: :1935\nsrtAddress: :8890\n" +
                "api: true\napiAddress: 127.0.0.1:9997\n" +
                "metrics: true\nmetricsAddress: 127.0.0.1:9998\n" +
                "pprof: false\nplayback: false\n" +
                "pathDefaults:\n  record: false\n  maxReaders: 5\n" +
                "paths:\n  all_others: {}\n";
            try (FileOutputStream out = new FileOutputStream(config)) {
                out.write(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            logFile = new File(getFilesDir(), "mediamtx.log");
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), config.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            engine = pb.start();
            updateNotification("RTMP :1935 • SRT :8890 • Web :8080");
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            updateNotification("MediaMTX start failed: " + detail);
            android.util.Log.e("MSS", "MediaMTX start failed", e);
        }
    }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            if (engine == null || !engine.isAlive()) {
                startEngine();
            }
            handler.postDelayed(this, 5000);
        }
    };

    private void stopEverything() {
        stopping = true;
        handler.removeCallbacks(watchdog);
        if (engine != null) {
            engine.destroy();
            try {
                if (!engine.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) engine.destroyForcibly();
            } catch (Exception ignored) {}
            engine = null;
        }
        if (web != null) {
            web.stopServer();
            web = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (engine != null) {
            engine.destroy();
            engine = null;
        }
        if (web != null) {
            web.stopServer();
            web = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

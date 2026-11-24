package ru.dogmen.lexrf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

final class AppUpdates {
    private static final String BASE = "https://raw.githubusercontent.com/Dog-men361/228er/updates/";
    private static final int MAX_APK = 24 * 1024 * 1024;
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private final Activity activity;
    private final SharedPreferences prefs;

    AppUpdates(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    void check(boolean manual) {
        long now = System.currentTimeMillis();
        if (!manual && (!prefs.getBoolean("auto_update", true)
                || now - prefs.getLong("last_update_check", 0) < CHECK_INTERVAL)) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        if (manual) message("Проверяем обновления…");
        new Thread(() -> {
            try {
                JSONObject update = manifest();
                int versionCode = update.getInt("versionCode");
                String version = update.getString("version");
                String hash = update.getString("sha256").toLowerCase(Locale.ROOT);
                if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !hash.matches("[0-9a-f]{64}")) {
                    throw new IOException("Invalid update metadata");
                }
                PackageManager manager = activity.getPackageManager();
                PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
                if (versionCode <= installed.versionCode) {
                    prefs.edit().putLong("last_update_check", now).apply();
                    if (manual) message("Установлена актуальная версия");
                    return;
                }
                File apk = new File(activity.getCacheDir(), UpdateApkProvider.FILE);
                if (!apk.isFile() || !hash.equals(digest(apk))) download(version, hash, apk);
                PackageInfo candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                if (candidate == null || !installed.packageName.equals(candidate.packageName)
                        || candidate.versionCode != versionCode || !version.equals(candidate.versionName)
                        || installed.signatures == null || installed.signatures.length == 0
                        || !Arrays.equals(installed.signatures, candidate.signatures)) {
                    throw new IOException("APK identity or signature mismatch");
                }
                activity.runOnUiThread(() -> offerInstall(version));
            } catch (Exception e) {
                if (manual) message("Проверка не удалась. Проверьте интернет и повторите.");
            } finally {
                RUNNING.set(false);
            }
        }, "LexRF-updates").start();
    }

    private JSONObject manifest() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        copy("latest.json", buffer, 8192);
        return new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    }

    private void download(String version, String hash, File result) throws Exception {
        File temporary = File.createTempFile("lexrf-", ".apk", activity.getCacheDir());
        try {
            try (OutputStream out = new FileOutputStream(temporary)) {
                copy("LexRF-" + version + ".apk", out, MAX_APK);
            }
            if (!hash.equals(digest(temporary))) throw new IOException("APK checksum mismatch");
            if (!temporary.renameTo(result)) throw new IOException("Cannot save update");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private void copy(String name, OutputStream out, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(BASE + name).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Update source unavailable");
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = new byte[8192];
                int total = 0;
                int size;
                while ((size = in.read(bytes)) != -1) {
                    total += size;
                    if (total > limit) throw new IOException("Update exceeds size limit");
                    out.write(bytes, 0, size);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private String digest(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new DigestInputStream(new FileInputStream(file), sha)) {
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) { }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private void offerInstall(String version) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle("LexRF " + version + " готов к установке")
                .setMessage("APK скачан и проверен. Android попросит подтвердить обновление.")
                .setPositiveButton("Установить", (dialog, button) -> install())
                .setNegativeButton("Позже", (dialog, button) -> prefs.edit()
                        .putLong("last_update_check", System.currentTimeMillis()).apply())
                .show();
    }

    private void install() {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                message("Разрешите установку для LexRF, затем повторите проверку обновлений.");
                return;
            }
            Intent installer = new Intent(Intent.ACTION_VIEW);
            installer.setDataAndType(UpdateApkProvider.APK, UpdateApkProvider.MIME);
            installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installer);
        } catch (RuntimeException e) {
            message("Не удалось открыть установщик Android");
        }
    }

    private void message(String text) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
}

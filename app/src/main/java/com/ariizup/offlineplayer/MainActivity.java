package com.ariizup.offlineplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;

    private int originalSystemUiVisibility;
    private int originalOrientation;

    private static final int FILE_REQUEST_CODE = 1001;
    private static final int MEDIA_PERMISSION_CODE = 1002;
    private static final int ARCHIVE_REQUEST_CODE = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        originalOrientation = getRequestedOrientation();

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(
                android.graphics.Color.BLACK
        );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(
                new MediaBridge(),
                "AndroidMedia"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                    );

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    intent.setType("video/*");

                    intent.putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            true
                    );

                    startActivityForResult(
                            intent,
                            FILE_REQUEST_CODE
                    );

                    return true;

                } catch (Exception e) {

                    fileCallback.onReceiveValue(null);
                    fileCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Tidak bisa membuka pemilih video",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }
            }

            @Override
            public void onShowCustomView(
                    View view,
                    CustomViewCallback callback) {

                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;

                originalSystemUiVisibility =
                        getWindow().getDecorView()
                                .getSystemUiVisibility();

                setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                );

                ViewGroup parent =
                        (ViewGroup) webView.getParent();

                if (parent != null) {
                    parent.removeView(webView);
                }

                fullscreenContainer.removeAllViews();

                fullscreenContainer.addView(
                        customView,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                );

                setContentView(fullscreenContainer);

                Window window = getWindow();

                window.addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                );

                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    // =========================================
    // FULLSCREEN DAN ORIENTASI
    // =========================================

    private void setLandscape(boolean enabled) {
        if (enabled) {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            );
        } else {
            setRequestedOrientation(originalOrientation);
        }
    }

    private void exitFullscreen() {
        if (customView == null) {
            return;
        }

        fullscreenContainer.removeView(customView);
        customView = null;

        setContentView(webView);

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                originalSystemUiVisibility
        );

        setLandscape(false);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    // =========================================
    // IZIN AKSES VIDEO
    // =========================================

    private String getVideoPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && getApplicationInfo().targetSdkVersion >= 33) {
            return "android.permission.READ_MEDIA_VIDEO";
        }

        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasVideoPermission() {
        String permission = getVideoPermission();

        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoPermission() {
        if (hasVideoPermission()) {
            notifyPermissionResult(true);
            return;
        }

        requestPermissions(
                new String[]{getVideoPermission()},
                MEDIA_PERMISSION_CODE
        );
    }

    private void notifyPermissionResult(boolean granted) {
        if (webView == null) {
            return;
        }

        String script =
                "if(window.onNativeMediaPermissionResult){"
                + "window.onNativeMediaPermissionResult("
                + granted + ");}";

        webView.post(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =========================================
    // PEMINDAIAN VIDEO
    // =========================================

    private String scanVideos() {
        JSONArray videos = new JSONArray();

        if (!hasVideoPermission()) {
            return videos.toString();
        }

        Cursor cursor = null;

        try {
            Uri collection =
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE
            };

            cursor = getContentResolver().query(
                    collection,
                    projection,
                    null,
                    null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
            );

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID
                );

                int nameColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DISPLAY_NAME
                );

                int durationColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DURATION
                );

                int sizeColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.SIZE
                );

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long duration = cursor.getLong(durationColumn);
                    long size = cursor.getLong(sizeColumn);

                    Uri videoUri = Uri.withAppendedPath(
                            collection,
                            String.valueOf(id)
                    );

                    JSONObject item = new JSONObject();

                    item.put("id", id);
                    item.put("name", name);
                    item.put("title", name);
                    item.put("duration", duration);
                    item.put("size", size);
                    item.put("uri", videoUri.toString());
                    item.put("url", videoUri.toString());

                    videos.put(item);
                }
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "ARZU_V",
                    "Gagal memindai video",
                    e
            );

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return videos.toString();
    }

    // =========================================
    // PEMILIH ARSIP ZIP / RAR
    // =========================================

    private void openArchivePicker() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_OPEN_DOCUMENT
            );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.setType("*/*");

            intent.putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    new String[]{
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/vnd.rar",
                            "application/x-rar-compressed",
                            "application/octet-stream",
                            "*/*"
                    }
            );

            startActivityForResult(
                    intent,
                    ARCHIVE_REQUEST_CODE
            );

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Tidak bisa membuka pemilih ZIP/RAR",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =========================================
    // PEMBACA ZIP / RAR
    // =========================================

    private boolean isSupportedVideo(String name) {
        if (name == null) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);

        return lower.endsWith(".mp4")
                || lower.endsWith(".m4v")
                || lower.endsWith(".mkv")
                || lower.endsWith(".webm")
                || lower.endsWith(".avi")
                || lower.endsWith(".mov")
                || lower.endsWith(".3gp")
                || lower.endsWith(".ts")
                || lower.endsWith(".mpeg")
                || lower.endsWith(".mpg");
    }

    private String getArchiveError(Exception e) {
        String message = e.getMessage();

        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }

        return "ERROR:" + message;
    }

    private boolean isRarArchive(String uriString) {
        InputStream input = null;

        try {
            input = getContentResolver().openInputStream(
                    Uri.parse(uriString)
            );

            if (input != null) {
                byte[] signature = new byte[7];
                int count = 0;

                while (count < signature.length) {
                    int read = input.read(
                            signature,
                            count,
                            signature.length - count
                    );

                    if (read < 0) {
                        break;
                    }

                    count += read;
                }

                // Signature RAR4 atau RAR5.
                if (count >= 6
                        && signature[0] == 'R'
                        && signature[1] == 'a'
                        && signature[2] == 'r'
                        && signature[3] == '!'
                        && signature[4] == 0x1A
                        && signature[5] == 0x07) {
                    return true;
                }
            }

        } catch (Exception e) {
            android.util.Log.w(
                    "ARZU_V",
                    "Gagal mendeteksi arsip",
                    e
            );

        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }

        String name = getDisplayName(Uri.parse(uriString));

        return name != null
                && name.toLowerCase(Locale.ROOT).endsWith(".rar");
    }

    // Membaca daftar ZIP tanpa mengekstrak seluruh video.
    private String listZipEntries(String uriString) {
        if (isRarArchive(uriString)) {
            return listRarEntries(uriString);
        }

        JSONArray entries = new JSONArray();

        try {
            InputStream input =
                    getContentResolver().openInputStream(
                            Uri.parse(uriString)
                    );

            if (input == null) {
                return "ERROR:Arsip tidak bisa dibuka";
            }

            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;

                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()
                            && isSupportedVideo(entry.getName())) {

                        JSONObject item = new JSONObject();
                        item.put("name", entry.getName());
                        item.put("size", entry.getSize());

                        entries.put(item);
                    }

                    zip.closeEntry();
                }
            }

            return entries.toString();

        } catch (Exception e) {
            return getArchiveError(e);
        }
    }

    // Membaca daftar RAR tanpa mengekstrak seluruh video.
    private String listRarEntries(String uriString) {
        JSONArray entries = new JSONArray();

        try {
            InputStream input =
                    getContentResolver().openInputStream(
                            Uri.parse(uriString)
                    );

            if (input == null) {
                return "ERROR:Arsip RAR tidak bisa dibuka";
            }

            try (Archive archive = new Archive(input)) {
                FileHeader header;

                while ((header = archive.nextFileHeader()) != null) {
                    String name = header.getFileName();

                    if (!header.isDirectory()
                            && isSupportedVideo(name)) {

                        JSONObject item = new JSONObject();
                        item.put("name", name);
                        item.put("size", header.getFullUnpackSize());

                        entries.put(item);
                    }
                }
            }

            return entries.toString();

        } catch (Exception e) {
            return getArchiveError(e);
        }
    }

    // =========================================
    // EKSTRAK SATU EPISODE
    // =========================================

    private File getArchiveCacheDir() throws Exception {
        File cacheDir = new File(
                getCacheDir(),
                "arzu_archive_cache"
        );

        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new Exception("Gagal membuat folder cache");
        }

        return cacheDir;
    }

    private String getSafeExtension(String name) {
        int dot = name.lastIndexOf('.');

        if (dot >= 0) {
            String ext = name.substring(dot).toLowerCase(
                    Locale.ROOT
            );

            if (ext.matches("\\.[a-z0-9]{1,8}")) {
                return ext;
            }
        }

        return ".video";
    }

    private String extractZipEntry(
            String uriString,
            String entryName) {

        if (isRarArchive(uriString)) {
            return extractRarEntry(uriString, entryName);
        }

        if (!isSupportedVideo(entryName)) {
            return "ERROR:Episode tidak valid";
        }

        File output = null;

        try {
            InputStream input =
                    getContentResolver().openInputStream(
                            Uri.parse(uriString)
                    );

            if (input == null) {
                return "ERROR:ZIP tidak bisa dibuka";
            }

            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;

                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()
                            && entry.getName().equals(entryName)) {

                        output = File.createTempFile(
                                "episode_",
                                getSafeExtension(entryName),
                                getArchiveCacheDir()
                        );

                        long total = 0;
                        byte[] buffer = new byte[8192];

                        try (FileOutputStream out =
                                     new FileOutputStream(output)) {

                            int count;

                            while ((count = zip.read(buffer)) != -1) {
                                total += count;

                                if (total > 2147483647L) {
                                    throw new Exception(
                                            "Ukuran episode terlalu besar"
                                    );
                                }

                                out.write(buffer, 0, count);
                            }
                        }

                        return Uri.fromFile(output).toString();
                    }

                    zip.closeEntry();
                }
            }

            return "ERROR:Episode tidak ditemukan dalam ZIP";

        } catch (Exception e) {
            if (output != null) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }

            return getArchiveError(e);
        }
    }

    private String extractRarEntry(
            String uriString,
            String entryName) {

        if (!isSupportedVideo(entryName)) {
            return "ERROR:Episode tidak valid";
        }

        File output = null;

        try {
            InputStream input =
                    getContentResolver().openInputStream(
                            Uri.parse(uriString)
                    );

            if (input == null) {
                return "ERROR:RAR tidak bisa dibuka";
            }

            try (Archive archive = new Archive(input)) {
                FileHeader header;

                while ((header = archive.nextFileHeader()) != null) {
                    String name = header.getFileName();

                    if (!header.isDirectory()
                            && entryName.equals(name)) {

                        output = File.createTempFile(
                                "episode_",
                                getSafeExtension(entryName),
                                getArchiveCacheDir()
                        );

                        try (FileOutputStream out =
                                     new FileOutputStream(output)) {
                            archive.extractFile(header, out);
                        }

                        return Uri.fromFile(output).toString();
                    }
                }
            }

            return "ERROR:Episode tidak ditemukan dalam RAR";

        } catch (Exception e) {
            if (output != null) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }

            return getArchiveError(e);
        }
    }

    // =========================================
    // NAMA FILE
    // =========================================

    private String getDisplayName(Uri uri) {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (column >= 0) {
                    String name = cursor.getString(column);

                    if (name != null) {
                        return name;
                    }
                }
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "ARZU_V",
                    "Gagal membaca nama file",
                    e
            );

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return "Anime.zip";
    }

    // =========================================
    // JAVASCRIPT BRIDGE
    // =========================================

    private class MediaBridge {

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(() -> requestVideoPermission());
        }

        @JavascriptInterface
        public boolean hasPermission() {
            return hasVideoPermission();
        }

        @JavascriptInterface
        public String getVideos() {
            return scanVideos();
        }

        @JavascriptInterface
        public void openZipPicker() {
            runOnUiThread(() -> openArchivePicker());
        }

        @JavascriptInterface
        public String listZipEntries(String uri) {
            return MainActivity.this.listZipEntries(uri);
        }

        @JavascriptInterface
        public String extractZipEntry(
                String uri,
                String entryName) {
            return MainActivity.this.extractZipEntry(
                    uri,
                    entryName
            );
        }

        @JavascriptInterface
        public void setFullscreenOrientation(boolean enabled) {
            runOnUiThread(() -> setLandscape(enabled));
        }

        @JavascriptInterface
        public void setImmersiveMode(boolean enabled) {
            runOnUiThread(() -> {
                if (enabled) {
                    getWindow().getDecorView()
                            .setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            );
                } else {
                    getWindow().getDecorView()
                            .setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_VISIBLE
                            );
                }
            });
        }
    }

    // =========================================
    // HASIL PEMILIHAN FILE
    // =========================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_REQUEST_CODE) {
            if (fileCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData()
                                .getItemAt(i)
                                .getUri();
                    }

                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
            return;
        }

        if (requestCode == ARCHIVE_REQUEST_CODE) {
            if (resultCode != RESULT_OK
                    || data == null
                    || data.getData() == null) {
                return;
            }

            Uri uri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
                // Beberapa penyedia file tidak mendukung izin persisten.
            }

            String uriString = uri.toString();
            String displayName = getDisplayName(uri);

            String script =
                    "if(window.onZipPicked){"
                    + "window.onZipPicked("
                    + JSONObject.quote(uriString) + ","
                    + JSONObject.quote(displayName)
                    + ");}";

            if (webView != null) {
                webView.post(() ->
                        webView.evaluateJavascript(script, null)
                );
            }
        }
    }

    // =========================================
    // HASIL PERMINTAAN IZIN
    // =========================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == MEDIA_PERMISSION_CODE) {
            boolean granted =
                    grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED;

            notifyPermissionResult(granted);

            if (!granted) {
                Toast.makeText(
                        this,
                        "Izin video belum diberikan",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // =========================================
    // TOMBOL KEMBALI
    // =========================================

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    // =========================================
    // PEMBERSIHAN
    // =========================================

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (webView != null) {
            webView.removeJavascriptInterface("AndroidMedia");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}

package com.ariizup.offlineplayer;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.database.Cursor;
import android.provider.MediaStore;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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

            // PEMILIH VIDEO MANUAL

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

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

                try {
                    startActivityForResult(
                            intent,
                            FILE_REQUEST_CODE
                    );

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

                return true;
            }

            // MASUK FULLSCREEN

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

                View decorView = window.getDecorView();

                int flags =
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

                decorView.setSystemUiVisibility(flags);
            }

            // KELUAR FULLSCREEN

            @Override
            public void onHideCustomView() {

                if (customView == null) {
                    return;
                }

                fullscreenContainer.removeView(customView);

                customView = null;

                setContentView(webView);

                Window window = getWindow();

                window.clearFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                );

                window.getDecorView().setSystemUiVisibility(
                        originalSystemUiVisibility
                );

                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        });

        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    // =====================================
    // IZIN AKSES VIDEO
    // =====================================

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

    // =====================================
    // PEMINDAIAN VIDEO
    // =====================================

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

                    long duration =
                            cursor.getLong(durationColumn);

                    long size =
                            cursor.getLong(sizeColumn);

                    Uri videoUri = Uri.withAppendedPath(
                            collection,
                            String.valueOf(id)
                    );

                    JSONObject item = new JSONObject();

                    item.put("id", id);
                    item.put("name", name);
                    item.put("duration", duration);
                    item.put("size", size);
                    item.put("uri", videoUri.toString());

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

    private void notifyPermissionResult(boolean granted) {

        if (webView == null) {
            return;
        }

        String script =
                "if(window.onNativeMediaPermissionResult){" +
                "window.onNativeMediaPermissionResult(" +
                granted + ");}";

        webView.post(() ->
                webView.evaluateJavascript(script, null)
        );
    }

    // =====================================
    // JAVASCRIPT BRIDGE
    // =====================================

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
    }

    // =====================================
    // HASIL PERMINTAAN IZIN
    // =====================================

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

    // =====================================
    // HASIL PEMILIH VIDEO MANUAL
    // =====================================

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

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }

                } else if (data.getData() != null) {

                    results = new Uri[]{
                            data.getData()
                    };
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    // =====================================
    // TOMBOL BACK
    // =====================================

    @Override
    public void onBackPressed() {

        if (customView != null) {
            if (webView != null) {
                webView.evaluateJavascript(
                        "document.exitFullscreen && document.exitFullscreen();",
                        null
                );
            }

            onHideFullscreenFallback();
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void onHideFullscreenFallback() {

        if (customView != null) {

            fullscreenContainer.removeView(customView);
            customView = null;

            setContentView(webView);

            Window window = getWindow();

            window.clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );

            window.getDecorView().setSystemUiVisibility(
                    originalSystemUiVisibility
            );

            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        }
    }

    // =====================================
    // PENUTUPAN APLIKASI
    // =====================================

    @Override
    protected void onDestroy() {

        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (customView != null) {
            onHideFullscreenFallback();
        }

        if (webView != null) {
            webView.removeJavascriptInterface("AndroidMedia");
            webView.destroy();
        }

        super.onDestroy();
    }
            }

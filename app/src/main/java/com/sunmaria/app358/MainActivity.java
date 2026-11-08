package com.sunmaria.app358;

import android.Manifest;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.messaging.FirebaseMessaging;
import com.startapp.sdk.adsbase.StartAppSDK;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String[] PHONES = {"08064373872", "08111345495", "08020576051"};
    private static final String EMAIL = "i.zinoconcept@gmail.com";

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private Toolbar toolbar;
    private AdManager adManager;

    private String failedUrl;

    // File chooser (<input type="file">)
    private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri[] uris = null;
                if (filePathCallback != null) {
                    uris = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
                    filePathCallback.onReceiveValue(uris);
                    filePathCallback = null;
                }
            });

    // Runtime permissions requested on behalf of the website
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private PermissionRequest pendingWebPermission;
    private String[] pendingDownload; // url, userAgent, contentDisposition, mimeType

    private final ActivityResultLauncher<String[]> geoPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                boolean ok = Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (pendingGeoCallback != null) {
                    pendingGeoCallback.invoke(pendingGeoOrigin, ok, false);
                    pendingGeoCallback = null;
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (pendingWebPermission != null) {
                    if (granted) pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    else pendingWebPermission.deny();
                    pendingWebPermission = null;
                }
            });

    private final ActivityResultLauncher<String> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingDownload != null) {
                    startDownload(pendingDownload[0], pendingDownload[1], pendingDownload[2], pendingDownload[3]);
                } else if (!granted) {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
                pendingDownload = null;
            });

    private final ActivityResultLauncher<String> notificationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start.io (StartApp) SDK – must run before setContentView()
        StartAppSDK.init(this, BuildConfig.STARTAPP_APP_ID);

        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG);

        if (BuildConfig.DISALLOW_SCREENSHOT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.activity_main);

        // Edge-to-edge safe layout (targetSdk 35)
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        progress = findViewById(R.id.progress);
        swipe = findViewById(R.id.swipe);
        webView = findViewById(R.id.webview);

        setupWebView();
        setupSwipeRefresh();
        setupBackHandling();
        createNotificationChannel();
        setupPush();

        adManager = new AdManager(this);
        adManager.init(findViewById(R.id.banner_container));

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(resolveStartUrl(getIntent()));
        }
    }

    // ------------------------------------------------------------------ WebView

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl(), request.isForMainFrame());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                if (url != null && url.startsWith("https://")) {
                    adManager.maybeShowInterstitial();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflinePage(request.getUrl().toString());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (BuildConfig.LIVE_TOOLBAR_TITLE && title != null && !title.isEmpty()
                        && !title.startsWith("data:") && !title.startsWith("about:")) {
                    toolbar.setTitle(title);
                } else if (!BuildConfig.LIVE_TOOLBAR_TITLE) {
                    toolbar.setTitle(R.string.app_name);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    fileChooserLauncher.launch(params.createIntent());
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!isOurOrigin(Uri.parse(origin))) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoCallback = callback;
                    pendingGeoOrigin = origin;
                    geoPermLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsVideo = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsVideo = true;
                    }
                    // Camera only (RECORD_AUDIO is not enabled for this app), our own origin only
                    if (!wantsVideo || !isOurOrigin(request.getOrigin())) {
                        request.deny();
                    } else if (hasPermission(Manifest.permission.CAMERA)) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingWebPermission = request;
                        cameraPermLauncher.launch(Manifest.permission.CAMERA);
                    }
                });
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                pendingDownload = new String[]{url, userAgent, contentDisposition, mimeType};
                storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                startDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipe.setEnabled(BuildConfig.PULL_TO_REFRESH);
        swipe.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark);
        swipe.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipe.setOnRefreshListener(this::refreshPage);
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    confirmExit();
                }
            }
        });
    }

    private void refreshPage() {
        String current = webView.getUrl();
        if (failedUrl != null && (current == null || !current.startsWith("http"))) {
            webView.loadUrl(failedUrl);
        } else {
            webView.reload();
        }
    }

    private void showOfflinePage(String url) {
        failedUrl = url;
        String html = readAsset("offline.html")
                .replace("%URL%", url.replace("'", "%27").replace("\"", "%22")
                        .replace("<", "%3C").replace(">", "%3E").replace("\\", "%5C"));
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        swipe.setRefreshing(false);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<html><body><h3>Offline</h3></body></html>";
        }
    }

    // ------------------------------------------------------------------ URL handling

    private boolean isOurOrigin(@Nullable Uri uri) {
        return uri != null && BuildConfig.HOME_HOST.equalsIgnoreCase(uri.getHost());
    }

    /** @return true if the app handled the URL, false to let the WebView load it. */
    private boolean handleUrl(Uri uri, boolean mainFrame) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

        if (scheme.equals("http") && BuildConfig.HTTPS_ONLY) {
            // HTTPS only: upgrade the link, then apply the normal checks
            uri = uri.buildUpon().scheme("https").build();
            scheme = "https";
            if (isOurOrigin(uri) && mainFrame) {
                webView.loadUrl(uri.toString());
                return true;
            }
        }

        if (scheme.equals("https") || scheme.equals("http")) {
            if (isOurOrigin(uri) || !mainFrame) return false; // stay in the app / sub-resources
            final Uri target = uri;
            if (BuildConfig.EXT_LINK_CHECK) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.external_link_title)
                        .setMessage(getString(R.string.external_link_message, target.toString()))
                        .setPositiveButton(R.string.open, (d, w) -> openExternally(target))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                openExternally(target);
            }
            return true;
        }

        // tel:, mailto:, sms:, geo:, whatsapp: and any other scheme go to the system, never the WebView
        openExternally(uri);
        return true;
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private String resolveStartUrl(@Nullable Intent intent) {
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && "https".equals(data.getScheme()) && isOurOrigin(data)) {
                return data.toString();
            }
            String extra = intent.getStringExtra("url"); // from push notification data
            if (extra != null) {
                Uri u = Uri.parse(extra);
                if ("https".equals(u.getScheme()) && isOurOrigin(u)) return extra;
            }
        }
        return BuildConfig.HOME_URL;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        webView.loadUrl(resolveStartUrl(intent));
    }

    // ------------------------------------------------------------------ Downloads

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            req.addRequestHeader("User-Agent", userAgent);
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            req.setTitle(name);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openExternally(Uri.parse(url));
        }
    }

    // ------------------------------------------------------------------ Push / notifications

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(getString(R.string.notification_channel_id),
                    getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void setupPush() {
        // Ask the user (Android 13+) – "AskUser" in the original push config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all");
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_home) { webView.loadUrl(BuildConfig.HOME_URL); return true; }
        if (id == R.id.nav_about) { webView.loadUrl(BuildConfig.HOME_URL + "about.html"); return true; }
        if (id == R.id.nav_services) { webView.loadUrl(BuildConfig.HOME_URL + "services.html"); return true; }
        if (id == R.id.nav_projects) { webView.loadUrl(BuildConfig.HOME_URL + "projects.html"); return true; }
        if (id == R.id.nav_resources) { webView.loadUrl(BuildConfig.HOME_URL + "blog.html"); return true; }
        if (id == R.id.nav_contact) { webView.loadUrl(BuildConfig.HOME_URL + "contact.html"); return true; }
        if (id == R.id.action_privacy) { webView.loadUrl(BuildConfig.HOME_URL + "privacy.html"); return true; }
        if (id == R.id.action_terms) { webView.loadUrl(BuildConfig.HOME_URL + "terms.html"); return true; }
        if (id == R.id.action_refresh) { refreshPage(); return true; }
        if (id == R.id.action_call) { showCallDialog(); return true; }
        if (id == R.id.action_email) { sendEmail(); return true; }
        if (id == R.id.action_share) { shareApp(); return true; }
        if (id == R.id.action_about_app) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.about_text) + "\n\nVersion " + BuildConfig.VERSION_NAME)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return true;
        }
        if (id == R.id.action_exit) { confirmExit(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void showCallDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_number)
                .setItems(PHONES, (d, which) -> openExternally(Uri.parse("tel:" + PHONES[which])))
                .show();
    }

    private void sendEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text) + "\n" + BuildConfig.HOME_URL);
        startActivity(Intent.createChooser(i, getString(R.string.share_via)));
    }

    private void confirmExit() {
        if (!BuildConfig.CONFIRM_EXIT) { finish(); return; }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_text)
                .setPositiveButton(R.string.exit, (d, w) -> finish())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ Lifecycle

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override protected void onResume() { super.onResume(); webView.onResume(); if (adManager != null) adManager.resume(); }
    @Override protected void onPause() { if (adManager != null) adManager.pause(); webView.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        if (adManager != null) adManager.destroy();
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        webView.destroy();
        super.onDestroy();
    }
}

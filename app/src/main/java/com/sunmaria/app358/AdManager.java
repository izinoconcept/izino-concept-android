package com.sunmaria.app358;

import android.app.Activity;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

/**
 * Ads for I.ZINO CONCEPT.
 *
 *  - Banner: AdMob first. If AdMob has no banner, a Start.io banner is shown instead.
 *  - Interstitial: at most one per BuildConfig.INTERSTITIAL_INTERVAL_MS, shown when a page finishes loading.
 *  - Start.io is initialised by Start.io's own provider from the manifest App ID (no init call here).
 */
public class AdManager {
    private static final String TAG = "AdManager";
    private static final long FIRST_AD_DELAY_MS = 20000L;

    // ------------------------------------------------------------------ SWITCHES
    /**
     * Start.io full-screen (interstitial) ads:
     *   0 = off
     *   1 = backup only: used only when AdMob has no interstitial ready   (recommended)
     *   2 = alternate with AdMob (every second interstitial is Start.io)
     */
    private static final int STARTAPP_INTERSTITIAL_MODE = 1;

    /** Show a Start.io banner when the AdMob banner fails to load. */
    private static final boolean STARTAPP_BANNER_FALLBACK = true;

    /**
     * Type of Start.io full-screen ad to request.
     *   FULLPAGE  = standard full-page interstitial, NOT video   (recommended here)
     *   AUTOMATIC = Start.io picks whatever earns most, including VIDEO interstitials
     *               (your analytics showed all Start.io revenue came from "Video Interstitial")
     */
    private static final StartAppAd.AdMode STARTAPP_AD_MODE = StartAppAd.AdMode.FULLPAGE;

    /**
     * TESTING ONLY: AdMob is switched off and only Start.io banner + interstitial are used.
     * Use with a DEBUG build (Start.io then serves its test ads). Must be false for release.
     */
    private static final boolean STARTAPP_ONLY_TEST = false;

    /** TEMPORARY: pop-up naming the network just before each full-screen ad. */
    private static final boolean SHOW_NETWORK_TOAST = false;
    // ---------------------------------------------------------------------------

    private static final boolean ENABLE_STARTAPP = STARTAPP_INTERSTITIAL_MODE > 0 || STARTAPP_ONLY_TEST;

    private final Activity activity;
    private ViewGroup bannerContainer;
    private AdView bannerView;
    private Banner startAppBanner;

    private InterstitialAd admobInterstitial;
    private boolean admobLoading;

    private StartAppAd startAppAd;
    private boolean startAppReady;
    private boolean startAppLoading;
    private boolean startAppTurn = false; // used by mode 2

    private long lastShown;

    public AdManager(Activity activity) {
        this.activity = activity;
        // First interstitial becomes eligible after FIRST_AD_DELAY_MS, then every INTERSTITIAL_INTERVAL_MS
        long head = Math.max(0L, BuildConfig.INTERSTITIAL_INTERVAL_MS - FIRST_AD_DELAY_MS);
        lastShown = SystemClock.elapsedRealtime() - head;
    }

    public void init(ViewGroup container) {
        this.bannerContainer = container;
        MobileAds.initialize(activity, status -> { });
        if (STARTAPP_ONLY_TEST) {
            showStartAppBanner();
        } else {
            loadBanner();
            loadAdmobInterstitial();
        }
        if (ENABLE_STARTAPP) loadStartApp();
    }

    private void report(String what, @Nullable LoadAdError error) {
        String msg = what + " failed" + (error == null ? "" : ": code=" + error.getCode() + " " + error.getMessage());
        Log.w(TAG, msg);
        if (BuildConfig.DEBUG) {
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- banners
    private void loadBanner() {
        bannerContainer.post(() -> {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            float widthPx = bannerContainer.getWidth() > 0 ? bannerContainer.getWidth() : dm.widthPixels;
            int widthDp = (int) (widthPx / dm.density);
            bannerView = new AdView(activity);
            bannerView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            bannerView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            bannerView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    hideStartAppBanner();
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    report("AdMob banner", error);
                    showStartAppBanner();
                }
            });
            bannerContainer.removeAllViews();
            bannerContainer.addView(bannerView);
            bannerView.loadAd(new AdRequest.Builder().build());
        });
    }

    /** Start.io banner (com.startapp.sdk.ads.banner.Banner) added on top of the empty AdMob banner. */
    private void showStartAppBanner() {
        if (!(STARTAPP_BANNER_FALLBACK || STARTAPP_ONLY_TEST)) return;
        if (startAppBanner != null || bannerContainer == null) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            startAppBanner = new Banner(activity);
            ViewGroup.LayoutParams lp;
            if (bannerContainer instanceof FrameLayout) {
                lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL);
            } else {
                lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            bannerContainer.addView(startAppBanner, lp);
            Log.i(TAG, "Start.io banner added");
        } catch (Throwable t) {
            Log.w(TAG, "Start.io banner failed", t);
            startAppBanner = null;
        }
    }

    private void hideStartAppBanner() {
        if (startAppBanner != null && bannerContainer != null) {
            bannerContainer.removeView(startAppBanner);
            startAppBanner = null;
        }
    }

    // ---------------------------------------------------------------- AdMob interstitial
    private void loadAdmobInterstitial() {
        if (admobLoading || admobInterstitial != null) return;
        admobLoading = true;
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        admobInterstitial = ad;
                        admobLoading = false;
                        Log.i(TAG, "AdMob interstitial loaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        admobInterstitial = null;
                        admobLoading = false;
                        report("AdMob interstitial", error);
                    }
                });
    }

    // ---------------------------------------------------------------- Start.io interstitial
    private void loadStartApp() {
        if (startAppLoading || startAppReady) return;
        startAppLoading = true;
        final StartAppAd ad = new StartAppAd(activity);
        ad.loadAd(STARTAPP_AD_MODE, new AdEventListener() {
            @Override
            public void onReceiveAd(@NonNull Ad received) {
                startAppAd = ad;
                startAppReady = true;
                startAppLoading = false;
                Log.i(TAG, "Start.io interstitial loaded");
            }

            @Override
            public void onFailedToReceiveAd(@Nullable Ad failed) {
                startAppAd = null;
                startAppReady = false;
                startAppLoading = false;
                Log.w(TAG, "Start.io interstitial failed to load");
                if (BuildConfig.DEBUG) {
                    Toast.makeText(activity, "Start.io interstitial failed to load", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void label(String network) {
        if (SHOW_NETWORK_TOAST) {
            Toast.makeText(activity, "Ad network: " + network, Toast.LENGTH_LONG).show();
        }
    }

    /** Call after a page finishes loading. Shows at most one interstitial per interval. */
    public void maybeShowInterstitial() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastShown < BuildConfig.INTERSTITIAL_INTERVAL_MS) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        boolean admobReady = !STARTAPP_ONLY_TEST && admobInterstitial != null;
        boolean startReady = ENABLE_STARTAPP && startAppReady && startAppAd != null;

        boolean useStartApp;
        if (STARTAPP_ONLY_TEST) {
            useStartApp = startReady;
        } else if (STARTAPP_INTERSTITIAL_MODE == 2) {
            useStartApp = startReady && (startAppTurn || !admobReady);
        } else {
            useStartApp = startReady && !admobReady; // mode 1: backup only
        }

        if (useStartApp) {
            StartAppAd ad = startAppAd;
            startAppAd = null;
            startAppReady = false;
            lastShown = now;
            startAppTurn = false;
            try {
                Log.i(TAG, "SHOWING Start.io interstitial");
                label("Start.io");
                ad.showAd();
            } catch (Throwable t) {
                Log.w(TAG, "Start.io show failed", t);
            }
            loadStartApp();
        } else if (admobReady) {
            InterstitialAd ad = admobInterstitial;
            admobInterstitial = null;
            lastShown = now;
            startAppTurn = true;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadAdmobInterstitial();
                }
            });
            Log.i(TAG, "SHOWING AdMob interstitial");
            label("AdMob");
            ad.show(activity);
        } else {
            // Nothing ready yet: try to load for next time
            if (!STARTAPP_ONLY_TEST) loadAdmobInterstitial();
            if (ENABLE_STARTAPP) loadStartApp();
        }
    }

    public void pause() { if (bannerView != null) bannerView.pause(); }
    public void resume() { if (bannerView != null) bannerView.resume(); }
    public void destroy() { if (bannerView != null) bannerView.destroy(); }
}

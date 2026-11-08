package com.sunmaria.app358;

import android.app.Activity;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
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
import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

public class AdManager {
    private static final String TAG = "AdManager";
    private static final long FIRST_AD_DELAY_MS = 20000L;

    private final Activity activity;
    private AdView bannerView;

    private InterstitialAd admobInterstitial;
    private boolean admobLoading;

    private StartAppAd startAppAd;
    private boolean startAppReady;
    private boolean startAppLoading;

    private long lastShown;

    public AdManager(Activity activity) {
        this.activity = activity;
        // First interstitial becomes eligible after FIRST_AD_DELAY_MS, then every INTERSTITIAL_INTERVAL_MS
        long head = Math.max(0L, BuildConfig.INTERSTITIAL_INTERVAL_MS - FIRST_AD_DELAY_MS);
        lastShown = SystemClock.elapsedRealtime() - head;
    }

    public void init(ViewGroup bannerContainer) {
        MobileAds.initialize(activity, status -> { });
        loadBanner(bannerContainer);
        loadAdmobInterstitial();
        loadStartApp();
    }

    private void report(String what, @Nullable LoadAdError error) {
        String msg = what + " failed" + (error == null ? "" : ": code=" + error.getCode() + " " + error.getMessage());
        Log.w(TAG, msg);
        if (BuildConfig.DEBUG) {
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void loadBanner(ViewGroup container) {
        container.post(() -> {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            float widthPx = container.getWidth() > 0 ? container.getWidth() : dm.widthPixels;
            int widthDp = (int) (widthPx / dm.density);
            bannerView = new AdView(activity);
            bannerView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            bannerView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            bannerView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    report("AdMob banner", error);
                }
            });
            container.removeAllViews();
            container.addView(bannerView);
            bannerView.loadAd(new AdRequest.Builder().build());
        });
    }

    // ---------------- AdMob interstitial ----------------
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

    // ---------------- Start.io interstitial ----------------
    private void loadStartApp() {
        if (startAppLoading || startAppReady) return;
        startAppLoading = true;
        final StartAppAd ad = new StartAppAd(activity);
        ad.loadAd(new AdEventListener() {
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

    /** Call after a page finishes loading. Shows at most one interstitial per interval. */
    public void maybeShowInterstitial() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastShown < BuildConfig.INTERSTITIAL_INTERVAL_MS) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        if (admobInterstitial != null) {
            InterstitialAd ad = admobInterstitial;
            admobInterstitial = null;
            lastShown = now;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadAdmobInterstitial();
                }
            });
            ad.show(activity);
        } else if (startAppReady && startAppAd != null) {
            StartAppAd ad = startAppAd;
            startAppAd = null;
            startAppReady = false;
            lastShown = now;
            try {
                ad.showAd();
            } catch (Throwable t) {
                Log.w(TAG, "Start.io show failed", t);
            }
            loadStartApp();
        } else {
            // Nothing ready yet: try to load both for next time
            loadAdmobInterstitial();
            loadStartApp();
        }
    }

    public void pause() { if (bannerView != null) bannerView.pause(); }
    public void resume() { if (bannerView != null) bannerView.resume(); }
    public void destroy() { if (bannerView != null) bannerView.destroy(); }
}

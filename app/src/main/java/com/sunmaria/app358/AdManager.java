package com.sunmaria.app358;

import android.app.Activity;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.startapp.sdk.adsbase.StartAppAd;

/**
 * AdMob banner + interstitial (min. BuildConfig.INTERSTITIAL_INTERVAL_MS between interstitials).
 * Start.io is initialised in MainActivity and registered as an AdMob mediation network;
 * it is also used as a direct fallback when no AdMob interstitial is ready.
 */
public class AdManager {
    private static final String TAG = "AdManager";

    private final Activity activity;
    private AdView bannerView;
    private InterstitialAd interstitial;
    private boolean loading;
    private long lastShown = SystemClock.elapsedRealtime(); // first interstitial after one interval

    public AdManager(Activity activity) {
        this.activity = activity;
    }

    public void init(ViewGroup bannerContainer) {
        MobileAds.initialize(activity, status -> { });
        loadBanner(bannerContainer);
        loadInterstitial();
    }

    private void loadBanner(ViewGroup container) {
        container.post(() -> {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            float widthPx = container.getWidth() > 0 ? container.getWidth() : dm.widthPixels;
            int widthDp = (int) (widthPx / dm.density);
            bannerView = new AdView(activity);
            bannerView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            bannerView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            container.removeAllViews();
            container.addView(bannerView);
            bannerView.loadAd(new AdRequest.Builder().build());
        });
    }

    private void loadInterstitial() {
        if (loading || interstitial != null) return;
        loading = true;
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitial = ad;
                        loading = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Log.w(TAG, "Interstitial failed: " + error.getMessage());
                        interstitial = null;
                        loading = false;
                    }
                });
    }

    /** Call after a page finishes loading. Shows at most one interstitial per interval. */
    public void maybeShowInterstitial() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastShown < BuildConfig.INTERSTITIAL_INTERVAL_MS) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        if (interstitial != null) {
            InterstitialAd ad = interstitial;
            interstitial = null;
            lastShown = now;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitial();
                }
            });
            ad.show(activity);
        } else {
            loadInterstitial();
            try {
                if (StartAppAd.showAd(activity)) {
                    lastShown = now;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Start.io fallback failed", t);
            }
        }
    }

    public void pause() { if (bannerView != null) bannerView.pause(); }
    public void resume() { if (bannerView != null) bannerView.resume(); }
    public void destroy() { if (bannerView != null) bannerView.destroy(); }
}

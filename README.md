# I.ZINO CONCEPT – Android app

Native Android wrapper for **https://izinoconcept.github.io**, rebuilt from the
`I_ZINO_CONCEPT.webapp` (Website 2 APK Builder) project as a full Gradle project that builds on GitHub Actions.

- Package: `com.sunmaria.app358` (matches `google-services.json`)
- Version: 3.1 (versionCode 4; the old webapp build was 3.0 / 3)
- Min SDK 23, target SDK 35, Java 17, AGP 8.5.2

## Build on GitHub
1. Create a repository and push this folder (see bottom).
2. Open **Actions → Build I.ZINO CONCEPT APK → Run workflow** (it also runs on every push).
3. Download the APK from the run's **Artifacts**.

### Signed release / Play Store (optional)
Add these repository **secrets**: `KEYSTORE_BASE64` (`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
The workflow then also produces a signed release APK + AAB. Pushing a tag such as `v3.1` attaches them to a GitHub Release.
Use the **same keystore** as the existing published app, otherwise Android will not treat it as an update.

### AdMob App ID
`app/build.gradle` defaults to the real AdMob *App* ID (`ca-app-pub-4163326546433333~6128040408`). Override it with a repository **variable** named `ADMOB_APP_ID` if it changes.
Debug builds always use Google's test ad units; release builds use the real units.

## Features carried over from the .webapp file
Splash 1.8 s · amber Material theme (#F9A825/#C17900/#FFD95A) · toolbar with live page title and Home button · progress bar ·
pull-to-refresh · pinch zoom (no zoom buttons) · text selection · hidden scrollbars · HTTPS-only · confirm before opening external links ·
confirm before exit ("We are always on TIME…") · About / Share texts · orientation auto · AdMob banner + interstitial every 60 s ·
location, camera, file upload and download support · FCM push notifications (asks permission on Android 13+).

## Added
Offline error page with retry · deep links for izinoconcept.github.io · tel/mailto/WhatsApp handling · menu shortcuts (About Us, Services, Projects, Resources, Contact, Call, Email, Privacy, Terms) ·
Firebase Analytics · edge-to-edge support for Android 15 · notification tap opens a `url` sent in the push data.

## Ads
- **Start.io (manifest method, per Start.io's "Integration via Maven" guide):** the App ID `208869870` is set with `com.startapp.sdk.APPLICATION_ID` in `AndroidManifest.xml`. Start.io's own provider (`StartAppInitProvider`) starts the SDK on every launch, so there is **no** `StartAppSDK.init(...)` call and the provider is **not** removed (the guide says to use only one initialisation method).
- Return ads and the Start.io splash ad are switched off with `RETURN_ADS_ENABLED` / `SPLASH_ENABLED`; the app has its own splash screen. Start.io's disclosure/consent box stays at its default (on).
- Interstitials alternate between AdMob and Start.io (`AdManager`), at most one per 60 s, shown when a page finishes loading. Debug builds use AdMob test units and Start.io test ads (`StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)`).
- AdMob banner at the bottom. The AdMob mediation adapter (`io.start:admob-mediation`) is **not** used; remove any Start.io custom-event rows from the AdMob mediation groups.
- ProGuard rules from the guide are in `app/proguard-rules.pro` (only needed if you turn on `minifyEnabled`).

## Settings in `app/build.gradle`
`HOME_URL`, `SPLASH_DURATION_MS`, `INTERSTITIAL_INTERVAL_MS`, `HTTPS_ONLY`, `EXT_LINK_CHECK`, `CONFIRM_EXIT`, `PULL_TO_REFRESH`, `DISALLOW_SCREENSHOT`, …

## Local build
Open in Android Studio, or install Gradle 8.7 and run `gradle assembleDebug`.
To add a wrapper: `gradle wrapper --gradle-version 8.7`.

## Push to GitHub
```
git remote add origin https://github.com/<you>/izino-concept-android.git
git push -u origin main
```

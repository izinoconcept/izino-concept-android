# Release builds currently run with minifyEnabled false.
# If you enable R8, keep the ad SDKs:
-keep class com.startapp.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.startapp.**

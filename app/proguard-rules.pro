# Release builds currently run with minifyEnabled false.
# If you enable R8/ProGuard, these Start.io rules are mandatory (Start.io guide, Step 2):
-keep class com.startapp.** {
    *;
}
-keep class com.truenet.** {
    *;
}
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile,
    LineNumberTable, *Annotation*, EnclosingMethod
-dontwarn android.webkit.JavascriptInterface
-dontwarn com.startapp.**
-dontwarn org.jetbrains.annotations.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }

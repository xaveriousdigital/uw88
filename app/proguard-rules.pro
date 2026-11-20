# Preserve line number information for Play Console crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep WebView JS/callbacks safe
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}

# Keep Kotlin Coroutines & Coil cleanly
-dontwarn kotlinx.coroutines.**
-dontwarn okio.**

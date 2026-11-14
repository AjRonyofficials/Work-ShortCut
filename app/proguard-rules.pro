# R8 / ProGuard Hardening & Obfuscation Rules against MT Manager / NP Manager / Jadx
-optimizationpasses 5
-overloadaggressively
-repackageclasses 'o'
-allowaccessmodification
-renamesourcefileattribute SourceFile

# Strip verbose Kotlin Debug Metadata and source file names to hinder decompilation
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
}

# Keep Native JNI classes & methods for SuperProxyVpnService (hev-socks5-tunnel / tun2socks)
-keep class hev.htproxy.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep AndroidManifest entry points (Activities, Services, Receivers, Workers)
-keep public class com.example.MainActivity
-keep public class com.example.util.ClipboardReaderActivity
-keep public class com.example.util.CopyOtpReceiver
-keep public class com.example.service.FloatingOverlayService
-keep public class com.example.service.SuperProxyVpnService
-keep public class com.example.service.AutoCleanAccessibilityService
-keep class com.example.worker.** { *; }

# Keep Room Database entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Kotlin Serialization & Parcelable models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Suppress harmless OkHttp / Conscrypt / BouncyCastle warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

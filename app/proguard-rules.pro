# R8 / ProGuard Hardening & Obfuscation Rules against MT Manager / NP Manager / JADX / Reverse Engineering
-dontoptimize
-overloadaggressively
-repackageclasses 'o'
-allowaccessmodification
-renamesourcefileattribute SourceFile
-dontwarn androidx.compose.ui.tooling.**

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

# Keep Room Database entities, DAOs, and generated *_Impl classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Moshi / Retrofit models & service interfaces
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * implements com.squareup.moshi.JsonAdapter { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations

# Keep Kotlin Serialization & Parcelable models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep AndroidX WorkManager / Lifecycle reflection constructors
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Suppress harmless OkHttp / Retrofit / Conscrypt / BouncyCastle / Kotlin warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Metadata

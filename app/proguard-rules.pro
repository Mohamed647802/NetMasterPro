# ============================================================
# NetMaster Pro — ProGuard Rules
# ============================================================

# ---- Keep line numbers for crash reports --------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin metadata (required for reflection/serialization) ----
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ================================================================
# KOTLINX SERIALIZATION
# ================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class com.netmaster.pro.**$$serializer { *; }
-keepclassmembers class com.netmaster.pro.** {
    *** Companion;
}
-keepclasseswithmembers class com.netmaster.pro.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ================================================================
# HILT / DAGGER
# ================================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

-keep class **_HiltModules* { *; }
-keep class **_MembersInjector* { *; }
-keep class **_Factory* { *; }
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
    @dagger.* <fields>;
    @dagger.* <methods>;
}

# ================================================================
# ROOM
# ================================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ================================================================
# RETROFIT + OKHTTP
# ================================================================
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ================================================================
# JSOUP
# ================================================================
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ================================================================
# GOOGLE MLKIT
# ================================================================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-dontwarn com.google.mlkit.**

# ================================================================
# COIL IMAGE LOADING
# ================================================================
-keep class coil.** { *; }
-dontwarn coil.**

# ================================================================
# GOOGLE ACCOMPANIST
# ================================================================
-keep class com.google.accompanist.** { *; }
-dontwarn com.google.accompanist.**

# ================================================================
# ANDROIDX WORK MANAGER
# ================================================================
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ================================================================
# ANDROIDX SECURITY CRYPTO
# ================================================================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ================================================================
# GLANCE APPWIDGET
# ================================================================
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# ================================================================
# BIOMETRIC
# ================================================================
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ================================================================
# NAVIGATION COMPOSE
# ================================================================
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ================================================================
# APP MODEL CLASSES — keep all data/domain models
# ================================================================
-keep class com.netmaster.pro.data.model.** { *; }
-keep class com.netmaster.pro.domain.adapter.** { *; }
-keep class com.netmaster.pro.data.local.** { *; }

# ================================================================
# GENERAL ANDROID
# ================================================================
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Application { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class * extends android.appwidget.AppWidgetProvider { *; }

# Keep Parcelable creators
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep R class fields referenced in code
-keepclassmembers class **.R$* { public static <fields>; }

# Suppress warnings from third-party libs
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn sun.misc.Unsafe

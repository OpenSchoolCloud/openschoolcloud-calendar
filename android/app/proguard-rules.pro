# OpenSchoolCloud Calendar ProGuard Rules

# Keep model classes (for Room and serialization)
-keep class nl.openschoolcloud.calendar.domain.model.** { *; }
-keep class nl.openschoolcloud.calendar.data.local.entity.** { *; }

# ical4j
-dontwarn net.fortuna.ical4j.**
-keep class net.fortuna.ical4j.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose
-keep class androidx.compose.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Stack traces - keep source file and line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ACRA crash reporting
-dontwarn org.acra.**
-keep class org.acra.** { *; }

# Moshi
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ZXing
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

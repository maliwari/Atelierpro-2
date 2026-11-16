# Room & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Data Entities & Gson TypeConverters models
-keep class com.atelierpro.couture.data.** { *; }
-keepclassmembers class com.atelierpro.couture.data.** { *; }

# Gson generic signatures & annotations
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# OkHttp / Okio / SyncManager
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep class com.atelierpro.couture.sync.** { *; }
-keepclassmembers class com.atelierpro.couture.sync.** { *; }

# ProGuard & R8 configuration for Paper Trail

# SQLCipher native and openhelper rules
-keep class net.zetetic.database.** { *; }
-keepclassmembers class * extends net.zetetic.database.sqlcipher.SQLiteOpenHelper { *; }
-keep class androidx.sqlite.db.** { *; }
-dontwarn net.zetetic.**

# ML Kit Text Recognition — must cover the whole com.google.mlkit tree, not just
# .vision: the component/DI registrar (com.google.mlkit.common.sdkinternal.*) is
# reflection-driven and crashes at startup ("Unsatisfied dependency for component")
# if R8 renames or strips it. Same for the internal Play Services support code
# ML Kit's registrar reaches into (com.google.android.gms.internal.mlkit_*,
# already-obfuscated by Google, not meant to be re-obfuscated or tree-shaken here).
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.internal.**

# Room Database & Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Biometric & Security Crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

# ProGuard & R8 configuration for Paper Trail

# SQLCipher native and openhelper rules
-keep class net.zetetic.database.** { *; }
-keepclassmembers class * extends net.zetetic.database.sqlcipher.SQLiteOpenHelper { *; }
-keep class androidx.sqlite.db.** { *; }
-dontwarn net.zetetic.**

# ML Kit Text Recognition
-keep class com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.vision.**

# Room Database & Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Biometric & Security Crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

# WebRTC uses JNI callbacks into these classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# osmdroid
-dontwarn org.osmdroid.**
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**
# keep stack traces readable
-keepattributes SourceFile,LineNumberTable

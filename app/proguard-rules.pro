# Keep rules go here when a library needs them.

# Network shares (Files): the SMB library finds its crypto and protocol classes by name, and refers to desktop-only ones the box does not have.
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.rapid7.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn javax.el.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn net.engio.mbassy.**
-dontwarn sun.security.**

# SVG pictures (collection covers).
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# The software sound decoder is found by name (reflection) and talks to native code.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.audio.** { *; }

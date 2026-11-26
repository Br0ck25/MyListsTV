# Keep rules go here when a library needs them.

# The software sound decoder is found by name (reflection) and talks to native code.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.audio.** { *; }

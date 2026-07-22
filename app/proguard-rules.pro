# Vosk reaches its native library through JNA, which resolves classes, fields
# and methods reflectively at runtime. R8 cannot see those references, so
# without these rules the app builds and installs cleanly and then fails the
# moment recognition starts — the worst possible time to find out.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * implements com.sun.jna.Callback { *; }

# JNA carries desktop-JVM code paths that never run on Android.
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
-dontwarn java.awt.**

# PDFBox can hand JPEG2000 images to an optional decoder that we do not ship.
# This app only ever asks PDFBox for text, so the image path is never taken.
-dontwarn com.gemalto.jp2.JP2Decoder

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keep class com.example.ui.components.Jarvis3DBridge {
   public *;
}
-keepattributes JavascriptInterface

# Keep Android components & JNI / reflection targets
-keep class com.example.MainActivity { *; }
-keep class com.example.domain.service.JarvisBackgroundService { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.db.** { *; }
-keep class com.example.data.model.** { *; }

# MediaPipe & ML Kit & Porcupine JNI bindings
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**


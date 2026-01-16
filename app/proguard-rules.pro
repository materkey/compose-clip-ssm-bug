# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Main APK proguard rules - minimal to allow R8 optimization
# Keep only the classes needed for the app to function
-keepattributes *Annotation*

# Keep entry points
-keep class work.vkkovalev.samplecomposebug.MainActivity { *; }

# Keep all app classes (needed for tests to work with R8)
-keep class work.vkkovalev.samplecomposebug.** { *; }

# Keep Kotlin stdlib (needed for tests to work against minified APK)
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Suppress warnings
-dontwarn javax.lang.model.element.Modifier
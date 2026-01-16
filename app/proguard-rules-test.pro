# Test APK proguard rules - comprehensive keep rules for tests to work
# This file is used by testProguardFiles and should keep all classes
# needed for instrumentation tests to function

# Suppress warnings
-dontwarn javax.lang.model.element.Modifier
-dontwarn java.lang.invoke.StringConcatFactory

# Keep all Kotlin stdlib
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Keep ALL androidx classes (needed for tests to work)
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep test runner classes
-keep class org.junit.** { *; }
-keep class junit.** { *; }

# Keep app classes (needed for tests)
-keep class work.vkkovalev.samplecomposebug.** { *; }

# Keep Guava classes (used by Compose tests)
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**

# Keep annotations
-keepattributes *Annotation*

# Don't obfuscate for easier debugging
-dontobfuscate

# Don't shrink test APK - keep all classes
# The test APK doesn't need minification, only the main APK does for R8 testing
-dontshrink

# Don't optimize test APK
-dontoptimize

# Explicitly keep tracing classes (needed by AndroidJUnitRunner)
-keep class androidx.tracing.** { *; }

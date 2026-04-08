# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Termux terminal-emulator
-keep class com.termux.terminal.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.netcatty.mobile.**$$serializer { *; }
-keepclassmembers class com.netcatty.mobile.** { *** Companion; }
-keepclasseswithmembers class com.netcatty.mobile.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

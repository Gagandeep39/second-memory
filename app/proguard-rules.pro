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
# Google API Client & Drive SDK - uses reflection for JSON parsing
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.model.** { *; }

# Gson model serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.google.api.services.drive.model.** {
    <fields>;
    <init>(...);
    *;
}

# Prevent stripping of generic type info used by Gson
-keepattributes EnclosingMethod
-keepattributes InnerClasses
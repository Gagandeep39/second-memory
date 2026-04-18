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

# ---- Apache HttpClient / Google API Client (Java SE classes not on Android) ----
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**
-dontwarn javax.naming.ldap.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# ---- Google Drive SDK + API Client (reflection/Gson based) ----
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.http.** { *; }

# ---- Keep all fields/methods on API model objects (deserialized via reflection) ----
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
    <fields>;
    *;
}
-keepclassmembers class * extends com.google.api.client.util.GenericData {
    <fields>;
    *;
}

# ---- Gson / reflection metadata ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ---- Google Account Credential ----
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
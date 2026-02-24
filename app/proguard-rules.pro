# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.qbapps.claudeusage.data.remote.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

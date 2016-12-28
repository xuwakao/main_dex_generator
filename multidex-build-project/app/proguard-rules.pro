-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-dontwarn android.support.**
-dontwarn javax.microedition.khronos.**
#-keepattributes InnerClasses
-keepattributes JavascriptInterface
-keepattributes Signature
-keepattributes *Annotation*
-ignorewarnings

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep class android.support.v4.** { *; }
-keep class * extends android.support.v4.**

-keepclasseswithmembers class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** valueOf(int);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    <fields>;
    <methods>;
}

-keep class * implements java.io.Serializable {
    *;
}

-keepattributes SourceFile,LineNumberTable

-keep class * extends android.view.View

-keepclassmembers class **.R$* {
    public static <fields>;
}
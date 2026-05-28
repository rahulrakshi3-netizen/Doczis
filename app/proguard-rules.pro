-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class org.apache.** { *; }
-dontwarn org.apache.**

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

-keepclassmembers class * extends java.lang.Exception {
    *;
}

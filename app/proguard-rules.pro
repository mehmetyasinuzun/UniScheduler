# UniScheduler ProGuard Rules

# Apache POI
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.etsi.**
-dontwarn org.openxmlformats.**
-dontwarn org.w3.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn com.microsoft.**

# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.unischeduler.**$$serializer { *; }
-keepclassmembers class com.unischeduler.** {
    *** Companion;
}
-keepclasseswithmembers class com.unischeduler.** {
    kotlinx.serialization.KSerializer serializer(...);
}

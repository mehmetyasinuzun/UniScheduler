# ── Project models — accessed via reflection by kotlinx.serialization ────────
-keep class com.unischeduler.data.model.** { *; }
-keepclassmembers class com.unischeduler.data.model.** { *; }

# ── kotlinx.serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JsonElement, JsonObject, JsonArray, JsonPrimitive — JsonUtil reflection-free
# id çıkarımı için bunları kullanıyor; built-in serializer'ların korunması şart.
-keep class kotlinx.serialization.json.JsonElement { *; }
-keep class kotlinx.serialization.json.JsonObject { *; }
-keep class kotlinx.serialization.json.JsonArray { *; }
-keep class kotlinx.serialization.json.JsonPrimitive { *; }
-keep class kotlinx.serialization.json.JsonNull { *; }

-keep,includedescriptorclasses class com.unischeduler.**$$serializer { *; }
-keepclassmembers class com.unischeduler.** {
    *** Companion;
}
-keepclasseswithmembers class com.unischeduler.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Tüm @Serializable işaretli sınıfları + ilkel serializer'ları koru.
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlinx.serialization.builtins.** { *; }

# ── Supabase / Ktor — keep types used over the wire ──────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# Ktor uses kotlinx-coroutines internals reflectively
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── Apache POI (Excel) — keep so XSSFWorkbook etc. don't crash on release ───
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.logging.**
-dontwarn javax.xml.**

# POI transitively depends on graphbuilder + java.awt + javax.imageio for chart
# and shape rendering, which don't exist on Android. We never invoke those code
# paths (we only read/write cells), so it is safe to ignore them under R8.
-dontwarn org.graphbuilder.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.security.auth.x500.**
-dontwarn org.etsi.uri.x01903.v13.**
-dontwarn org.w3.x2000.x09.xmldsig.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn org.osgi.**
-dontwarn org.bouncycastle.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.**
-dontwarn de.rototor.pdfbox.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.xerces.**
-dontwarn org.apache.xml.security.**
-dontwarn org.python.**
-dontwarn org.codehaus.**
-dontwarn org.junit.**
-dontwarn junit.framework.**
-dontwarn java.beans.**
-dontwarn java.rmi.**
-dontwarn javax.crypto.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# ── Logging frameworks pulled in by POI ─────────────────────────────────────
-dontwarn org.slf4j.**
-dontwarn aQute.bnd.**

# ── AndroidX Security Crypto — Tink reflection ──────────────────────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Standard Android — coroutines, kotlin-reflect ───────────────────────────
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keep class kotlin.Result { *; }
-keepclassmembers class kotlin.Result { *; }

# Strip Log.d/v/i in release builds for performance + privacy
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

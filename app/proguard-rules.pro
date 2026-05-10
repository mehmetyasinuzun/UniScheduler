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

# ── Excel — Apache POI removed Nov 2026 ──────────────────────────────────-
# We replaced POI with hand-written MiniXlsxReader / MiniXlsxWriter
# (raw ZIP + XmlPullParser). No POI rules needed.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

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

# ── WorkManager + receivers ─────────────────────────────────────────────────
# WorkManager loads workers reflectively from the merged manifest; the
# class name must survive R8.
-keep class com.unischeduler.notif.** { *; }

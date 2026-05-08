package com.unischeduler.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Release/R8 ile uyumlu JSON id çıkarımı.
//
// `decodeList<Map<String, Int>>()` kullanmak release modunda kotlinx.serialization
// reified generic tip bilgisini kaybedebiliyor ("Serializer for class 'Map' is not found").
// JsonArray/JsonObject reflection gerektirmediği için ProGuard korumasıyla
// güvenli — bu yardımcı her ortamda aynı sonucu verir.
object JsonUtil {

    fun extractIntsFromColumn(raw: String, column: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val element = Json.parseToJsonElement(raw)
        return element.jsonArray
            .mapNotNull { row ->
                val obj = row as? JsonObject ?: return@mapNotNull null
                val cell = obj[column] ?: return@mapNotNull null
                if (cell is JsonNull) null else cell.jsonPrimitive.intOrNull
            }
            .toSet()
    }

    fun rowCount(raw: String): Int {
        if (raw.isBlank()) return 0
        return Json.parseToJsonElement(raw).jsonArray.size
    }
}

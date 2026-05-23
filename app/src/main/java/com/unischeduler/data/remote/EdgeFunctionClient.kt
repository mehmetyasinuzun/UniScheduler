// EdgeFunctionClient — Supabase Edge Functions için ince HTTP istemcisi.
//
// Neden Ktor / supabase-kt functions plugin'i değil?
//   - Ktor zaten Supabase-kt dependency'si olarak transitive geliyor (HttpClient
//     CIO engine) — yeni bir dependency eklemek gerekmiyor.
//   - supabase-kt'nin "functions" eklentisi şu an opsiyonel; manuel JSON
//     serialize edip POST atmak daha az soyutlama + tam kontrol veriyor.
//   - Bu helper sadece bir endpoint için kullanılıyor; küçük ve önceden
//     görülebilir.
package com.unischeduler.data.remote

import android.util.Log
import com.unischeduler.BuildConfig
import com.unischeduler.util.CsvImporter
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object EdgeFunctionClient {

    // Edge Functions URL şablonu: {SUPABASE_URL}/functions/v1/{function-name}
    // SUPABASE_URL local.properties'ten BuildConfig'e yazılıyor.
    private val functionsBaseUrl: String by lazy {
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1"
    }

    // Kısa-ömürlü HTTP client. Edge Function çağrıları nadir ve ağır olabilir
    // (bulk import 30 sn+), bu yüzden timeout'lar cömert.
    private val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis  = 120_000   // 2 dakika
                connectTimeoutMillis  = 10_000
                socketTimeoutMillis   = 120_000
            }
        }
    }

    // Lax JSON — Edge Function response'unda beklenmeyen alanlar gelirse
    // crash etmesin.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls     = false
    }

    // ── DTOs ──────────────────────────────────────────────────────────────
    @Serializable
    private data class LecturerRowDto(
        val title: String? = null,
        val firstName: String,
        val lastName: String,
        val email: String? = null,
        val departmentName: String? = null,
    )

    @Serializable
    private data class BulkCreateLecturersRequest(
        val orgId: Int,
        val rows: List<LecturerRowDto>,
        val fallbackDepartmentId: Int? = null,
    )

    @Serializable
    data class CredentialDto(
        val username: String,
        val password: String,
        val firstName: String,
        val lastName: String,
    )

    @Serializable
    data class BulkCreateLecturersResponse(
        val imported: Int = 0,
        val skipped: Int = 0,
        @SerialName("skippedNames") val skippedNames: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val credentials: List<CredentialDto> = emptyList(),
        val error: String? = null,
    )

    /**
     * Calls the [bulk-create-lecturers] Edge Function with the current
     * admin's JWT. Returns parsed response on success.
     *
     * Throws on:
     *   - Missing JWT (caller not authenticated)
     *   - HTTP non-2xx (with body excerpt for debugging)
     *   - JSON parse failure
     *
     * The caller (DataViewModel.importLecturers) catches and falls back to
     * client-side import on any exception — yani Edge Function deploy
     * edilmemişse uygulama yine çalışır.
     */
    suspend fun bulkCreateLecturers(
        orgId: Int,
        rows: List<CsvImporter.LecturerRow>,
        fallbackDepartmentId: Int,
    ): BulkCreateLecturersResponse {
        val session = SupabaseClient.client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("Edge Function çağrısı için admin oturumu gerekli (JWT yok).")
        val accessToken = session.accessToken

        val payload = BulkCreateLecturersRequest(
            orgId = orgId,
            rows = rows.map {
                LecturerRowDto(
                    title          = it.title.takeIf { t -> t.isNotBlank() },
                    firstName      = it.firstName,
                    lastName       = it.lastName,
                    email          = it.email?.takeIf { e -> e.isNotBlank() },
                    departmentName = it.departmentName?.takeIf { d -> d.isNotBlank() },
                )
            },
            fallbackDepartmentId = fallbackDepartmentId.takeIf { it > 0 },
        )

        val url = "$functionsBaseUrl/bulk-create-lecturers"
        Log.d("EdgeFn", "POST $url with ${rows.size} rows")

        val response = httpClient.post(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                // anon_key apikey header'ı — Supabase Edge Functions her zaman
                // bekliyor (function genel olarak public erişilebilir olsa bile).
                append("apikey", BuildConfig.SUPABASE_ANON_KEY)
            }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(BulkCreateLecturersRequest.serializer(), payload))
        }

        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Edge Function deploy edilmemişse genelde 404 veya HTML döner;
            // bunu net mesajla raporla ki fallback path'i tetiklensin.
            throw IllegalStateException(
                "Edge Function HTTP ${response.status.value}: ${bodyText.take(300)}"
            )
        }

        return runCatching {
            json.decodeFromString(BulkCreateLecturersResponse.serializer(), bodyText)
        }.getOrElse {
            throw IllegalStateException("Edge Function yanıtı parse edilemedi: ${bodyText.take(300)}")
        }
    }
}

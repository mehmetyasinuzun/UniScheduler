// Singleton Supabase client.
// Endpoint : BuildConfig.SUPABASE_URL  (from local.properties)
// Auth key  : BuildConfig.SUPABASE_ANON_KEY
// Plugins   : GoTrue (Auth) + Postgrest (CRUD) + Realtime (live queries)
package com.unischeduler.data.remote

import com.unischeduler.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.serialization.json.Json

object SupabaseClient {
    // Forgiving JSON config — Postgrest responses include columns the
    // mobile app's data classes don't model (created_at, updated_at,
    // deleted_at, is_active, last_login_at, …). Without ignoreUnknownKeys,
    // a single new column added in a database migration would crash every
    // decodeList<>() call in the app. Tightly coupling client model
    // versions to the schema would force a coordinated app release for
    // every backend change — not viable for a multi-device deployment.
    private val laxJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls     = false
    }

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest) {
            serializer = io.github.jan.supabase.serializer.KotlinXSerializer(laxJson)
        }
        install(Realtime)
    }

    /**
     * Tear down every active Realtime channel (course/schedule/availability
     * observers) so a previous session's subscriptions can never leak data
     * to a new login. Call from logout — must run before clearing local state.
     */
    suspend fun closeAllRealtimeChannels() {
        runCatching {
            client.realtime.subscriptions.values
                .toList()
                .forEach { ch -> runCatching { client.realtime.removeChannel(ch) } }
        }
        runCatching { client.realtime.disconnect() }
    }

    /**
     * Hard reset auth + realtime in one call. Idempotent.
     */
    suspend fun resetForLogout() {
        closeAllRealtimeChannels()
        runCatching { client.auth.signOut() }
    }
}

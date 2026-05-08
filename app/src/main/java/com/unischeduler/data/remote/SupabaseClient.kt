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

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
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

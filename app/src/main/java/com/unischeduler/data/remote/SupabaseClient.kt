// Singleton Supabase client.
// Endpoint : BuildConfig.SUPABASE_URL  (from local.properties)
// Auth key  : BuildConfig.SUPABASE_ANON_KEY
// Plugins   : GoTrue (Auth) + Postgrest (CRUD) + Realtime (live queries)
package com.unischeduler.data.remote

import com.unischeduler.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}

package com.unischeduler.data.repository

import com.unischeduler.data.model.ClientErrorLogInsert
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest

class ErrorLogRepository {

    suspend fun insert(log: ClientErrorLogInsert) {
        client.postgrest["client_error_logs"].insert(log)
    }
}

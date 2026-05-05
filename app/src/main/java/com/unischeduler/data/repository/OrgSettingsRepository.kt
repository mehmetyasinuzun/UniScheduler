// Organization settings repository
package com.unischeduler.data.repository

import com.unischeduler.data.model.DAYS
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest

class OrgSettingsRepository {

    suspend fun getSettings(orgId: Int): OrgSettings {
        val settings = client.postgrest["org_settings"]
            .select { filter { eq("org_id", orgId) } }
            .decodeSingleOrNull<OrgSettings>()
        return settings ?: OrgSettings(
            orgId = orgId,
            timeStepMinutes = 10,
            activeDays = DAYS,
            dayStart = "08:00",
            dayEnd = "18:00"
        )
    }
}

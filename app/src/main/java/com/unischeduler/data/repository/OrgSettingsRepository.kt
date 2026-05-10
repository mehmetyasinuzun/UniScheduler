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

    /**
     * Upsert org settings. Tries UPDATE first; if no row matches we
     * fall back to INSERT. Done this way so the call works whether or
     * not the panel pre-seeded a row when the org was created.
     */
    suspend fun updateSettings(
        orgId: Int,
        timeStepMinutes: Int,
        activeDays: List<String>,
        dayStart: String,
        dayEnd: String
    ) {
        val payload = OrgSettings(
            orgId = orgId,
            timeStepMinutes = timeStepMinutes,
            activeDays = activeDays,
            dayStart = dayStart,
            dayEnd = dayEnd
        )
        // Postgrest upsert: insert with on-conflict update on the
        // primary key (org_id). One round trip, no race window.
        client.postgrest["org_settings"]
            .upsert(payload) { onConflict = "org_id" }
    }
}

// Availability repository — CRUD for lecturer BUSY time blocks.
// Default: all lecturers are available. Entries mark times they are NOT available.
package com.unischeduler.data.repository

import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.LecturerAvailabilityInsert
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest

class AvailabilityRepository {

    suspend fun getForLecturer(lecturerId: Int, orgId: Int): List<LecturerAvailability> =
        client.postgrest["lecturer_availability"]
            .select {
                filter {
                    eq("lecturer_id", lecturerId)
                    eq("org_id", orgId)
                }
            }
            .decodeList<LecturerAvailability>()

    /** Org-wide availability rows (used by Admin Auto-Schedule and Backup). */
    suspend fun getAllForOrg(orgId: Int): List<LecturerAvailability> =
        client.postgrest["lecturer_availability"]
            .select {
                filter { eq("org_id", orgId) }
                limit(20000)
            }
            .decodeList<LecturerAvailability>()

    suspend fun insert(lecturerId: Int, day: String, startTime: String, endTime: String, orgId: Int) {
        client.postgrest["lecturer_availability"].insert(
            LecturerAvailabilityInsert(
                orgId = orgId,
                lecturerId = lecturerId,
                day = day,
                startTime = startTime,
                endTime = endTime
            )
        )
    }

    suspend fun delete(id: Int, orgId: Int) {
        client.postgrest["lecturer_availability"]
            .delete { filter { eq("id", id); eq("org_id", orgId) } }
    }

    /**
     * Check if lecturer is available at a given day+time range.
     * Default = available (no entries). Entries mark BUSY times.
     * Returns false if any busy block overlaps the requested range.
     */
    suspend fun isAvailable(
        lecturerId: Int,
        day: String,
        startTime: String,
        endTime: String,
        orgId: Int
    ): Boolean {
        val busySlots = getForLecturer(lecturerId, orgId)
            .filter { it.day == day }
        if (busySlots.isEmpty()) return true
        val startMin = toMinutes(startTime)
        val endMin   = toMinutes(endTime)
        return busySlots.none { slot ->
            toMinutes(slot.startTime) < endMin && startMin < toMinutes(slot.endTime)
        }
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}

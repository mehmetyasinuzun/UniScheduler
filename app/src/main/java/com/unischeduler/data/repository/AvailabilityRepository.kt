// Availability repository — CRUD for lecturer free-time blocks.
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

    /** Check if lecturer is available at a given day+time range. */
    suspend fun isAvailable(
        lecturerId: Int,
        day: String,
        startTime: String,
        endTime: String,
        orgId: Int
    ): Boolean {
        val slots = getForLecturer(lecturerId, orgId)
            .filter { it.day == day }
        if (slots.isEmpty()) return false // no availability marked = not available
        val startMin = toMinutes(startTime)
        val endMin   = toMinutes(endTime)
        // Check if the requested range is fully covered by at least one availability block
        return slots.any { slot ->
            toMinutes(slot.startTime) <= startMin && toMinutes(slot.endTime) >= endMin
        }
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}

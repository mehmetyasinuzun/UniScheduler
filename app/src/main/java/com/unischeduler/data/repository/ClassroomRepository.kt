// Classroom repository — CRUD + available classroom query
// "Available" = has at least one free slot in the week
package com.unischeduler.data.repository

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.ClassroomInsert
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class ClassroomRepository {

    suspend fun getAllClassrooms(orgId: Int): List<Classroom> =
        client.postgrest["classrooms"]
            .select(Columns.raw("*, departments(*)")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Classroom>()

    suspend fun insertClassroom(roomCode: String, capacity: Int, departmentId: Int?, orgId: Int, type: String = "theory") {
        client.postgrest["classrooms"].insert(
            ClassroomInsert(
                orgId = orgId,
                roomCode = roomCode,
                capacity = capacity,
                departmentId = departmentId,
                type = type
            )
        )
    }

    suspend fun deleteClassroom(id: Int, orgId: Int) {
        client.postgrest["classrooms"]
            .delete { filter { eq("id", id); eq("org_id", orgId) } }
    }

    // A classroom is "available" if total booked minutes is below weekly capacity.
    suspend fun getAvailableClassrooms(orgId: Int, settings: OrgSettings): List<Classroom> {
        val all = getAllClassrooms(orgId)
        val totalMinutesPerDay = timeDiffMinutes(settings.dayStart, settings.dayEnd)
        val totalWeekMinutes = totalMinutesPerDay * settings.activeDays.size

        val entries = client.postgrest["schedule_entries"]
            .select(Columns.raw("classroom_id, start_time, end_time")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Map<String, String>>()

        val bookedMinutesByClassroom = mutableMapOf<Int, Int>()
        for (row in entries) {
            val classroomId = row["classroom_id"]?.toIntOrNull() ?: continue
            val start = row["start_time"] ?: continue
            val end = row["end_time"] ?: continue
            val minutes = timeDiffMinutes(start, end)
            bookedMinutesByClassroom[classroomId] =
                (bookedMinutesByClassroom[classroomId] ?: 0) + minutes
        }

        return all.filter { (bookedMinutesByClassroom[it.id] ?: 0) < totalWeekMinutes }
    }

    private fun timeDiffMinutes(start: String, end: String): Int {
        val startMin = toMinutes(start)
        val endMin = toMinutes(end)
        return (endMin - startMin).coerceAtLeast(0)
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }
}

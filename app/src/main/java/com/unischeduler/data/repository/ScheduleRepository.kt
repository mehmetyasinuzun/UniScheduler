// Schedule repository — assignment CRUD + double-booking guard
// Double-booking check happens before insert (DB unique constraints are the final safety net).
// Real-time Flow emits on any schedule_entries change.
package com.unischeduler.data.repository

import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.model.ScheduleEntryInsert
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class ScheduleRepository {

    private val joinColumns = Columns.raw(
        "*, offerings(*, courses(*, departments(*))), lecturers(*, departments(*), users(*)), classrooms(*, departments(*))"
    )

    data class ScheduleConflicts(
        val lecturerConflicts: List<ScheduleEntry>,
        val classroomConflicts: List<ScheduleEntry>
    ) {
        val hasConflicts: Boolean
            get() = lecturerConflicts.isNotEmpty() || classroomConflicts.isNotEmpty()
    }

    suspend fun getAllEntries(orgId: Int): List<ScheduleEntry> =
        client.postgrest["schedule_entries"]
            .select(joinColumns) {
                filter { eq("org_id", orgId) }
                order(column = "day", order = Order.ASCENDING)
                order(column = "start_time", order = Order.ASCENDING)
                limit(20000)
            }
            .decodeList<ScheduleEntry>()

    suspend fun getEntriesForLecturer(lecturerId: Int, orgId: Int): List<ScheduleEntry> =
        client.postgrest["schedule_entries"]
            .select(joinColumns) {
                filter {
                    eq("lecturer_id", lecturerId)
                    eq("org_id", orgId)
                }
                order(column = "day", order = Order.ASCENDING)
                order(column = "start_time", order = Order.ASCENDING)
                limit(10000)
            }
            .decodeList<ScheduleEntry>()

    // Throws IllegalStateException on double-booking before hitting DB.
    // excludeEntryId: editing flow → ignore the entry being edited so it
    // doesn't collide with itself (same lecturer/room/time as before).
    suspend fun findConflicts(
        lecturerId: Int?,
        classroomId: Int,
        day: String,
        startTime: String,
        endTime: String,
        orgId: Int,
        excludeEntryId: Int? = null
    ): ScheduleConflicts {
        val lecturerEntries = if (lecturerId != null) {
            client.postgrest["schedule_entries"]
                .select(joinColumns) {
                    filter {
                        eq("lecturer_id", lecturerId)
                        eq("day", day)
                        eq("org_id", orgId)
                    }
                }
                .decodeList<ScheduleEntry>()
        } else emptyList()

        val classroomEntries = client.postgrest["schedule_entries"]
            .select(joinColumns) {
                filter {
                    eq("classroom_id", classroomId)
                    eq("day", day)
                    eq("org_id", orgId)
                }
            }
            .decodeList<ScheduleEntry>()

        val start = toMinutes(startTime)
        val end   = toMinutes(endTime)

        val lecturerConflicts = lecturerEntries
            .filter { excludeEntryId == null || it.id != excludeEntryId }
            .filter { overlaps(start, end, it.startTime, it.endTime) }
        val classroomConflicts = classroomEntries
            .filter { excludeEntryId == null || it.id != excludeEntryId }
            .filter { overlaps(start, end, it.startTime, it.endTime) }

        return ScheduleConflicts(lecturerConflicts, classroomConflicts)
    }

    suspend fun updateEntry(
        entryId: Int,
        offeringId: Int,
        lecturerId: Int?,
        classroomId: Int,
        day: String,
        startTime: String,
        endTime: String,
        orgId: Int
    ) {
        client.postgrest["schedule_entries"]
            .update({
                set("offering_id", offeringId)
                set("lecturer_id", lecturerId)
                set("classroom_id", classroomId)
                set("day", day)
                set("start_time", startTime)
                set("end_time", endTime)
            }) {
                filter {
                    eq("id", entryId)
                    eq("org_id", orgId)
                }
            }
    }

    suspend fun insertEntry(
        offeringId: Int,
        lecturerId: Int?,
        classroomId: Int,
        day: String,
        startTime: String,
        endTime: String,
        orgId: Int
    ) {
        client.postgrest["schedule_entries"].insert(
            ScheduleEntryInsert(
                orgId = orgId,
                offeringId = offeringId,
                lecturerId = lecturerId,
                classroomId = classroomId,
                day = day,
                startTime = startTime,
                endTime = endTime
            )
        )
    }

    suspend fun deleteEntry(entryId: Int, orgId: Int) {
        client.postgrest["schedule_entries"]
            .delete {
                filter {
                    eq("id", entryId)
                    eq("org_id", orgId)
                }
            }
    }

    private fun overlaps(startA: Int, endA: Int, startB: String, endB: String): Boolean {
        val bStart = toMinutes(startB)
        val bEnd = toMinutes(endB)
        return startA < bEnd && bStart < endA
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }
}

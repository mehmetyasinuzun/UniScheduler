// Offering repository — CRUD for opened sections
package com.unischeduler.data.repository

import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.OfferingInsert
import com.unischeduler.data.remote.SupabaseClient.client
import com.unischeduler.util.JsonUtil
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class OfferingRepository {

    private val joinColumns = Columns.raw(
        "*, courses(*, departments(*)), lecturers(*, departments(*), users(*))"
    )

    suspend fun getAllOfferings(orgId: Int): List<Offering> =
        client.postgrest["offerings"]
            .select(joinColumns) {
                filter { eq("org_id", orgId) }
                order(column = "academic_year", order = Order.DESCENDING)
                limit(10000)
            }
            .decodeList<Offering>()

    suspend fun insertOffering(
        courseId: Int,
        lecturerId: Int?,
        academicYear: String,
        term: String,
        classYear: Int,
        section: String,
        capacity: Int,
        orgId: Int
    ) {
        client.postgrest["offerings"].insert(
            OfferingInsert(
                orgId = orgId,
                courseId = courseId,
                lecturerId = lecturerId,
                academicYear = academicYear,
                term = term,
                classYear = classYear,
                section = section,
                capacity = capacity
            )
        )
    }

    suspend fun updateOffering(id: Int, lecturerId: Int?, academicYear: String, term: String, classYear: Int, section: String, capacity: Int, orgId: Int) {
        client.postgrest["offerings"]
            .update({
                set("lecturer_id", lecturerId)
                set("academic_year", academicYear)
                set("term", term)
                set("class_year", classYear)
                set("section", section)
                set("capacity", capacity)
            }) { filter { eq("id", id); eq("org_id", orgId) } }
    }

    suspend fun deleteOffering(id: Int, orgId: Int) {
        client.postgrest["offerings"]
            .delete { filter { eq("id", id); eq("org_id", orgId) } }
    }

    suspend fun getUnassignedOfferings(orgId: Int): List<Offering> {
        val all = getAllOfferings(orgId)
        val raw = client.postgrest["schedule_entries"]
            .select(Columns.raw("offering_id")) {
                filter { eq("org_id", orgId) }
            }
            .data
        val assignedIds = JsonUtil.extractIntsFromColumn(raw, "offering_id")
        return all.filter { it.id !in assignedIds }
    }
}

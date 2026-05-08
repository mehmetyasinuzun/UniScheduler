// Course repository — CRUD + real-time Flow via Supabase Realtime
// Endpoint: /rest/v1/courses (with departments join)
// Real-time: callbackFlow wrapping PostgresChangeAction listener
package com.unischeduler.data.repository

import com.unischeduler.data.model.Course
import com.unischeduler.data.model.CourseInsert
import com.unischeduler.data.remote.SupabaseClient.client
import com.unischeduler.util.JsonUtil
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class CourseRepository {

    // One-shot fetch — used by Task 1 pattern.
    // Explicit limit guards against the silent 1000-row Supabase default; the
    // org-scoped data set should never approach this in practice but the cap
    // keeps very large orgs predictable.
    suspend fun getAllCourses(orgId: Int): List<Course> =
        client.postgrest["courses"]
            .select(Columns.raw("*, departments(*)")) {
                filter { eq("org_id", orgId) }
                order(column = "code", order = Order.ASCENDING)
                limit(10000)
            }
            .decodeList<Course>()

    suspend fun insertCourse(
        code: String,
        name: String,
        departmentId: Int,
        orgId: Int,
        theoryHours: Int = 0,
        labHours: Int    = 0,
        credits: Int     = 0
    ) {
        client.postgrest["courses"].insert(
            CourseInsert(
                orgId = orgId,
                code = code,
                name = name,
                theoryHours = theoryHours,
                labHours = labHours,
                credits = credits,
                departmentId = departmentId
            )
        )
    }

    suspend fun updateCourse(id: Int, code: String, name: String, theoryHours: Int, labHours: Int, credits: Int, orgId: Int) {
        client.postgrest["courses"]
            .update({
                set("code", code)
                set("name", name)
                set("theory_hours", theoryHours)
                set("lab_hours", labHours)
                set("credits", credits)
            }) { filter { eq("id", id); eq("org_id", orgId) } }
    }

    suspend fun deleteCourse(id: Int, orgId: Int) {
        client.postgrest["courses"]
            .delete { filter { eq("id", id); eq("org_id", orgId) } }
    }

    // Returns courses with no entry in schedule_entries (unassigned)
    suspend fun getUnassignedCourses(orgId: Int): List<Course> {
        val all = getAllCourses(orgId)
        val raw = client.postgrest["offerings"]
            .select(Columns.raw("course_id")) {
                filter { eq("org_id", orgId) }
            }
            .data
        val assignedIds = JsonUtil.extractIntsFromColumn(raw, "course_id")
        return all.filter { it.id !in assignedIds }
    }
}

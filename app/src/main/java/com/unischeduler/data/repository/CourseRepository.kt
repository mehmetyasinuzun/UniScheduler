// Course repository — CRUD + real-time Flow via Supabase Realtime
// Endpoint: /rest/v1/courses (with departments join)
// Real-time: callbackFlow wrapping PostgresChangeAction listener
package com.unischeduler.data.repository

import com.unischeduler.data.model.Course
import com.unischeduler.data.model.CourseInsert
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class CourseRepository {

    // One-shot fetch — used by Task 1 pattern
    suspend fun getAllCourses(orgId: Int): List<Course> =
        client.postgrest["courses"]
            .select(Columns.raw("*, departments(*)")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Course>()

    suspend fun getCoursesByDepartment(departmentId: Int, orgId: Int): List<Course> =
        client.postgrest["courses"]
            .select(Columns.raw("*, departments(*)")) {
                filter {
                    eq("department_id", departmentId)
                    eq("org_id", orgId)
                }
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

    // Real-time Flow — used by Task 2 pattern
    // Emits a fresh list every time any row in "courses" changes.
    fun observeCourses(orgId: Int): Flow<List<Course>> = callbackFlow {
        val channel = client.realtime.channel("courses_changes")

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "courses"
        }

        launch {
            changeFlow.collect {
                // Re-fetch full list with join on every change
                trySend(getAllCourses(orgId))
            }
        }

        channel.subscribe()
        // Emit initial data immediately
        trySend(getAllCourses(orgId))

        awaitClose {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                client.realtime.removeChannel(channel)
            }
        }
    }

    // Returns courses with no entry in schedule_entries (unassigned)
    suspend fun getUnassignedCourses(orgId: Int): List<Course> {
        val all = getAllCourses(orgId)
        val assignedIds = client.postgrest["offerings"]
            .select(Columns.raw("course_id")) {
                filter { eq("org_id", orgId) }
            }
            .decodeList<Map<String, Int>>()
            .mapNotNull { it["course_id"] }
            .toSet()
        return all.filter { it.id !in assignedIds }
    }
}

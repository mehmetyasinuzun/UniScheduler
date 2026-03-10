package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.CourseLecturerDto
import com.unischeduler.data.remote.dto.CourseDto
import com.unischeduler.data.remote.mapper.toDomain
import com.unischeduler.data.remote.mapper.toDto
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.repository.CourseRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

class CourseRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : CourseRepository {

    override suspend fun getCoursesByDepartment(departmentId: Int): List<Course> {
        return supabase.postgrest.from("courses")
            .select { filter { eq("department_id", departmentId) } }
            .decodeList<CourseDto>()
            .map { it.toDomain() }
    }

    override suspend fun getCourseById(id: Int): Course? {
        return supabase.postgrest.from("courses")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<CourseDto>()
            ?.toDomain()
    }

    override suspend fun upsertCourse(course: Course): Course {
        val dto = course.toDto()
        // id=0 ise yeni ders - code+department_id üzerinden conflict kontrolü yap
        val result = if (dto.id == 0) {
            supabase.postgrest.from("courses")
                .upsert(dto) {
                    onConflict = "code,department_id"
                    select()
                }
                .decodeSingle<CourseDto>()
        } else {
            supabase.postgrest.from("courses")
                .upsert(dto) { select() }
                .decodeSingle<CourseDto>()
        }
        return result.toDomain()
    }

    override suspend fun upsertCourses(courses: List<Course>) {
        supabase.postgrest.from("courses")
            .upsert(courses.map { it.toDto() })
    }

    override suspend fun deleteCourse(id: Int) {
        supabase.postgrest.from("courses")
            .delete { filter { eq("id", id) } }
    }

    override suspend fun assignLecturerToCourse(courseId: Int, lecturerId: Int) {
        supabase.postgrest.from("course_lecturers")
            .upsert(CourseLecturerDto(courseId = courseId, lecturerId = lecturerId))
    }

    override suspend fun getCoursesForLecturer(lecturerId: Int): List<Course> {
        val links = supabase.postgrest.from("course_lecturers")
            .select { filter { eq("lecturer_id", lecturerId) } }
            .decodeList<CourseLecturerDto>()

        if (links.isEmpty()) return emptyList()

        return supabase.postgrest.from("courses")
            .select { filter { isIn("id", links.map { it.courseId }) } }
            .decodeList<CourseDto>()
            .map { it.toDomain() }
    }
}

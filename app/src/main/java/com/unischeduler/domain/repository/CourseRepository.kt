package com.unischeduler.domain.repository

import com.unischeduler.domain.model.Course

interface CourseRepository {
    suspend fun getCoursesByDepartment(departmentId: Int): List<Course>
    suspend fun getCourseById(id: Int): Course?
    suspend fun upsertCourse(course: Course): Course
    suspend fun upsertCourses(courses: List<Course>)
    suspend fun deleteCourse(id: Int)
    suspend fun assignLecturerToCourse(courseId: Int, lecturerId: Int)
    suspend fun getCoursesForLecturer(lecturerId: Int): List<Course>
}

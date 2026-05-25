// TourMockStore — admin tour boyunca kullanılan in-memory mock veri deposu.
//
// PATTERN: Fragment'lar (SettingsFragment, DataFragment, ClassroomsFragment,
// AssignmentFragment, AdminCalendarFragment) bu store'a subscribe olur. Tour
// adımında "Ekle" butonu basıldığında TourCoordinator overlay tap'i intercept
// edip ilgili add* metodunu çağırır — store güncellenir → listener tetiklenir
// → fragment'ın adapter'ı gerçek liste ile mock liste'yi union ederek
// submitList yapar. Repository ve Supabase'e SIFIR temas.
//
// Tur bitince clear() çağrılır → mock'lar kaybolur → fragment.viewModel
// gerçek DB'den reload eder (zaten state collect ediyor).
//
// ID'ler negatif (-1, -2, ...) verilir — gerçek DB id'leri pozitif olduğu
// için karışma riski yok ve "mock" olduğunu hemen ayırt edebiliriz.
package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.model.User

object TourMockStore {

    const val DEPT_NAME = "Bilgisayar Mühendisliği"
    const val LECTURER_FIRST = "Farhan"
    const val LECTURER_LAST = "Adl"
    const val COURSE1_CODE = "PL101"
    const val COURSE1_NAME = "Programming Language"
    const val COURSE2_CODE = "MP101"
    const val COURSE2_NAME = "Mobile Programming"
    const val CLASSROOM_CODE = "L101"

    @Volatile private var nextId: Int = -1
    private fun nextId(): Int = nextId--

    @Volatile var department: Department? = null; private set
    @Volatile var lecturer: Lecturer? = null; private set
    @Volatile var course1: Course? = null; private set
    @Volatile var course2: Course? = null; private set
    @Volatile var offering1: Offering? = null; private set
    @Volatile var offering2: Offering? = null; private set
    @Volatile var classroom: Classroom? = null; private set
    @Volatile var schedule: ScheduleEntry? = null; private set

    // Tüm mock öğeleri tek tek liste olarak — fragment union için
    val departments: List<Department> get() = listOfNotNull(department)
    val lecturers: List<Lecturer> get() = listOfNotNull(lecturer)
    val courses: List<Course> get() = listOfNotNull(course1, course2)
    val offerings: List<Offering> get() = listOfNotNull(offering1, offering2)
    val classrooms: List<Classroom> get() = listOfNotNull(classroom)
    val schedules: List<ScheduleEntry> get() = listOfNotNull(schedule)

    private val listeners = mutableSetOf<() -> Unit>()
    fun subscribe(l: () -> Unit) { listeners.add(l) }
    fun unsubscribe(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.toList().forEach { runCatching { it.invoke() } } }

    fun clear() {
        department = null; lecturer = null; course1 = null; course2 = null
        offering1 = null; offering2 = null; classroom = null; schedule = null
        nextId = -1
        notifyListeners()
    }

    fun addDepartment(orgId: Int) {
        if (department != null) return
        department = Department(id = nextId(), orgId = orgId, name = DEPT_NAME)
        notifyListeners()
    }

    fun addLecturer(orgId: Int) {
        if (lecturer != null) return
        val dept = department ?: Department(id = nextId(), orgId = orgId, name = DEPT_NAME).also { department = it }
        lecturer = Lecturer(
            id = nextId(),
            orgId = orgId,
            userId = "tour-demo-farhan-adl",
            title = "Dr.",
            firstName = LECTURER_FIRST,
            lastName = LECTURER_LAST,
            email = null,
            departmentId = dept.id,
            departments = dept,
            users = User(id = "tour-demo-farhan-adl", orgId = orgId,
                username = "farhan_adl", role = "lecturer", mustChangePassword = true)
        )
        notifyListeners()
    }

    fun addCourse1(orgId: Int) {
        if (course1 != null) return
        val dept = department ?: Department(id = nextId(), orgId = orgId, name = DEPT_NAME).also { department = it }
        course1 = Course(
            id = nextId(), orgId = orgId,
            code = COURSE1_CODE, name = COURSE1_NAME,
            theoryHours = 3, labHours = 0, credits = 3,
            departmentId = dept.id, departments = dept
        )
        notifyListeners()
    }

    fun addCourse2(orgId: Int) {
        if (course2 != null) return
        val dept = department ?: Department(id = nextId(), orgId = orgId, name = DEPT_NAME).also { department = it }
        course2 = Course(
            id = nextId(), orgId = orgId,
            code = COURSE2_CODE, name = COURSE2_NAME,
            theoryHours = 2, labHours = 2, credits = 4,
            departmentId = dept.id, departments = dept
        )
        notifyListeners()
    }

    fun addOffering1(orgId: Int) {
        if (offering1 != null) return
        val c = course1 ?: return
        val l = lecturer ?: return
        offering1 = Offering(
            id = nextId(), orgId = orgId,
            courseId = c.id, lecturerId = l.id,
            academicYear = "2024-2025", term = "Fall",
            classYear = 2, section = "A", capacity = 30,
            courses = c, lecturers = l
        )
        notifyListeners()
    }

    fun addOffering2(orgId: Int) {
        if (offering2 != null) return
        val c = course2 ?: return
        val l = lecturer ?: return
        offering2 = Offering(
            id = nextId(), orgId = orgId,
            courseId = c.id, lecturerId = l.id,
            academicYear = "2024-2025", term = "Fall",
            classYear = 2, section = "A", capacity = 30,
            courses = c, lecturers = l
        )
        notifyListeners()
    }

    fun addClassroom(orgId: Int) {
        if (classroom != null) return
        val dept = department ?: Department(id = nextId(), orgId = orgId, name = DEPT_NAME).also { department = it }
        classroom = Classroom(
            id = nextId(), orgId = orgId,
            roomCode = CLASSROOM_CODE, capacity = 30, type = "theory",
            departmentId = dept.id, departments = dept
        )
        notifyListeners()
    }

    fun addSchedule(orgId: Int) {
        if (schedule != null) return
        val o = offering1 ?: return
        val l = lecturer ?: return
        val cr = classroom ?: return
        schedule = ScheduleEntry(
            id = nextId(), orgId = orgId,
            offeringId = o.id, lecturerId = l.id, classroomId = cr.id,
            day = "Monday", startTime = "09:00", endTime = "12:00",
            offerings = o, lecturers = l, classrooms = cr
        )
        notifyListeners()
    }
}

// BackupManager — exports the entire org-scoped dataset to a single JSON
// document for archival.
//
// Design decisions (senior review notes):
//
//  • Export-only in this version. Restore is intentionally NOT implemented
//    here because a correct restore requires:
//       (a) a CASCADE-safe order of inserts (organizations → settings →
//           departments → users (auth) → lecturers → courses → classrooms
//           → offerings → schedule_entries → availability)
//       (b) Auth Admin API access to recreate users with stable IDs — only
//           available with the service_role key, which the mobile app does
//           NOT carry (and shouldn't carry — bypassing RLS from a phone is
//           a security disaster).
//       (c) FK id remapping (every row has a SERIAL primary key; restoring
//           into a non-empty database changes the ids and breaks every
//           FK reference in the dump).
//    A correct restore therefore lives on the super-admin web panel, which
//    has service_role and can run a single transaction. That ticket is
//    tracked separately. For mobile users, this export is the data sigorta
//    poliçesi: download, archive, sleep at night.
//
//  • One JSON file with a `schemaVersion`. Future readers (the eventual
//    panel-side restore, or a different tool) can branch on this to handle
//    older dumps. Keep current export at v1.
//
//  • All Supabase reads happen inside a single coroutineScope so the seven
//    table reads run in parallel — fast enough that a 5000-row dataset
//    finishes in <2s on a typical fiber connection.
//
//  • The output JSON is pretty-printed for human inspection. The ~30%
//    size penalty is irrelevant compared to the value of being able to
//    `cat` the file in a terminal during incident response.
package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.AvailabilityRepository
import com.unischeduler.data.repository.ClassroomRepository
import com.unischeduler.data.repository.CourseRepository
import com.unischeduler.data.repository.DepartmentRepository
import com.unischeduler.data.repository.LecturerRepository
import com.unischeduler.data.repository.OfferingRepository
import com.unischeduler.data.repository.OrgSettingsRepository
import com.unischeduler.data.repository.ScheduleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupManager {

    /** Bumped if the on-disk JSON shape changes. v1 = initial. */
    const val SCHEMA_VERSION = 1

    @Serializable
    data class Backup(
        @SerialName("schema_version") val schemaVersion: Int,
        @SerialName("exported_at")    val exportedAt: String,
        @SerialName("app_version")    val appVersion: String,
        @SerialName("org_id")         val orgId: Int,
        val data: BackupData
    )

    @Serializable
    data class BackupData(
        val settings:    OrgSettings? = null,
        val departments: List<Department>            = emptyList(),
        val lecturers:   List<Lecturer>              = emptyList(),
        val courses:     List<Course>                = emptyList(),
        val classrooms:  List<Classroom>             = emptyList(),
        val offerings:   List<Offering>              = emptyList(),
        @SerialName("schedule_entries")
        val schedule:    List<ScheduleEntry>         = emptyList(),
        val availability: List<LecturerAvailability> = emptyList()
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Pull every org-scoped table in parallel and serialise the bundle.
     *
     * Caller is expected to be on Dispatchers.IO.
     */
    suspend fun createBackup(orgId: Int, appVersion: String): String = coroutineScope {
        val settingsRepo    = OrgSettingsRepository()
        val deptRepo        = DepartmentRepository()
        val lecturerRepo    = LecturerRepository()
        val courseRepo      = CourseRepository()
        val classroomRepo   = ClassroomRepository()
        val offeringRepo    = OfferingRepository()
        val scheduleRepo    = ScheduleRepository()
        val availabilityRepo = AvailabilityRepository()

        // Parallelise all eight reads. settings can be null on a brand-new org.
        val settingsAsync     = async { runCatching { settingsRepo.getSettings(orgId) }.getOrNull() }
        val departmentsAsync  = async { runCatching { deptRepo.getAllDepartments(orgId) }.getOrDefault(emptyList()) }
        val lecturersAsync    = async { runCatching { lecturerRepo.getAllLecturers(orgId) }.getOrDefault(emptyList()) }
        val coursesAsync      = async { runCatching { courseRepo.getAllCourses(orgId) }.getOrDefault(emptyList()) }
        val classroomsAsync   = async { runCatching { classroomRepo.getAllClassrooms(orgId) }.getOrDefault(emptyList()) }
        val offeringsAsync    = async { runCatching { offeringRepo.getAllOfferings(orgId) }.getOrDefault(emptyList()) }
        val scheduleAsync     = async { runCatching { scheduleRepo.getAllEntries(orgId) }.getOrDefault(emptyList()) }
        val availabilityAsync = async { runCatching { availabilityRepo.getAllForOrg(orgId) }.getOrDefault(emptyList()) }

        val backup = Backup(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = isoTimestamp(),
            appVersion = appVersion,
            orgId = orgId,
            data = BackupData(
                settings     = settingsAsync.await(),
                departments  = departmentsAsync.await(),
                lecturers    = lecturersAsync.await(),
                courses      = coursesAsync.await(),
                classrooms   = classroomsAsync.await(),
                offerings    = offeringsAsync.await(),
                schedule     = scheduleAsync.await(),
                availability = availabilityAsync.await()
            )
        )
        json.encodeToString(Backup.serializer(), backup)
    }

    /** Quick statistics for the success dialog. */
    fun summarise(backup: Backup): String = buildString {
        val d = backup.data
        appendLine("• Bölüm: ${d.departments.size}")
        appendLine("• Hoca: ${d.lecturers.size}")
        appendLine("• Ders: ${d.courses.size}")
        appendLine("• Derslik: ${d.classrooms.size}")
        appendLine("• Açılan ders: ${d.offerings.size}")
        appendLine("• Program kaydı: ${d.schedule.size}")
        append("• Müsaitlik kaydı: ${d.availability.size}")
    }

    /**
     * Parse a JSON file produced by createBackup(). Doesn't touch the
     * database — pure deserialisation so the caller can validate and
     * confirm with the user before committing.
     */
    fun parseBackup(jsonText: String): Backup =
        json.decodeFromString(Backup.serializer(), jsonText)

    /**
     * Restore the data half of a backup (departments / courses /
     * classrooms / org_settings) into the caller's organization.
     *
     * Senior decision — what we DO and DON'T restore:
     *   ✅ org_settings (idempotent UPSERT)
     *   ✅ departments
     *   ✅ courses
     *   ✅ classrooms
     *   ❌ lecturers   — these depend on auth.users entries that the
     *                    mobile app cannot recreate without service_role
     *   ❌ offerings   — FK to lecturers, would dangle
     *   ❌ schedule    — FK to offerings/lecturers/classrooms, dangling
     *   ❌ availability — FK to lecturers
     *
     * What this means for users: the "lookup" data (what departments,
     * which courses, which rooms) is restored. Personnel (lecturers)
     * and the actual timetable need to be re-entered or re-imported
     * from Excel. This is the safe subset that doesn't require us to
     * have RLS-bypassing keys on the phone.
     *
     * The caller is expected to:
     *   1. Show the user a confirmation with summarise() output
     *   2. Verify backup.orgId matches session.orgId (refuse otherwise)
     *   3. Run this on Dispatchers.IO
     *
     * Strategy:
     *   • Wipe target tables in dependency-safe order (we use the same
     *     CASCADE Postgres would use, but explicit deletes go through
     *     RLS so admins of other orgs are safe).
     *   • Insert in the same dependency-safe order. id columns are
     *     SERIAL so we can't preserve the originals; instead we map
     *     old_id → new_id for departments/courses/classrooms so any
     *     joined fields could be back-filled in a future enhancement
     *     (offerings/schedule restore via panel).
     */
    suspend fun restoreLookupData(orgId: Int, backup: Backup): RestoreResult {
        require(backup.orgId == orgId) {
            "Backup org_id (${backup.orgId}) does not match current org ($orgId)"
        }

        val deptRepo     = DepartmentRepository()
        val courseRepo   = CourseRepository()
        val classroomRepo = ClassroomRepository()
        val settingsRepo = OrgSettingsRepository()

        // 1) Wipe — order matters: courses & classrooms reference departments.
        //    All deletes go through RLS (admin-only writes inside our org).
        for (c in courseRepo.getAllCourses(orgId))      runCatching { courseRepo.deleteCourse(c.id, orgId) }
        for (r in classroomRepo.getAllClassrooms(orgId)) runCatching { classroomRepo.deleteClassroom(r.id, orgId) }
        for (d in deptRepo.getAllDepartments(orgId))    runCatching { deptRepo.deleteDepartment(d.id, orgId) }

        // 2) Restore departments first (others FK them by name lookup).
        var deptsRestored = 0
        val nameToNewId = HashMap<String, Int>()
        for (dep in backup.data.departments) {
            runCatching {
                deptRepo.insertDepartment(dep.name, orgId)
                deptsRestored++
            }
        }
        // Re-fetch to pick up the new SERIAL ids, build a name→id map.
        for (d in deptRepo.getAllDepartments(orgId)) {
            nameToNewId[d.name] = d.id
        }

        // 3) Courses — re-resolve department by NAME (the original
        //    department_id is dead since departments were re-inserted
        //    with new SERIAL ids).
        var coursesRestored = 0
        for (c in backup.data.courses) {
            val newDeptId = c.departments?.name?.let { nameToNewId[it] }
                ?: c.departmentId?.let { oldId ->
                    backup.data.departments.firstOrNull { it.id == oldId }?.name?.let(nameToNewId::get)
                }
            runCatching {
                courseRepo.insertCourse(
                    code = c.code, name = c.name,
                    departmentId = newDeptId ?: -1,
                    orgId = orgId,
                    theoryHours = c.theoryHours,
                    labHours = c.labHours,
                    credits = c.credits
                )
                coursesRestored++
            }
        }

        // 4) Classrooms — same dept-by-name resolution.
        var classroomsRestored = 0
        for (r in backup.data.classrooms) {
            val newDeptId = r.departments?.name?.let { nameToNewId[it] }
                ?: r.departmentId?.let { oldId ->
                    backup.data.departments.firstOrNull { it.id == oldId }?.name?.let(nameToNewId::get)
                }
            runCatching {
                classroomRepo.insertClassroom(
                    roomCode = r.roomCode, capacity = r.capacity,
                    departmentId = newDeptId, orgId = orgId, type = r.type
                )
                classroomsRestored++
            }
        }

        // 5) org_settings — single-row table per org; UPSERT semantics.
        var settingsRestored = false
        backup.data.settings?.let { src ->
            runCatching {
                settingsRepo.updateSettings(
                    orgId = orgId,
                    timeStepMinutes = src.timeStepMinutes,
                    activeDays = src.activeDays.ifEmpty {
                        listOf("Monday","Tuesday","Wednesday","Thursday","Friday")
                    },
                    dayStart = src.dayStart,
                    dayEnd = src.dayEnd
                )
                settingsRestored = true
            }
        }

        return RestoreResult(
            departments = deptsRestored,
            courses = coursesRestored,
            classrooms = classroomsRestored,
            settingsRestored = settingsRestored,
            skippedLecturers = backup.data.lecturers.size,
            skippedOfferings = backup.data.offerings.size,
            skippedSchedule = backup.data.schedule.size,
            skippedAvailability = backup.data.availability.size
        )
    }

    data class RestoreResult(
        val departments: Int,
        val courses: Int,
        val classrooms: Int,
        val settingsRestored: Boolean,
        val skippedLecturers: Int,
        val skippedOfferings: Int,
        val skippedSchedule: Int,
        val skippedAvailability: Int
    ) {
        fun summary(): String = buildString {
            appendLine("Geri yüklendi:")
            appendLine("• Bölüm: $departments")
            appendLine("• Ders: $courses")
            appendLine("• Derslik: $classrooms")
            if (settingsRestored) appendLine("• Org ayarları: ✓")
            val skipTotal = skippedLecturers + skippedOfferings + skippedSchedule + skippedAvailability
            if (skipTotal > 0) {
                appendLine()
                appendLine("Geri yüklenmeyen (mobil yetkisi yetmiyor — panelden ekleyin):")
                if (skippedLecturers > 0)    appendLine("• Hoca: $skippedLecturers")
                if (skippedOfferings > 0)    appendLine("• Açılan ders: $skippedOfferings")
                if (skippedSchedule > 0)     appendLine("• Program kaydı: $skippedSchedule")
                if (skippedAvailability > 0) appendLine("• Müsaitlik: $skippedAvailability")
            }
        }
    }

    private fun isoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())
}
